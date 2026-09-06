#!/usr/bin/env python3
"""Procedurally synthesizes all game audio (sfx + music) into sounds/*.wav.

Everything is generated from numpy oscillators/noise — no samples, no external
audio libraries, zero licensing concerns.

The engine below is a small sound-design toolkit (FM, resonant state-variable
filters, saturation, convolution reverb, delay, bitcrush, chorus, formants)
rather than raw tone+noise bursts, because that is what gives sounds character.
Every sound is built from the same four layers:

    transient  — 5-20ms bright click that gives the "attack"
    body       — pitched element (FM/saw/sine) with pitch + filter envelopes
    texture    — noise band, tremolo, granular flutter
    tail       — reverb/delay, which puts the sound in a space

Generators produce mono. Every sound then goes through the shared `master()`
chain, exactly like a real sound-design session:

    transient shaping -> compression -> sub reinforcement -> stereo
    decorrelation/width -> true-stereo reverb send -> auto-pan -> limiting

Pitched attacks are tuned to A minor so they agree with the music instead of
clashing with it. Output is 16-bit stereo WAV.

Run after adding a new ProjectileType/archetype, the same way
scripts/generate_tiles.py is rerun after adding a tile. Takes ~30-60s
(the time-varying filters and dynamics run sample-by-sample).
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


# ─────────────────────────── filters ───────────────────────────

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


# Vowel formant frequencies — running a sound through these makes it read as a
# voice/creature rather than as filtered noise.
VOWELS = {
    "ah": (730, 1090, 2440),
    "oo": (300, 870, 2240),
    "eh": (530, 1840, 2480),
    "uh": (640, 1190, 2390),
}


def formant(x, vowel="ah", amount=0.7, q=9.0):
    f1, f2, f3 = VOWELS[vowel]
    voiced = res_bp(x, f1, q) * 1.0 + res_bp(x, f2, q) * 0.55 + res_bp(x, f3, q) * 0.3
    peak = np.max(np.abs(voiced))
    if peak > 1e-9:
        voiced = voiced / peak * (np.max(np.abs(x)) + 1e-9)
    return x * (1.0 - amount) + voiced * amount


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


# ─────────────────────────── shaping & space ───────────────────────────

def saturate(x, drive=2.0):
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


def _impulse_response(decay, damp, name):
    key = (round(decay, 3), round(damp, 1), name)
    if key in _IR_CACHE:
        return _IR_CACHE[key]
    n = n_of(decay)
    ir = np.random.default_rng(seed_of("ir" + str(key))).normal(0, 1, n)
    ir *= np.exp(-np.linspace(0.0, 7.0, n))
    ir = lowpass(ir, damp, order=1)
    # sparse early reflections give the space a size
    for tap, g in ((0.011, 0.5), (0.019, -0.38), (0.031, 0.3), (0.047, -0.22)):
        i = int(tap * SR)
        if i < n:
            ir[i] += g
    ir /= np.sqrt(np.sum(ir ** 2)) + 1e-9
    _IR_CACHE[key] = ir
    return ir


def reverb(x, decay=0.6, damp=5000.0, mix=0.25, name="room"):
    if mix <= 0:
        return x
    ir = _impulse_response(decay, damp, name)
    wet = fft_convolve(x, ir)
    out = np.zeros(len(wet))
    out[:len(x)] += x * (1.0 - mix)
    out += wet * mix * 2.2
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


def limit(x, ceiling=0.985, lookahead_ms=1.5):
    """Soft limiter — catches peaks without the flat-topping that hard clipping causes."""
    env = env_follow(x, 0.15, 30.0)
    over = np.maximum(env / ceiling, 1.0)
    gain = 1.0 / over
    k = max(1, int(lookahead_ms * 0.001 * SR))
    gain = np.minimum(gain, np.roll(gain, -k))
    gain = lowpass(gain, 3000, order=1)
    return np.tanh(x * np.clip(gain, 0.05, 1.0) * 1.02) * 0.98


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


# ─────────────────────────── output ───────────────────────────

def mixdown(*parts):
    n = max(len(p) for p in parts)
    out = np.zeros(n)
    for p in parts:
        out[:len(p)] += p
    return out


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
        for _ in range(4):
            d = int(rng.uniform(0.0007, 0.013) * SR)
            if d < n:
                ch[d:] += x[: n - d] * rng.uniform(0.10, 0.26) * sign
    mid = (l + r) * 0.5
    side = (l - r) * 0.5 * (width * 2.0)
    return np.stack([mid + side, mid - side], axis=1)


def stereo_reverb(st, decay=0.7, damp=5000.0, mix=0.18, name="sv"):
    """True-stereo reverb: a different impulse response per channel."""
    if mix <= 0:
        return st
    il = _impulse_response(decay, damp, name + "L")
    ir = _impulse_response(decay, damp * 0.92, name + "R")
    wl = fft_convolve(st[:, 0], il)
    wr = fft_convolve(st[:, 1], ir)
    n = len(wl)
    out = np.zeros((n, 2))
    out[: len(st), 0] += st[:, 0] * (1.0 - mix)
    out[: len(st), 1] += st[:, 1] * (1.0 - mix)
    out[:, 0] += wl * mix * 2.2
    out[:, 1] += wr * mix * 2.2
    return out


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


def at(buf, start_sec, sig, gain=1.0):
    start = int(start_sec * SR)
    if start >= len(buf):
        return
    end = min(len(buf), start + len(sig))
    buf[start:end] += sig[:end - start] * gain


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


def master(x, width=0.65, transient=1.0, comp=(0.22, 4.0), sub=None,
           tail=(0.7, 0.18), pan=None, pingpong=None, name="s"):
    """Shared finishing chain: punch in mono, then space and width in stereo."""
    if not is_stereo(x):
        if transient > 0:
            x = transient_shape(x, boost=transient)
        if comp is not None:
            x = compress(x, thresh=comp[0], ratio=comp[1])
        if sub is not None:
            x = sub_boost(x, freq=sub[0], amount=sub[1], name=name + "sub")
        st = stereoize(x, width=width, name=name)
    else:
        st = x
    if pingpong is not None:
        st = ping_pong(st, time_s=pingpong[0], feedback=pingpong[1], mix=pingpong[2])
    if tail is not None:
        st = stereo_reverb(st, decay=tail[0], damp=5200, mix=tail[1], name=name)
    if pan is not None:
        st = auto_pan(st, pan[0], pan[1])
    return st


def save_wav(name, signal, level=0.85, trim=True, **master_kw):
    x = np.nan_to_num(np.asarray(signal, dtype=float))
    x = master(x, name=name, **master_kw)
    x = x - np.mean(x, axis=0)  # kill DC offset
    if trim:
        x = trim_tail(x)
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


def limit_stereo(st):
    linked = np.max(np.abs(st), axis=1)
    env = env_follow(linked, 0.15, 30.0)
    over = np.maximum(env / 0.985, 1.0)
    gain = lowpass(1.0 / over, 3000, order=1)
    gain = np.clip(gain, 0.05, 1.0)
    return np.tanh((st.T * gain).T * 1.02) * 0.98


# ═══════════════════════════ shared attack archetypes ═══════════════════════════
# Tints change the FM ratio / filter character / noise band so families built
# from one generator still read as distinct elements.

ZAP_TINTS = {
    #            ratio  index    filt_q  noise band       drive  reverb
    "neutral": (2.0, (6, 0.6), 3.0, (900, 5000), 1.6, (0.30, 0.16)),
    "arcane":  (1.41, (9, 1.2), 5.0, (2000, 9000), 1.4, (0.55, 0.30)),
    "dark":    (1.73, (7, 0.8), 4.5, (120, 1600), 2.2, (0.70, 0.34)),
    "fire":    (2.73, (11, 1.5), 2.5, (400, 6500), 3.0, (0.35, 0.20)),
    "poison":  (3.31, (8, 1.0), 6.0, (300, 3000), 2.0, (0.40, 0.22)),
    "tech":    (4.0, (5, 0.2), 8.0, (3000, 12000), 1.3, (0.28, 0.18)),
    "sharp":   (3.7, (6, 0.4), 6.5, (1800, 11000), 1.8, (0.26, 0.14)),
    "twinkle": (5.02, (4, 0.5), 4.0, (4000, 13000), 1.2, (0.75, 0.34)),
    "mud":     (1.26, (9, 1.4), 2.0, (80, 900), 2.6, (0.30, 0.18)),
    "sand":    (2.31, (7, 1.1), 3.0, (600, 5500), 2.0, (0.30, 0.18)),
}


def gen_zap(base, tint, dur=0.42, name="zap"):
    ratio, (idx_hi, idx_lo), q, band, drive, (rv_dec, rv_mix) = ZAP_TINTS[tint]
    n = n_of(dur)

    pitch = seg_env([(0.0, base * 2.6), (0.06, base * 1.15), (0.4, base * 0.9), (1.0, base * 0.72)], dur)
    index = seg_env([(0.0, idx_hi), (0.12, idx_hi * 0.45), (1.0, idx_lo)], dur)
    body = fm(pitch, ratio, index, dur)
    body *= exp_env(dur, attack=0.002, decay=6.0)
    body = svf(body, seg_env([(0.0, 8000), (0.15, 2600), (1.0, 700)], dur), q=q)

    tex = bandpass(noise(dur, name + "tex"), *band)
    tex *= exp_env(dur, attack=0.001, decay=13.0)

    click = highpass(noise(0.012, name + "cl"), 2500) * np.linspace(1, 0, n_of(0.012)) ** 2

    out = mixdown(body * 0.85, tex * 0.45)
    out = saturate(out, drive)
    out[:len(click)] += click * 0.55
    return reverb(out, decay=rv_dec, damp=6500, mix=rv_mix, name="zap")


BEAM_TINTS = {
    "ice":     dict(base=1500, ratio=2.01, idx=(3.0, 0.8), sweep=(9000, 4200), q=7, band=(3500, 12000), mix=0.5, drive=1.4, rv=(0.55, 0.30)),
    "drain":   dict(base=190, ratio=1.49, idx=(6.0, 2.0), sweep=(2200, 420), q=6, band=(90, 1400), mix=0.45, drive=2.4, rv=(0.75, 0.34), fall=True),
    "vine":    dict(base=130, ratio=3.11, idx=(7.0, 1.5), sweep=(2600, 700), q=4, band=(150, 2200), mix=0.55, drive=2.2, rv=(0.40, 0.22)),
    "laser":   dict(base=2100, ratio=1.0, idx=(2.0, 0.4), sweep=(12000, 5000), q=9, band=(5000, 14000), mix=0.35, drive=1.8, rv=(0.30, 0.18)),
    "railgun": dict(base=80, ratio=2.5, idx=(9.0, 1.0), sweep=(9000, 900), q=8, band=(1200, 13000), mix=0.55, drive=3.2, rv=(0.85, 0.32)),
    "stone":   dict(base=150, ratio=1.87, idx=(8.0, 3.0), sweep=(2000, 500), q=3, band=(300, 3200), mix=0.7, drive=2.6, rv=(0.60, 0.28)),
    "whip":    dict(base=280, ratio=2.66, idx=(6.0, 0.6), sweep=(8000, 1500), q=5, band=(1200, 9000), mix=0.6, drive=2.4, rv=(0.35, 0.20), crack=True),
    "gravity": dict(base=58, ratio=1.33, idx=(10.0, 2.5), sweep=(1200, 260), q=5, band=(40, 700), mix=0.4, drive=2.8, rv=(1.10, 0.36)),
}


def gen_beam(tint, dur=0.62, name="beam"):
    p = BEAM_TINTS[tint]
    base = p["base"]
    if p.get("fall"):
        pitch = seg_env([(0.0, base * 3.2), (0.25, base * 1.4), (1.0, base * 0.6)], dur)
    else:
        pitch = seg_env([(0.0, base * 0.55), (0.07, base * 1.12), (0.35, base), (1.0, base * 0.94)], dur)
    lfo = 1.0 + 0.018 * np.sin(2 * np.pi * 6.5 * t_axis(dur))
    index = seg_env([(0.0, p["idx"][0]), (0.2, p["idx"][0] * 0.5), (1.0, p["idx"][1])], dur)

    body = fm(pitch * lfo, p["ratio"], index, dur)
    body += 0.35 * saw(pitch * 0.501, dur)
    body *= ad_env(dur, attack=0.02, hold=0.45, curve=1.4)
    body = svf(body, seg_env([(0.0, p["sweep"][0]), (0.3, p["sweep"][1] * 1.5), (1.0, p["sweep"][1])], dur), q=p["q"])

    tex = bandpass(noise(dur, name + "t"), *p["band"]) * ad_env(dur, attack=0.01, hold=0.5, curve=1.2)
    out = saturate(mixdown(body * (1 - p["mix"] * 0.5), tex * p["mix"]), p["drive"])

    if p.get("crack"):
        cr = highpass(noise(0.02, name + "c"), 3000) * np.linspace(1, 0, n_of(0.02)) ** 2
        out[:len(cr)] += cr * 1.1
    out = chorus(out, rate=1.1, depth_ms=5.0, voices=2, mix=0.3, name="beam" + name)
    return reverb(out, decay=p["rv"][0], damp=6000, mix=p["rv"][1], name="beam")


SPINNER_TINTS = {
    "metal":  dict(base=760, ratio=3.71, rate=27, band=(1500, 9000), drive=2.2, rv=(0.45, 0.24)),
    "bone":   dict(base=430, ratio=2.37, rate=22, band=(700, 5500), drive=1.8, rv=(0.40, 0.22)),
    "cursed": dict(base=260, ratio=1.73, rate=19, band=(200, 3000), drive=2.6, rv=(0.80, 0.32)),
    "holy":   dict(base=1080, ratio=2.0, rate=24, band=(2500, 11000), drive=1.5, rv=(0.90, 0.34)),
}


def gen_spinner(tint, dur=0.6, name="spin"):
    p = SPINNER_TINTS[tint]
    n = n_of(dur)
    # doppler arc: the blade travels past you
    pitch = seg_env([(0.0, p["base"] * 0.82), (0.35, p["base"] * 1.1), (1.0, p["base"] * 0.7)], dur)
    body = fm(pitch, p["ratio"], seg_env([(0.0, 6.0), (0.3, 3.5), (1.0, 1.2)], dur), dur)
    whirl = tremolo(n, p["rate"], depth=0.75, shape="pulse")
    whirl = lowpass(whirl, 3000, order=1)
    body *= whirl * ad_env(dur, attack=0.012, hold=0.5, curve=1.5)

    air = bandpass(noise(dur, name + "a"), *p["band"]) * whirl * swell_env(dur, 0.8)
    out = saturate(mixdown(body * 0.8, air * 0.5), p["drive"])
    if tint == "holy":
        out = mixdown(out, sine(p["base"] * 2.5, dur) * 0.18 * exp_env(dur, decay=2.5))
    return reverb(out, decay=p["rv"][0], damp=7000, mix=p["rv"][1], name="spin")


def gen_chain(dur=0.55, name="chain"):
    n = n_of(dur)
    out = np.zeros(n + n_of(0.2))
    hits = 6
    for i in range(hits):
        f = 1500 * (0.72 ** i) + 220
        seg_dur = 0.09
        seg = fm(seg_env([(0.0, f * 2.2), (1.0, f * 0.6)], seg_dur), 2.41,
                 seg_env([(0.0, 9.0), (1.0, 0.8)], seg_dur), seg_dur)
        seg *= exp_env(seg_dur, attack=0.0008, decay=11.0)
        cr = highpass(noise(seg_dur, name + str(i)), 2600) * exp_env(seg_dur, attack=0.0005, decay=16.0)
        at(out, i * (dur * 0.72 / hits), saturate(seg * 0.8 + cr * 0.6, 2.4), 1.0 - i * 0.09)
    return reverb(out, decay=0.5, damp=7000, mix=0.24, name="chain")


def gen_physproj(kind, dur=0.42, name="phys"):
    n = n_of(dur)
    # air rushing past — resonant band sweeping down = doppler whoosh
    air = noise(dur, name + "air")
    air = svf(air, seg_env([(0.0, 900), (0.45, 3400), (1.0, 800)], dur), q=6, mode="bp")
    air *= swell_env(dur, 1.3)

    thud_dur = 0.16
    thud = sine(seg_env([(0.0, 190), (1.0, 46)], thud_dur), thud_dur) * exp_env(thud_dur, decay=7.0)
    body = bandpass(noise(thud_dur, name + "b"), 200, 2600) * exp_env(thud_dur, decay=12.0)

    out = np.zeros(n + n_of(thud_dur))
    out[:n] += air * 0.75
    at(out, dur * 0.62, saturate(thud * 0.9 + body * 0.5, 2.2))
    if kind == "arrow":
        hiss = highpass(noise(dur, name + "h"), 5500) * swell_env(dur, 2.0) * 0.35
        out[:n] += hiss
        shaft = fm(seg_env([(0.0, 1400), (1.0, 700)], dur), 3.7, 3.0, dur) * exp_env(dur, decay=8) * 0.25
        out[:n] += shaft
    return reverb(out, decay=0.4, damp=6000, mix=0.2, name="phys")


def gen_lobbed(dur=0.85, name="lob"):
    n = n_of(dur)
    arc_dur = dur * 0.55
    arc = noise(arc_dur, name + "arc")
    arc = svf(arc, seg_env([(0.0, 500), (1.0, 2600)], arc_dur), q=5, mode="bp")
    arc *= seg_env([(0.0, 0.0), (0.3, 0.55), (1.0, 0.35)], arc_dur)

    imp_dur = 0.3
    sub = sine(seg_env([(0.0, 150), (1.0, 38)], imp_dur), imp_dur) * exp_env(imp_dur, decay=6.0)
    crunch = bandpass(noise(imp_dur, name + "cr"), 150, 4500) * exp_env(imp_dur, decay=9.0)
    click = highpass(noise(0.008, name + "ck"), 3500) * np.linspace(1, 0, n_of(0.008))
    impact = saturate(sub * 1.0 + crunch * 0.6, 2.6)
    impact[:len(click)] += click * 0.7

    out = np.zeros(n + n_of(imp_dur))
    out[:len(arc)] += arc * 0.6
    at(out, dur * 0.55, impact)
    return reverb(out, decay=0.7, damp=4500, mix=0.26, name="lob")


def gen_explosion(dur=1.3, size=1.0, name="boom"):
    """Layered explosion: sub drop + broadband burst + crack + debris + hall."""
    n = n_of(dur)
    sub_dur = dur * 0.5
    sub = sine(seg_env([(0.0, 95 * size), (0.25, 52 * size), (1.0, 26 * size)], sub_dur), sub_dur)
    sub *= exp_env(sub_dur, attack=0.004, decay=3.2)
    sub = saturate(sub, 2.4)

    burst = noise(dur, name + "b")
    burst = svf(burst, seg_env([(0.0, 9000), (0.08, 2600), (0.45, 800), (1.0, 260)], dur), q=1.6)
    burst *= exp_env(dur, attack=0.002, decay=3.4)

    crack = highpass(noise(0.04, name + "c"), 2500) * np.linspace(1, 0, n_of(0.04)) ** 1.5

    # debris grains scattered through the tail
    debris = np.zeros(n)
    rng = np.random.default_rng(seed_of(name + "d"))
    for _ in range(18):
        t0 = rng.uniform(0.08, dur * 0.75)
        g_dur = rng.uniform(0.01, 0.04)
        g = bandpass(noise(g_dur, name + str(rng.integers(1 << 20))), 900, 7000)
        g *= exp_env(g_dur, decay=10.0) * rng.uniform(0.05, 0.22) * np.exp(-2.2 * t0 / dur)
        at(debris, t0, g)

    out = np.zeros(n)
    out[:len(sub)] += sub * 1.0
    out += burst * 0.75
    out[:len(crack)] += crack * 0.8
    out += debris
    out = saturate(out, 1.8)
    return reverb(out, decay=1.5 * size, damp=3500, mix=0.34, name="boom")


def gen_wave(kind, dur=0.75, name="wave"):
    p = dict(wind=dict(band=(400, 5000), q=5, tone=(280, 900), drive=1.8, rv=(0.55, 0.26)),
             sonic=dict(band=(900, 11000), q=8, tone=(1400, 2800), drive=2.2, rv=(0.45, 0.24)))[kind]
    n = n_of(dur)
    sweep = noise(dur, name + "s")
    sweep = svf(sweep, seg_env([(0.0, p["band"][0]), (0.45, p["band"][1]), (1.0, p["band"][0] * 1.6)], dur),
                q=p["q"], mode="bp")
    sweep *= swell_env(dur, 1.1)
    tone = fm(seg_env([(0.0, p["tone"][0]), (1.0, p["tone"][1])], dur), 1.5,
              seg_env([(0.0, 5.0), (1.0, 0.5)], dur), dur)
    tone *= swell_env(dur, 1.6) * 0.35
    out = saturate(mixdown(sweep, tone), p["drive"])
    return reverb(out, decay=p["rv"][0], damp=6000, mix=p["rv"][1], name="wave")


def gen_bullet(dur=0.28, name="gun"):
    crack_d = 0.014
    crack = noise(crack_d, name + "c")
    crack = highpass(crack, 1800) * np.linspace(1, 0, n_of(crack_d)) ** 1.2
    crack = saturate(crack, 5.0)

    body = noise(0.16, name + "b")
    body = svf(body, seg_env([(0.0, 6000), (1.0, 500)], 0.16), q=2.5)
    body *= exp_env(0.16, attack=0.001, decay=9.0)

    thump = sine(seg_env([(0.0, 130), (1.0, 45)], 0.1), 0.1) * exp_env(0.1, decay=8.0)
    mech = highpass(noise(0.03, name + "m"), 4000) * exp_env(0.03, decay=12.0) * 0.2

    out = np.zeros(n_of(dur))
    out[:len(body)] += body * 0.8
    out[:len(thump)] += thump * 0.7
    out[:len(crack)] += crack * 1.0
    at(out, 0.05, mech)
    return reverb(saturate(out, 2.0), decay=0.55, damp=4000, mix=0.24, name="gun")


def gen_punch(dur=0.35, name="punch"):
    sub = sine(seg_env([(0.0, 200), (1.0, 52)], 0.22), 0.22) * exp_env(0.22, decay=6.5)
    smack = bandpass(noise(0.14, name + "s"), 250, 3800) * exp_env(0.14, attack=0.001, decay=11.0)
    click = highpass(noise(0.006, name + "c"), 4000) * np.linspace(1, 0, n_of(0.006))
    out = np.zeros(n_of(dur))
    out[:len(sub)] += sub
    out[:len(smack)] += smack * 0.65
    out[:len(click)] += click * 0.5
    return reverb(saturate(out, 2.8), decay=0.4, damp=3500, mix=0.22, name="punch")


# ═══════════════════════════ specialized attacks ═══════════════════════════

def gen_tentacle_slap():
    dur = 0.45
    wet = bandpass(noise(dur, "tent"), 120, 1800) * exp_env(dur, attack=0.004, decay=6.0)
    wet = svf(wet, seg_env([(0.0, 2200), (1.0, 400)], dur), q=4)
    slap = sine(seg_env([(0.0, 260), (1.0, 62)], 0.1), 0.1) * exp_env(0.1, decay=7.0)
    squelch = fm(seg_env([(0.0, 420), (1.0, 130)], dur), 1.26, seg_env([(0.0, 8), (1.0, 1)], dur), dur)
    squelch *= exp_env(dur, decay=5.0) * 0.4
    out = mixdown(wet * 0.8, squelch)
    out[:len(slap)] += slap * 0.9
    return reverb(saturate(out, 2.2), decay=0.5, damp=4000, mix=0.24, name="tent")


def gen_fireball_whoosh():
    dur = 0.9
    n = n_of(dur)
    roar = noise(dur, "fire")
    roar = svf(roar, seg_env([(0.0, 700), (0.3, 3000), (1.0, 900)], dur), q=2.5)
    roar *= ad_env(dur, attack=0.04, hold=0.35, curve=1.3)
    # turbulence: slow random amplitude flutter
    flut = lowpass(np.abs(noise(dur, "fireflut")), 22, order=1)
    flut = 0.6 + 1.6 * flut / (np.max(flut) + 1e-9)
    roar *= flut

    crackle = np.zeros(n)
    rng = np.random.default_rng(seed_of("firecr"))
    for _ in range(26):
        t0 = rng.uniform(0.0, dur * 0.8)
        gd = rng.uniform(0.004, 0.016)
        g = highpass(noise(gd, "g" + str(rng.integers(1 << 20))), 3500) * exp_env(gd, decay=14) * rng.uniform(0.1, 0.35)
        at(crackle, t0, g)

    low = fm(seg_env([(0.0, 150), (1.0, 85)], dur), 1.41, seg_env([(0.0, 7), (1.0, 2)], dur), dur)
    low *= ad_env(dur, attack=0.03, hold=0.4, curve=1.4) * 0.5
    out = saturate(mixdown(roar * 0.8, crackle, low), 2.4)
    return reverb(out, decay=0.9, damp=4500, mix=0.28, name="fire")


def gen_inferno_blast_roar():
    dur = 1.2
    base = gen_fireball_whoosh()
    boom = gen_explosion(dur=1.0, size=0.85, name="inf")
    out = mixdown(base * 0.7, boom * 0.85)
    return saturate(out, 1.6)


def gen_tidal_wave_crash():
    dur = 1.3
    n = n_of(dur)
    swell = noise(dur, "tide")
    swell = svf(swell, seg_env([(0.0, 400), (0.45, 2600), (1.0, 500)], dur), q=2.0)
    swell *= seg_env([(0.0, 0.05), (0.4, 1.0), (0.75, 0.7), (1.0, 0.0)], dur)
    foam = highpass(noise(dur, "foam"), 4500) * seg_env([(0.0, 0.0), (0.45, 0.8), (1.0, 0.05)], dur)
    rumble = sine(seg_env([(0.0, 75), (1.0, 38)], dur), dur) * seg_env([(0.0, 0.2), (0.4, 1.0), (1.0, 0.0)], dur)
    # bubbles
    bub = np.zeros(n)
    rng = np.random.default_rng(seed_of("bub"))
    for _ in range(14):
        t0 = rng.uniform(dur * 0.35, dur * 0.9)
        bd = rng.uniform(0.02, 0.05)
        b = sine(seg_env([(0.0, rng.uniform(300, 900)), (1.0, rng.uniform(900, 1800))], bd), bd)
        at(bub, t0, b * exp_env(bd, decay=8) * rng.uniform(0.05, 0.16))
    out = saturate(mixdown(swell * 0.8, foam * 0.5, rumble * 0.7, bub), 1.8)
    return reverb(out, decay=1.3, damp=3800, mix=0.3, name="tide")


def gen_geyser_burst():
    dur = 0.8
    hiss = noise(dur, "gey")
    hiss = svf(hiss, seg_env([(0.0, 900), (0.25, 5500), (1.0, 2200)], dur), q=3.5, mode="bp")
    hiss *= seg_env([(0.0, 0.1), (0.18, 1.0), (1.0, 0.08)], dur)
    surge = fm(seg_env([(0.0, 180), (0.3, 620), (1.0, 300)], dur), 1.5, seg_env([(0.0, 8), (1.0, 1)], dur), dur)
    surge *= seg_env([(0.0, 0.0), (0.2, 0.8), (1.0, 0.0)], dur) * 0.5
    return reverb(saturate(mixdown(hiss, surge), 2.0), decay=0.8, damp=5000, mix=0.28, name="gey")


def gen_rocket_launch():
    dur = 0.95
    ignite = highpass(noise(0.07, "ign"), 2500) * exp_env(0.07, decay=7.0)
    thrust = noise(dur, "thr")
    thrust = svf(thrust, seg_env([(0.0, 500), (0.3, 2200), (1.0, 1200)], dur), q=2.2)
    thrust *= ad_env(dur, attack=0.03, hold=0.5, curve=1.3)
    dop = fm(seg_env([(0.0, 320), (1.0, 900)], dur), 2.0, seg_env([(0.0, 6), (1.0, 1.5)], dur), dur)
    dop *= ad_env(dur, attack=0.05, hold=0.5, curve=1.2) * 0.4
    out = np.zeros(n_of(dur))
    out += saturate(thrust * 0.8 + dop, 2.4)
    out[:len(ignite)] += ignite * 0.9
    return reverb(out, decay=0.85, damp=4500, mix=0.28, name="rkt")


def gen_talon_swipe():
    dur = 0.32
    swipe = noise(dur, "talon")
    swipe = svf(swipe, seg_env([(0.0, 1800), (0.4, 7000), (1.0, 2200)], dur), q=7, mode="bp")
    swipe *= exp_env(dur, attack=0.004, decay=8.0)
    tear = bandpass(noise(0.12, "tear"), 900, 5000) * exp_env(0.12, decay=11) * 0.5
    screech = fm(seg_env([(0.0, 2400), (1.0, 1100)], 0.16), 2.37, seg_env([(0.0, 5), (1.0, 0.5)], 0.16), 0.16)
    screech *= exp_env(0.16, decay=6) * 0.3
    out = np.zeros(n_of(dur))
    out += swipe
    out[:len(tear)] += tear
    at(out, 0.03, screech)
    return reverb(saturate(out, 2.2), decay=0.4, damp=6500, mix=0.22, name="talon")


def gen_claw_swipe_slash():
    dur = 0.3
    swipe = noise(dur, "claw")
    swipe = svf(swipe, seg_env([(0.0, 1200), (0.35, 6000), (1.0, 1500)], dur), q=6, mode="bp")
    swipe *= exp_env(dur, attack=0.003, decay=9.0)
    growl = fm(seg_env([(0.0, 190), (1.0, 95)], dur), 1.73, seg_env([(0.0, 9), (1.0, 2)], dur), dur)
    growl *= exp_env(dur, decay=6.0) * 0.45
    growl = saturate(growl, 3.0)
    return reverb(mixdown(swipe, growl), decay=0.45, damp=5000, mix=0.24, name="claw")


def gen_poison_dart():
    dur = 0.35
    hiss = bandpass(noise(dur, "dart"), 1200, 6500) * exp_env(dur, attack=0.002, decay=9.0)
    body = fm(seg_env([(0.0, 1100), (1.0, 420)], dur), 3.31, seg_env([(0.0, 7), (1.0, 0.8)], dur), dur)
    body *= exp_env(dur, decay=8.0) * 0.5
    return reverb(saturate(mixdown(hiss, body), 1.8), decay=0.4, damp=7000, mix=0.22, name="dart")


def gen_sword_wave_slash():
    dur = 0.42
    slash = noise(dur, "sw")
    slash = svf(slash, seg_env([(0.0, 2500), (0.3, 8500), (1.0, 2000)], dur), q=8, mode="bp")
    slash *= exp_env(dur, attack=0.002, decay=8.0)
    ring = fm(seg_env([(0.0, 1900), (1.0, 1500)], dur), 2.0, seg_env([(0.0, 4), (1.0, 0.4)], dur), dur)
    ring *= exp_env(dur, decay=4.0) * 0.35
    return reverb(saturate(mixdown(slash, ring), 2.0), decay=0.6, damp=7500, mix=0.28, name="sw")


def gen_scythe_swing():
    dur = 0.5
    swing = noise(dur, "scy")
    swing = svf(swing, seg_env([(0.0, 1500), (0.45, 6500), (1.0, 1200)], dur), q=7, mode="bp")
    swing *= swell_env(dur, 1.2)
    groan = fm(seg_env([(0.0, 150), (1.0, 92)], dur), 1.41, seg_env([(0.0, 8), (1.0, 2)], dur), dur)
    groan *= exp_env(dur, attack=0.02, decay=4.0) * 0.45
    return reverb(saturate(mixdown(swing, groan), 2.2), decay=0.75, damp=5000, mix=0.3, name="scy")


def gen_reap_slash():
    dur = 0.6
    base = gen_scythe_swing()
    souls = fm(seg_env([(0.0, 620), (1.0, 260)], dur), 1.73, seg_env([(0.0, 9), (1.0, 3)], dur), dur)
    souls *= swell_env(dur, 1.6) * 0.5
    souls = delay_fx(souls, 0.09, feedback=0.45, mix=0.4, taps=4)
    return reverb(mixdown(base * 0.85, souls * 0.7), decay=1.0, damp=4500, mix=0.32, name="reap")


def gen_blood_fang_bite():
    dur = 0.4
    snap = sine(seg_env([(0.0, 330), (1.0, 70)], 0.09), 0.09) * exp_env(0.09, attack=0.001, decay=8.0)
    crunch = bandpass(noise(0.18, "bf"), 250, 3200) * exp_env(0.18, attack=0.001, decay=8.0)
    slurp = fm(seg_env([(0.0, 200), (1.0, 480)], dur), 1.26, seg_env([(0.0, 6), (1.0, 1.5)], dur), dur)
    slurp *= seg_env([(0.0, 0.0), (0.4, 0.6), (1.0, 0.0)], dur) * 0.45
    out = np.zeros(n_of(dur))
    out[:len(crunch)] += crunch * 0.75
    out[:len(snap)] += snap
    out += slurp
    return reverb(saturate(out, 2.6), decay=0.45, damp=4000, mix=0.24, name="bf")


def gen_devour_bite():
    dur = 0.45
    growl = fm(seg_env([(0.0, 120), (0.5, 88), (1.0, 105)], dur), 1.73,
               seg_env([(0.0, 11), (1.0, 3)], dur), dur)
    growl *= ad_env(dur, attack=0.02, hold=0.4, curve=1.3) * 0.7
    growl = saturate(formant(growl, "uh", amount=0.6), 3.2)
    chomp = bandpass(noise(0.2, "dev"), 200, 4000) * exp_env(0.2, attack=0.001, decay=7.0)
    snap = sine(seg_env([(0.0, 280), (1.0, 55)], 0.1), 0.1) * exp_env(0.1, decay=6.0)
    out = np.zeros(n_of(dur))
    out += growl
    at(out, 0.02, chomp * 0.7)
    at(out, 0.02, snap * 0.9)
    return reverb(out, decay=0.55, damp=3800, mix=0.26, name="dev")


def gen_jaw_chomp():
    dur = 0.35
    snap1 = sine(seg_env([(0.0, 380), (1.0, 62)], 0.08), 0.08) * exp_env(0.08, attack=0.0008, decay=9.0)
    crunch = bandpass(noise(0.16, "jaw"), 300, 5000) * exp_env(0.16, attack=0.001, decay=9.0)
    bone = fm(400, 3.7, seg_env([(0.0, 8), (1.0, 0.5)], 0.09), 0.09) * exp_env(0.09, decay=12) * 0.4
    out = np.zeros(n_of(dur))
    out[:len(crunch)] += crunch * 0.8
    out[:len(snap1)] += snap1
    at(out, 0.006, bone)
    return reverb(saturate(out, 2.8), decay=0.4, damp=4500, mix=0.22, name="jaw")


def gen_bat_swarm_screech():
    dur = 0.75
    n = n_of(dur)
    wings = bandpass(noise(dur, "bat"), 400, 5000)
    wings *= tremolo(n, 19, depth=0.9, shape="pulse")
    wings *= swell_env(dur, 0.8)
    scr = np.zeros(n)
    rng = np.random.default_rng(seed_of("batscr"))
    for _ in range(7):
        t0 = rng.uniform(0.0, dur * 0.7)
        sd = rng.uniform(0.06, 0.14)
        f0 = rng.uniform(2400, 4200)
        s = fm(seg_env([(0.0, f0), (1.0, f0 * 0.55)], sd), 2.37, seg_env([(0.0, 5), (1.0, 0.6)], sd), sd)
        at(scr, t0, s * exp_env(sd, decay=5.0) * rng.uniform(0.15, 0.32))
    return reverb(saturate(mixdown(wings * 0.7, scr), 1.8), decay=0.7, damp=7000, mix=0.28, name="bat")


def gen_frost_shard_shatter():
    dur = 0.55
    # crystalline: inharmonic partial stack + icy noise
    out = np.zeros(n_of(dur))
    for p, g, d in ((1.0, 1.0, 4.0), (2.37, 0.55, 6.0), (3.71, 0.35, 8.0), (5.02, 0.2, 10.0)):
        out += sine(2300 * p, dur) * exp_env(dur, attack=0.001, decay=d) * g
    ice = highpass(noise(dur, "frost"), 5000) * exp_env(dur, attack=0.001, decay=9.0)
    crack = highpass(noise(0.02, "fcr"), 4000) * np.linspace(1, 0, n_of(0.02)) ** 2
    body = fm(seg_env([(0.0, 900), (1.0, 500)], dur), 2.01, seg_env([(0.0, 5), (1.0, 0.5)], dur), dur)
    body *= exp_env(dur, decay=7.0) * 0.4
    out = mixdown(out * 0.5, ice * 0.5, body)
    out[:len(crack)] += crack * 0.7
    return reverb(out, decay=0.9, damp=9000, mix=0.32, name="frost")


def gen_lightning_crack():
    dur = 0.7
    n = n_of(dur)
    # rapidly jumping resonant band on noise = electric crackle
    rng = np.random.default_rng(seed_of("ltg"))
    steps = 90
    cut = np.repeat(rng.uniform(600, 9000, steps), max(1, n // steps))[:n]
    cut = np.pad(cut, (0, max(0, n - len(cut))), mode="edge")
    arc = svf(noise(dur, "ltg"), lowpass(cut, 300, order=1), q=9, mode="bp")
    arc *= exp_env(dur, attack=0.001, decay=6.0)
    crack = highpass(noise(0.03, "ltgc"), 2000) * np.linspace(1, 0, n_of(0.03)) ** 1.3
    crack = saturate(crack, 4.0)
    rumble = lowpass(noise(dur, "ltgr"), 400) * exp_env(dur, attack=0.01, decay=3.0)
    out = mixdown(arc * 0.8, rumble * 0.55)
    out[:len(crack)] += crack * 1.0
    return reverb(saturate(out, 2.2), decay=1.0, damp=4000, mix=0.3, name="ltg")


def gen_thunder_strike_boom():
    dur = 1.6
    strike = gen_lightning_crack()
    boom = gen_explosion(dur=1.5, size=1.25, name="thun")
    sub = sine(seg_env([(0.0, 60), (1.0, 24)], 1.2), 1.2) * exp_env(1.2, attack=0.005, decay=2.4)
    out = mixdown(strike * 0.8, boom * 0.9, saturate(sub, 2.0) * 0.8)
    return out


def gen_boulder_rumble():
    dur = 1.0
    n = n_of(dur)
    rumble = lowpass(noise(dur, "bldr"), 260, order=1)
    rumble *= ad_env(dur, attack=0.05, hold=0.45, curve=1.4)
    grind = bandpass(noise(dur, "grnd"), 300, 2200) * ad_env(dur, attack=0.04, hold=0.4, curve=1.3) * 0.45
    # tumbling knocks
    knocks = np.zeros(n)
    rng = np.random.default_rng(seed_of("knock"))
    for _ in range(9):
        t0 = rng.uniform(0.02, dur * 0.8)
        kd = rng.uniform(0.05, 0.11)
        k = sine(seg_env([(0.0, rng.uniform(90, 170)), (1.0, 40)], kd), kd) * exp_env(kd, decay=7)
        at(knocks, t0, saturate(k, 2.5) * rng.uniform(0.25, 0.6))
    out = saturate(mixdown(rumble * 0.9, grind, knocks * 0.7), 2.0)
    return reverb(out, decay=1.1, damp=3000, mix=0.3, name="bldr")


def gen_thorn_shot():
    dur = 0.28
    thwip = noise(dur, "thorn")
    thwip = svf(thwip, seg_env([(0.0, 1200), (0.3, 5500), (1.0, 1400)], dur), q=7, mode="bp")
    thwip *= exp_env(dur, attack=0.002, decay=10.0)
    wood = fm(seg_env([(0.0, 700), (1.0, 300)], dur), 3.11, seg_env([(0.0, 6), (1.0, 0.6)], dur), dur)
    wood *= exp_env(dur, decay=10.0) * 0.4
    return reverb(saturate(mixdown(thwip, wood), 2.0), decay=0.35, damp=6000, mix=0.2, name="thorn")


def gen_raise_dead_moan():
    dur = 1.3
    voices = np.zeros(n_of(dur))
    for i, (f, det) in enumerate(((115, 1.0), (172, 1.006), (231, 0.994))):
        v = fm(seg_env([(0.0, f * det * 0.85), (0.4, f * det), (1.0, f * det * 0.78)], dur),
               1.41, seg_env([(0.0, 3), (0.5, 7), (1.0, 2)], dur), dur)
        v *= seg_env([(0.0, 0.0), (0.25, 0.9), (0.7, 0.75), (1.0, 0.0)], dur)
        voices += v * (0.6 if i == 0 else 0.35)
    dirt = bandpass(noise(dur, "dirt"), 90, 900) * seg_env([(0.0, 0.3), (0.3, 0.6), (1.0, 0.0)], dur) * 0.4
    voices = svf(voices, seg_env([(0.0, 500), (0.5, 1400), (1.0, 400)], dur), q=5)
    voices = formant(voices, "uh", amount=0.75)  # reads as a chorus of throats, not filtered tone
    out = saturate(mixdown(voices, dirt), 1.8)
    out = chorus(out, rate=0.35, depth_ms=11, voices=3, mix=0.4, name="rd")
    return reverb(out, decay=1.5, damp=3500, mix=0.36, name="rd")


def gen_wail_shriek():
    dur = 1.1
    pitch = seg_env([(0.0, 700), (0.25, 2300), (0.6, 1800), (1.0, 620)], dur)
    voice = fm(pitch, 1.49, seg_env([(0.0, 2), (0.35, 8), (1.0, 1.5)], dur), dur)
    vib = 1.0 + 0.03 * np.sin(2 * np.pi * 5.5 * t_axis(dur))
    voice = fm(pitch * vib, 1.49, seg_env([(0.0, 2), (0.35, 8), (1.0, 1.5)], dur), dur)
    voice *= seg_env([(0.0, 0.0), (0.15, 1.0), (0.7, 0.8), (1.0, 0.0)], dur)
    # sweep the vowel ah -> eh across the shriek so it sounds like a mouth moving
    voice = formant(voice, "ah", amount=0.6) * 0.6 + formant(voice, "eh", amount=0.6) * 0.5
    breath = bandpass(noise(dur, "wail"), 900, 5500) * swell_env(dur, 1.2) * 0.35
    out = saturate(mixdown(voice * 0.75, breath), 2.0)
    return reverb(out, decay=1.6, damp=4500, mix=0.36, name="wail")


def gen_shadow_bolt_whoosh():
    dur = 0.65
    whoosh = noise(dur, "shad")
    whoosh = svf(whoosh, seg_env([(0.0, 2400), (0.4, 900), (1.0, 300)], dur), q=4, mode="bp")
    whoosh *= exp_env(dur, attack=0.01, decay=4.5)
    body = fm(seg_env([(0.0, 260), (1.0, 92)], dur), 1.73, seg_env([(0.0, 10), (1.0, 2.5)], dur), dur)
    body *= exp_env(dur, attack=0.005, decay=4.0) * 0.7
    body = saturate(body, 2.6)
    return reverb(mixdown(whoosh * 0.6, body), decay=1.0, damp=3200, mix=0.34, name="shad")


def gen_curse_hex():
    dur = 0.9
    drone = np.zeros(n_of(dur))
    for f, g in ((146.8, 1.0), (155.6, 0.6), (220.0, 0.4)):  # dissonant minor-second stack
        drone += fm(f, 1.41, seg_env([(0.0, 6), (1.0, 2)], dur), dur) * g
    drone *= seg_env([(0.0, 0.0), (0.12, 1.0), (0.6, 0.8), (1.0, 0.0)], dur)
    whisper = bandpass(noise(dur, "hex"), 700, 3400) * tremolo(n_of(dur), 11, 0.6) * swell_env(dur, 1.2) * 0.4
    out = saturate(mixdown(drone * 0.6, whisper), 2.0)
    out = delay_fx(out, 0.13, feedback=0.42, mix=0.3, taps=5)
    return reverb(out, decay=1.4, damp=3800, mix=0.34, name="hex")


def gen_holy_bolt_chime():
    dur = 1.2
    out = np.zeros(n_of(dur))
    # bell = inharmonic partial stack with per-partial decay
    for p, g, d in ((1.0, 1.0, 2.2), (2.0, 0.6, 3.0), (2.76, 0.4, 4.0), (5.4, 0.25, 6.0), (8.1, 0.12, 8.0)):
        out += sine(523.25 * p, dur) * exp_env(dur, attack=0.004, decay=d) * g
    strike = highpass(noise(0.015, "holy"), 4000) * np.linspace(1, 0, n_of(0.015)) ** 2
    shimmer = highpass(noise(dur, "shim"), 7000) * exp_env(dur, attack=0.02, decay=4.0) * 0.18
    choir = fm(261.6, 2.0, seg_env([(0.0, 1.5), (1.0, 0.3)], dur), dur) * swell_env(dur, 1.4) * 0.25
    choir = chorus(choir, rate=0.5, depth_ms=9, voices=3, mix=0.5, name="holych")
    out = mixdown(out * 0.55, shimmer, choir)
    out[:len(strike)] += strike * 0.6
    return reverb(out, decay=1.8, damp=7000, mix=0.38, name="holy")


def gen_data_bolt_blip():
    dur = 0.4
    notes = [1046, 1568, 1318, 2093, 1760]
    seg = dur * 0.55 / len(notes)
    out = np.zeros(n_of(dur))
    for i, f in enumerate(notes):
        s = square(f, seg, duty=0.35) * exp_env(seg, attack=0.001, decay=7.0) * 0.5
        s = bitcrush(s, bits=5, hold=3)
        at(out, i * seg, s)
    sweep = svf(noise(dur, "data"), seg_env([(0.0, 3000), (1.0, 9000)], dur), q=8, mode="bp")
    out += sweep * exp_env(dur, attack=0.01, decay=6.0) * 0.25
    return reverb(saturate(out, 1.6), decay=0.45, damp=9000, mix=0.24, name="data")


def gen_virus_glitch():
    dur = 0.5
    rng = np.random.default_rng(seed_of("virus"))
    out = np.zeros(n_of(dur))
    t = 0.0
    while t < dur * 0.8:
        sd = rng.uniform(0.02, 0.06)
        f = rng.uniform(180, 2600)
        s = square(f, sd, duty=rng.uniform(0.15, 0.5)) * exp_env(sd, attack=0.001, decay=rng.uniform(4, 12))
        s = bitcrush(s, bits=int(rng.integers(2, 5)), hold=int(rng.integers(4, 18)))
        at(out, t, s * rng.uniform(0.3, 0.7))
        t += sd * rng.uniform(0.5, 1.2)
    hum = saw(seg_env([(0.0, 110), (1.0, 70)], dur), dur) * exp_env(dur, decay=3.5) * 0.3
    hum = bitcrush(hum, bits=4, hold=8)
    out = saturate(mixdown(out, hum), 2.2)
    return reverb(out, decay=0.5, damp=6000, mix=0.22, name="virus")


def gen_gravity_ball_warp():
    dur = 1.1
    # collapse then rebound — deep pitch bend with ring modulation
    pitch = seg_env([(0.0, 620), (0.45, 48), (0.75, 180), (1.0, 70)], dur)
    body = fm(pitch, 1.33, seg_env([(0.0, 4), (0.5, 12), (1.0, 3)], dur), dur)
    ring = np.sin(2 * np.pi * np.cumsum(as_array(pitch * 0.37, n_of(dur))) / SR)
    body = body * (0.65 + 0.35 * ring)
    body *= seg_env([(0.0, 0.0), (0.1, 1.0), (0.7, 0.8), (1.0, 0.0)], dur)
    sub = sine(seg_env([(0.0, 90), (1.0, 30)], dur), dur) * swell_env(dur, 1.2) * 0.6
    air = svf(noise(dur, "grav"), seg_env([(0.0, 2000), (0.5, 300), (1.0, 900)], dur), q=6, mode="bp")
    air *= swell_env(dur, 1.5) * 0.3
    out = saturate(mixdown(body * 0.8, sub, air), 2.0)
    return reverb(out, decay=1.5, damp=3000, mix=0.34, name="grav")


def gen_void_bolt_implode():
    dur = 0.85
    # reversed swell into a collapse
    swell = svf(noise(dur * 0.6, "void"), seg_env([(0.0, 400), (1.0, 6000)], dur * 0.6), q=6, mode="bp")
    swell *= np.linspace(0, 1, n_of(dur * 0.6)) ** 2.2
    collapse = fm(seg_env([(0.0, 1600), (1.0, 42)], dur * 0.5), 1.73,
                  seg_env([(0.0, 3), (1.0, 12)], dur * 0.5), dur * 0.5)
    collapse *= exp_env(dur * 0.5, attack=0.002, decay=3.5)
    out = np.zeros(n_of(dur))
    out[:len(swell)] += swell * 0.5
    at(out, dur * 0.5, saturate(collapse, 2.6) * 0.9)
    return reverb(out, decay=1.6, damp=2800, mix=0.36, name="void")


def gen_venom_bolt_splat():
    dur = 0.45
    splat = svf(noise(dur, "ven"), seg_env([(0.0, 2600), (0.3, 900), (1.0, 400)], dur), q=5, mode="bp")
    splat *= exp_env(dur, attack=0.003, decay=7.0)
    bubbles = np.zeros(n_of(dur))
    rng = np.random.default_rng(seed_of("venb"))
    for _ in range(9):
        t0 = rng.uniform(0.0, dur * 0.7)
        bd = rng.uniform(0.02, 0.05)
        b = sine(seg_env([(0.0, rng.uniform(240, 700)), (1.0, rng.uniform(700, 1500))], bd), bd)
        at(bubbles, t0, b * exp_env(bd, decay=9) * rng.uniform(0.1, 0.28))
    hiss = bandpass(noise(dur, "venh"), 2000, 7000) * exp_env(dur, decay=8.0) * 0.3
    return reverb(saturate(mixdown(splat * 0.8, bubbles, hiss), 2.0), decay=0.5, damp=5000, mix=0.24, name="ven")


def gen_web_shot_splat():
    dur = 0.4
    thwip = svf(noise(dur, "web"), seg_env([(0.0, 1400), (0.25, 6500), (1.0, 1800)], dur), q=7, mode="bp")
    thwip *= exp_env(dur, attack=0.002, decay=9.0)
    stretch = fm(seg_env([(0.0, 900), (1.0, 260)], dur), 2.66, seg_env([(0.0, 7), (1.0, 1)], dur), dur)
    stretch *= exp_env(dur, decay=6.0) * 0.4
    return reverb(saturate(mixdown(thwip, stretch), 1.8), decay=0.4, damp=6500, mix=0.22, name="web")


def gen_stinger_prick():
    dur = 0.25
    buzz = square(seg_env([(0.0, 2800), (1.0, 1900)], 0.14), 0.14, duty=0.3)
    buzz *= tremolo(n_of(0.14), 60, 0.7) * exp_env(0.14, attack=0.001, decay=10.0) * 0.35
    prick = highpass(noise(0.03, "sting"), 5500) * exp_env(0.03, attack=0.0005, decay=13.0)
    stab = fm(seg_env([(0.0, 2200), (1.0, 800)], 0.09), 3.7, seg_env([(0.0, 7), (1.0, 0.5)], 0.09), 0.09)
    stab *= exp_env(0.09, decay=11.0) * 0.5
    out = np.zeros(n_of(dur))
    out[:len(buzz)] += buzz
    out[:len(prick)] += prick * 0.8
    out[:len(stab)] += stab
    return reverb(saturate(out, 2.0), decay=0.3, damp=8000, mix=0.2, name="sting")


def gen_horn_bellow():
    dur = 1.1
    vib = 1.0 + 0.012 * np.sin(2 * np.pi * 5.0 * t_axis(dur))
    body = np.zeros(n_of(dur))
    for h, g in ((1, 1.0), (2, 0.55), (3, 0.4), (4, 0.22), (5, 0.14), (6, 0.08)):
        body += sine(98.0 * h * vib, dur) * g
    body *= seg_env([(0.0, 0.0), (0.1, 1.0), (0.75, 0.85), (1.0, 0.0)], dur)
    body = saturate(body * 0.5, 2.4)
    # brass formant peaks
    body = res_lp(body, 900, q=2.5) + 0.4 * res_lp(body, 1900, q=3.0)
    rasp = bandpass(noise(dur, "horn"), 150, 1200) * swell_env(dur, 1.4) * 0.2
    return reverb(mixdown(body, rasp), decay=1.6, damp=3500, mix=0.34, name="horn")


# ═══════════════════════════ event sounds ═══════════════════════════

def gen_spawn():
    dur = 0.9
    # rising shimmer that resolves into a bright chord
    rise = fm(seg_env([(0.0, 220), (0.75, 880), (1.0, 1100)], dur * 0.7), 2.0,
              seg_env([(0.0, 1.0), (1.0, 4.0)], dur * 0.7), dur * 0.7)
    rise *= np.linspace(0, 1, n_of(dur * 0.7)) ** 1.8
    sparkle = highpass(noise(dur, "spawn"), 6000) * np.linspace(0.05, 0.7, n_of(dur)) ** 2

    chord_d = 0.55
    chord = np.zeros(n_of(chord_d))
    for f, g in ((523.25, 1.0), (659.25, 0.7), (783.99, 0.55), (1046.5, 0.35)):
        chord += sine(f, chord_d) * exp_env(chord_d, attack=0.006, decay=3.2) * g
    whoosh = svf(noise(dur * 0.7, "spw"), seg_env([(0.0, 600), (1.0, 7000)], dur * 0.7), q=5, mode="bp")
    whoosh *= np.linspace(0, 1, n_of(dur * 0.7)) ** 2 * 0.4

    out = np.zeros(n_of(dur + chord_d))
    out[:n_of(dur * 0.7)] += rise[:n_of(dur * 0.7)] * 0.5 + whoosh
    out[:len(sparkle)] += sparkle * 0.35
    at(out, dur * 0.62, chord * 0.6)
    return reverb(out, decay=1.2, damp=7000, mix=0.32, name="spawn")


def gen_death():
    dur = 1.2
    # pitch/filter collapse + body thud + ghostly exhale
    fall = fm(seg_env([(0.0, 620), (0.35, 240), (1.0, 62)], dur * 0.8), 1.73,
              seg_env([(0.0, 4), (1.0, 10)], dur * 0.8), dur * 0.8)
    fall *= exp_env(dur * 0.8, attack=0.005, decay=3.2)
    fall = svf(fall, seg_env([(0.0, 4500), (1.0, 350)], dur * 0.8), q=4)

    fizz = svf(noise(dur, "death"), seg_env([(0.0, 5000), (1.0, 400)], dur), q=3, mode="bp")
    fizz *= exp_env(dur, attack=0.004, decay=4.0) * 0.5

    thud = sine(seg_env([(0.0, 130), (1.0, 40)], 0.25), 0.25) * exp_env(0.25, decay=6.0)
    exhale = bandpass(noise(0.5, "exh"), 300, 2200) * swell_env(0.5, 1.5) * 0.3

    out = np.zeros(n_of(dur + 0.3))
    out[:len(fall)] += saturate(fall, 2.2) * 0.75
    out[:len(fizz)] += fizz
    at(out, dur * 0.55, saturate(thud, 2.5) * 0.7)
    at(out, dur * 0.5, exhale)
    return reverb(out, decay=1.5, damp=3200, mix=0.34, name="death")


def gen_hit_taken():
    """You got hit — meaty, muffled, urgent. Should read as bad news."""
    dur = 0.55
    thump = sine(seg_env([(0.0, 210), (1.0, 48)], 0.28), 0.28) * exp_env(0.28, attack=0.001, decay=5.5)
    thump = saturate(thump, 3.2)
    smack = svf(noise(0.2, "hitt"), seg_env([(0.0, 3500), (1.0, 500)], 0.2), q=3)
    smack *= exp_env(0.2, attack=0.0008, decay=9.0)
    # dissonant downward "ugh"
    grunt = fm(seg_env([(0.0, 320), (1.0, 120)], 0.35), 1.41, seg_env([(0.0, 8), (1.0, 2)], 0.35), 0.35)
    grunt *= exp_env(0.35, attack=0.004, decay=5.0) * 0.5
    click = highpass(noise(0.008, "hitc"), 3000) * np.linspace(1, 0, n_of(0.008))
    out = np.zeros(n_of(dur))
    out[:len(thump)] += thump
    out[:len(smack)] += smack * 0.7
    out[:len(grunt)] += saturate(grunt, 2.4)
    out[:len(click)] += click * 0.5
    return reverb(out, decay=0.6, damp=2800, mix=0.26, name="hitt")


def gen_hit_dealt():
    """Hitmarker — crisp, bright, high-mid so it cuts through everything."""
    dur = 0.3
    tock = fm(seg_env([(0.0, 1500), (1.0, 620)], 0.13), 3.7,
              seg_env([(0.0, 7), (1.0, 0.6)], 0.13), 0.13)
    tock *= exp_env(0.13, attack=0.0008, decay=9.0)
    snap = highpass(noise(0.03, "hitd"), 3500) * exp_env(0.03, attack=0.0004, decay=12.0)
    # short upward confirm chirp
    chirp = sine(seg_env([(0.0, 900), (1.0, 1500)], 0.07), 0.07) * exp_env(0.07, attack=0.002, decay=6.0)
    body = sine(seg_env([(0.0, 300), (1.0, 150)], 0.1), 0.1) * exp_env(0.1, decay=8.0)
    out = np.zeros(n_of(dur))
    out[:len(tock)] += tock * 0.9
    out[:len(snap)] += snap * 0.8
    out[:len(body)] += body * 0.4
    at(out, 0.035, chirp * 0.35)
    return reverb(saturate(out, 2.2), decay=0.3, damp=8000, mix=0.2, name="hitd")


def gen_hit_other():
    """Someone else got hit — duller, neutral, sits behind the mix."""
    dur = 0.35
    thump = sine(seg_env([(0.0, 170), (1.0, 55)], 0.18), 0.18) * exp_env(0.18, decay=7.0)
    smack = bandpass(noise(0.14, "hito"), 300, 3000) * exp_env(0.14, attack=0.001, decay=10.0)
    out = np.zeros(n_of(dur))
    out[:len(thump)] += saturate(thump, 2.4) * 0.8
    out[:len(smack)] += smack * 0.55
    return reverb(out, decay=0.45, damp=3500, mix=0.24, name="hito")


def gen_dash():
    dur = 0.4
    whoosh = svf(noise(dur, "dash"), seg_env([(0.0, 700), (0.4, 4500), (1.0, 900)], dur), q=6, mode="bp")
    whoosh *= swell_env(dur, 1.4)
    thrust = fm(seg_env([(0.0, 180), (0.4, 420), (1.0, 150)], dur), 1.5,
                seg_env([(0.0, 6), (1.0, 1)], dur), dur)
    thrust *= swell_env(dur, 1.8) * 0.4
    return reverb(saturate(mixdown(whoosh, thrust), 2.0), decay=0.5, damp=6000, mix=0.26, name="dash")


def gen_teleport():
    dur = 0.6
    # zip out, pop in
    out_d, in_d = 0.22, 0.28
    zip_out = fm(seg_env([(0.0, 400), (1.0, 3000)], out_d), 2.0,
                 seg_env([(0.0, 6), (1.0, 1)], out_d), out_d)
    zip_out *= exp_env(out_d, attack=0.003, decay=4.0)
    pop_in = fm(seg_env([(0.0, 2600), (1.0, 380)], in_d), 1.41,
                seg_env([(0.0, 8), (1.0, 1)], in_d), in_d)
    pop_in *= exp_env(in_d, attack=0.001, decay=6.0)
    sparkle = highpass(noise(dur, "tp"), 6000) * exp_env(dur, attack=0.005, decay=5.0) * 0.3
    out = np.zeros(n_of(dur))
    out[:len(zip_out)] += zip_out * 0.6
    at(out, 0.26, pop_in * 0.7)
    out += sparkle
    out = delay_fx(out, 0.07, feedback=0.3, mix=0.25, taps=3)
    return reverb(saturate(out, 1.8), decay=0.8, damp=7500, mix=0.3, name="tp")


def gen_phase_shift():
    dur = 0.8
    n = n_of(dur)
    shimmer = svf(noise(dur, "ph"), seg_env([(0.0, 1500), (0.5, 7000), (1.0, 2500)], dur), q=7, mode="bp")
    shimmer *= tremolo(n, 9, 0.6) * swell_env(dur, 1.0)
    tone = fm(seg_env([(0.0, 380), (0.5, 620), (1.0, 460)], dur), 2.01,
              seg_env([(0.0, 1), (0.5, 5), (1.0, 1)], dur), dur)
    tone *= swell_env(dur, 1.3) * 0.4
    ring = np.sin(2 * np.pi * 7.0 * t_axis(dur))
    tone = tone * (0.7 + 0.3 * ring)
    out = mixdown(shimmer * 0.6, tone)
    return reverb(out, decay=1.2, damp=7000, mix=0.34, name="ph")


# ═══════════════════════════ music ═══════════════════════════

def midi_hz(m):
    return 440.0 * (2.0 ** ((m - 69) / 12.0))


def m_note(m_num, dur, kind="saw", detune=0.006, voices=3, fc=(2500, 700), q=3.0, drive=1.6, decay=4.0, attack=0.006):
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


def drum_kick(dur=0.32, f0=150, f1=44, drive=3.0):
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
        at_st(buf, b * bar, drum_kick(0.4, f0=120, f1=40, drive=2.0), 0.3)
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
                        fc=(bass_hi, 300), q=5.0, drive=3.0, decay=9.0, attack=0.002)
            at_st(buf, t, nb, 0.34)

        # power stab on the downbeat
        stab = np.zeros(n_of(beat * 0.8))
        for m_num in (bass + 12, bass + 19, bass + 24):
            stab += m_note(m_num, beat * 0.8, kind="saw", voices=2, detune=0.012,
                           fc=(3500, 800), q=4.0, drive=2.6, decay=7.0, attack=0.003)
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
                        fc=(5200, 1400), q=6.0, drive=2.0, decay=11.0, attack=0.002)
            at(arp, t, na, 0.15 if not section_b else 0.17)
    buf += ping_pong(arp, beat * 0.75, feedback=0.32, mix=0.3, taps=5)[:len(buf)]

    # drums
    kicks = []
    for b in range(bars):
        fill_bar = (b % 8) == 7
        for i in range(4):
            t = b * bar + i * beat
            at_st(buf, t, drum_kick(0.26, f0=160, f1=46, drive=3.4), 0.55)
            kicks.append(t)
        at_st(buf, b * bar + beat * 0.5, drum_kick(0.2, f0=140, f1=44, drive=3.0), 0.25)
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

def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    count = 0

    # (name, generator, level) — levels give the mix real dynamics instead of
    # normalizing every sound to the same loudness.
    # Pitched attacks are tuned to A minor, the key of both music tracks, so a
    # firefight stays harmonically coherent instead of sounding like noise.
    N = dict(D3=146.83, F3=174.61, A3=220.00, C4=261.63, D4=293.66, E4=329.63,
             G4=392.00, A4=440.00, C5=523.25, E5=659.25, G5=783.99, A5=880.00,
             E6=1318.51, G6=1567.98)
    zap_variants = {
        "atk_normal_bolt": (N["C5"], "neutral", 0.50),
        "atk_soul_bolt": (N["C4"], "dark", 0.62),
        "atk_haunt_bolt": (N["D4"], "dark", 0.62),
        "atk_arcane_bolt": (N["E5"], "arcane", 0.62),
        "atk_plague_bolt": (N["E4"], "poison", 0.60),
        "atk_flame_bolt": (N["A4"], "fire", 0.68),
        "atk_mud_glob": (N["F3"], "mud", 0.62),
        "atk_sand_shot": (N["G4"], "sand", 0.58),
        "atk_death_bolt": (N["D3"], "dark", 0.70),
        "atk_charm_bolt": (N["G5"], "twinkle", 0.55),
        "atk_nano_bolt": (N["G6"], "tech", 0.52),
        "atk_sting": (N["E6"], "sharp", 0.52),
        "atk_star_bolt": (N["A5"], "twinkle", 0.58),
        "atk_leech_bolt": (N["A3"], "dark", 0.62),
    }
    for name, (freq, tint, lvl) in zap_variants.items():
        save_wav(name, gen_zap(freq, tint, name=name), level=lvl,
                 width=0.55, transient=1.3, tail=(0.5, 0.16))
        count += 1

    beam_levels = dict(ice=0.62, drain=0.65, vine=0.60, laser=0.60, railgun=0.85, stone=0.66, whip=0.62, gravity=0.72)
    for tint in BEAM_TINTS:
        sub = (42, 0.5) if tint in ("railgun", "gravity") else None
        save_wav("atk_beam_" + tint, gen_beam(tint, name=tint), level=beam_levels[tint],
                 width=0.8, transient=0.8, sub=sub, tail=(0.8, 0.2))
        count += 1

    for tint, lvl in (("metal", 0.62), ("bone", 0.60), ("cursed", 0.64), ("holy", 0.62)):
        # spinners physically travel past the player, so sweep them across the field
        save_wav("atk_spinner_" + tint, gen_spinner(tint, name=tint), level=lvl,
                 width=0.7, transient=1.1, pan=(-0.55, 0.55), tail=(0.6, 0.18))
        count += 1

    simple = [
        ("atk_chain_bolt", gen_chain, 0.64, dict(width=0.85, transient=1.4, tail=(0.6, 0.2))),
        ("atk_spear_thrust", lambda: gen_physproj("spear"), 0.60, dict(width=0.5, transient=1.5, pan=(-0.5, 0.5))),
        ("atk_arrow_shot", lambda: gen_physproj("arrow"), 0.58, dict(width=0.5, transient=1.6, pan=(-0.5, 0.5))),
        ("atk_grenade_lob", gen_lobbed, 0.72, dict(width=0.6, transient=1.3, sub=(44, 0.6))),
        ("atk_aoe_boom", lambda: gen_explosion(dur=1.3, size=1.0), 0.92,
         dict(width=0.95, transient=1.2, sub=(38, 0.85), comp=(0.16, 5.0), tail=(1.4, 0.26))),
        ("atk_wind_wave", lambda: gen_wave("wind"), 0.62, dict(width=0.9, transient=0.6, pan=(-0.7, 0.7))),
        ("atk_sonic_wave", lambda: gen_wave("sonic"), 0.64, dict(width=0.9, transient=0.8, pan=(0.7, -0.7))),
        ("atk_gunshot", gen_bullet, 0.78, dict(width=0.55, transient=1.8, sub=(50, 0.45), comp=(0.18, 6.0))),
        ("atk_punch", gen_punch, 0.72, dict(width=0.45, transient=1.8, sub=(46, 0.7), comp=(0.18, 5.0))),
    ]
    for name, fn, lvl, kw in simple:
        save_wav(name, fn(), level=lvl, **kw)
        count += 1

    WIDE = dict(width=0.9, transient=0.9, tail=(1.0, 0.22))
    PUNCH = dict(width=0.5, transient=1.7, comp=(0.18, 5.0))
    SWEEP = dict(width=0.75, transient=1.4, pan=(-0.6, 0.6))
    specialized = [
        ("atk_tentacle_slap", gen_tentacle_slap, 0.62, dict(width=0.55, transient=1.5)),
        ("atk_fireball_whoosh", gen_fireball_whoosh, 0.78, dict(width=0.85, transient=0.9, sub=(46, 0.5))),
        ("atk_tidal_wave_crash", gen_tidal_wave_crash, 0.85, dict(width=1.0, transient=0.6, sub=(38, 0.6), tail=(1.5, 0.26))),
        ("atk_geyser_burst", gen_geyser_burst, 0.72, dict(width=0.8, transient=1.0)),
        ("atk_rocket_launch", gen_rocket_launch, 0.82, dict(width=0.7, transient=1.2, sub=(42, 0.6), pan=(-0.3, 0.8))),
        ("atk_talon_swipe", gen_talon_swipe, 0.62, SWEEP),
        ("atk_poison_dart", gen_poison_dart, 0.56, dict(width=0.5, transient=1.6, pan=(-0.45, 0.45))),
        ("atk_sword_wave_slash", gen_sword_wave_slash, 0.68, SWEEP),
        ("atk_blood_fang_bite", gen_blood_fang_bite, 0.68, PUNCH),
        ("atk_bat_swarm_screech", gen_bat_swarm_screech, 0.66, dict(width=1.0, transient=0.8, pan=(-0.8, 0.6))),
        ("atk_frost_shard_shatter", gen_frost_shard_shatter, 0.64, dict(width=0.85, transient=1.5, tail=(1.0, 0.24))),
        ("atk_lightning_crack", gen_lightning_crack, 0.82, dict(width=1.0, transient=1.6, sub=(44, 0.4), comp=(0.16, 5.0), tail=(1.2, 0.24))),
        ("atk_thunder_strike_boom", gen_thunder_strike_boom, 0.96,
         dict(width=1.0, transient=1.3, sub=(32, 0.9), comp=(0.15, 5.5), tail=(1.8, 0.28))),
        ("atk_boulder_rumble", gen_boulder_rumble, 0.84, dict(width=0.8, transient=1.1, sub=(36, 0.8), tail=(1.2, 0.24))),
        ("atk_thorn_shot", gen_thorn_shot, 0.56, dict(width=0.5, transient=1.7)),
        ("atk_inferno_blast_roar", gen_inferno_blast_roar, 0.78,
         dict(width=0.95, transient=0.9, sub=(36, 0.75), comp=(0.16, 5.0), tail=(1.5, 0.26))),
        ("atk_raise_dead_moan", gen_raise_dead_moan, 0.70, dict(width=1.0, transient=0.4, tail=(1.8, 0.3))),
        ("atk_wail_shriek", gen_wail_shriek, 0.74, dict(width=1.0, transient=0.5, pingpong=(0.21, 0.4, 0.28), tail=(1.8, 0.3))),
        ("atk_claw_swipe_slash", gen_claw_swipe_slash, 0.64, SWEEP),
        ("atk_devour_bite", gen_devour_bite, 0.72, dict(width=0.6, transient=1.6, sub=(44, 0.6), comp=(0.18, 5.0))),
        ("atk_scythe_swing", gen_scythe_swing, 0.66, SWEEP),
        ("atk_reap_slash", gen_reap_slash, 0.72, dict(width=0.9, transient=1.2, pan=(-0.5, 0.5), tail=(1.2, 0.24))),
        ("atk_shadow_bolt_whoosh", gen_shadow_bolt_whoosh, 0.70, dict(width=0.85, transient=1.0, tail=(1.2, 0.24))),
        ("atk_curse_hex", gen_curse_hex, 0.68, dict(width=1.0, transient=0.4, pingpong=(0.13, 0.42, 0.3), tail=(1.6, 0.3))),
        ("atk_holy_bolt_chime", gen_holy_bolt_chime, 0.68, dict(width=1.0, transient=0.7, tail=(2.0, 0.3))),
        ("atk_data_bolt_blip", gen_data_bolt_blip, 0.58, dict(width=0.8, transient=1.5, pingpong=(0.07, 0.3, 0.22))),
        ("atk_virus_glitch", gen_virus_glitch, 0.62, dict(width=0.95, transient=1.4)),
        ("atk_gravity_ball_warp", gen_gravity_ball_warp, 0.76, dict(width=0.95, transient=0.6, sub=(30, 0.9), tail=(1.8, 0.28))),
        ("atk_void_bolt_implode", gen_void_bolt_implode, 0.74,
         dict(width=1.0, transient=0.6, sub=(32, 0.7), pingpong=(0.16, 0.38, 0.26), tail=(1.8, 0.3))),
        ("atk_venom_bolt_splat", gen_venom_bolt_splat, 0.62, dict(width=0.6, transient=1.4)),
        ("atk_web_shot_splat", gen_web_shot_splat, 0.58, dict(width=0.55, transient=1.6)),
        ("atk_stinger_prick", gen_stinger_prick, 0.54, dict(width=0.6, transient=1.8)),
        ("atk_horn_bellow", gen_horn_bellow, 0.78, dict(width=0.9, transient=0.5, sub=(49, 0.5), tail=(1.8, 0.28))),
        ("atk_jaw_chomp", gen_jaw_chomp, 0.68, PUNCH),
    ]
    for name, fn, lvl, kw in specialized:
        save_wav(name, fn(), level=lvl, **kw)
        count += 1

    events = [
        ("spawn", gen_spawn, 0.70, dict(width=1.0, transient=0.7, tail=(1.6, 0.28))),
        ("death", gen_death, 0.80, dict(width=0.9, transient=0.9, sub=(38, 0.7), tail=(1.6, 0.28))),
        # hit_taken stays narrow and centred — it is about you, not about the arena
        ("hit_taken", gen_hit_taken, 0.85, dict(width=0.3, transient=1.9, sub=(42, 0.85), comp=(0.15, 6.0), tail=(0.5, 0.12))),
        ("hit_dealt", gen_hit_dealt, 0.68, dict(width=0.25, transient=2.0, comp=(0.16, 6.0), tail=(0.3, 0.10))),
        ("hit_other", gen_hit_other, 0.60, dict(width=0.7, transient=1.5, comp=(0.2, 4.5))),
        ("explosion", lambda: gen_explosion(dur=1.4, size=1.1), 0.94,
         dict(width=1.0, transient=1.2, sub=(34, 0.9), comp=(0.15, 5.5), tail=(1.8, 0.28))),
        ("dash", gen_dash, 0.62, dict(width=0.8, transient=1.3, pan=(-0.65, 0.65))),
        ("teleport", gen_teleport, 0.66, dict(width=0.95, transient=1.2, pingpong=(0.09, 0.35, 0.28), tail=(1.2, 0.26))),
        ("phase_shift", gen_phase_shift, 0.60, dict(width=1.0, transient=0.4, tail=(1.6, 0.3))),
    ]
    for name, fn, lvl, kw in events:
        save_wav(name, fn(), level=lvl, **kw)
        count += 1

    # menu sits back as ambience; battle is the louder, denser mix. Both are
    # already stereo, so master() only limits them.
    save_wav("music_menu", gen_menu_theme(), level=0.74, trim=False, tail=None)
    save_wav("music_battle", gen_battle_theme(), level=0.88, trim=False, tail=None)
    count += 2

    print("generated %d sounds in %s" % (count, os.path.normpath(OUT_DIR)))


if __name__ == "__main__":
    main()
