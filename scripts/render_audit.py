#!/usr/bin/env python3
"""Measure what render_audit rendered: how well every character and projectile stands out from the
ground of every map. Needs numpy and Pillow.

    bazel run //src/main/scala/com/gridgame/client:render_audit -- --out=/tmp/audit
    python3 scripts/render_audit.py /tmp/audit             # summary, worst cases, contact sheets
    python3 scripts/render_audit.py /tmp/audit --worst 40  # more of them

Every shot comes as a pair: `_a` as rendered, `_b` with the thing being judged taken out (a
character keeps its shadow, light and name plate and loses only its sprite). The difference
between the two, measured as CIE76 colour difference in Lab, is exactly what that sprite or
projectile adds to the frame:

- characters: `edge` is the upper quartile of the difference over the outer rim of the silhouette
  (the 6px band in from its edge: the soft halo and the sprite's own contour inside it), which
  is what separates a figure from what is behind it.
  `body` is the median over the whole silhouette. Under ~15 the figure is hard to pick out.
- projectiles: `strong` is how many pixels (per 1000 of its box) differ from the ground by more
  than 20, i.e. what still reads at a glance; `edge` as for characters.

Writes `report.txt` and contact sheets of the worst cases (`worst_*.png`, each cell the shot as
rendered beside the same ground without it) into the audit directory.
"""

import csv
import os
import sys
from collections import defaultdict

import numpy as np
from PIL import Image, ImageDraw


def srgb_to_lab(img):
    """HxWx3 uint8 sRGB -> HxWx3 float Lab (D65)."""
    c = img.astype(np.float32) / 255.0
    c = np.where(c <= 0.04045, c / 12.92, ((c + 0.055) / 1.055) ** 2.4)
    m = np.array([[0.4124564, 0.3575761, 0.1804375],
                  [0.2126729, 0.7151522, 0.0721750],
                  [0.0193339, 0.1191920, 0.9503041]], dtype=np.float32)
    xyz = c @ m.T
    xyz /= np.array([0.95047, 1.0, 1.08883], dtype=np.float32)
    e = 216 / 24389
    k = 24389 / 27
    f = np.where(xyz > e, np.cbrt(xyz), (k * xyz + 16) / 116)
    lab = np.empty_like(f)
    lab[..., 0] = 116 * f[..., 1] - 16
    lab[..., 1] = 500 * (f[..., 0] - f[..., 1])
    lab[..., 2] = 200 * (f[..., 1] - f[..., 2])
    return lab


def erode(mask, n):
    """Binary erosion by n pixels (4-neighbourhood), without scipy."""
    m = mask.copy()
    for _ in range(n):
        s = m.copy()
        s[1:, :] &= m[:-1, :]
        s[:-1, :] &= m[1:, :]
        s[:, 1:] &= m[:, :-1]
        s[:, :-1] &= m[:, 1:]
        m = s
    return m


class Pair:
    cache = {}

    @classmethod
    def load(cls, root, base):
        if base not in cls.cache:
            if len(cls.cache) > 4:
                cls.cache.clear()
            a = np.asarray(Image.open(os.path.join(root, base + "_a.png")).convert("RGB"))
            b = np.asarray(Image.open(os.path.join(root, base + "_b.png")).convert("RGB"))
            cls.cache[base] = (a, b, np.linalg.norm(srgb_to_lab(a) - srgb_to_lab(b), axis=2))
        return cls.cache[base]


def measure(root, row):
    a, b, de = Pair.load(root, row["file"])
    x0, y0, x1, y1 = (max(0, int(row[k])) for k in ("x0", "y0", "x1", "y1"))
    d = de[y0:y1, x0:x1]
    foot = d > 4.0
    n = int(foot.sum())
    if n < 12:
        return dict(n=n, edge=0.0, body=0.0, strong=0.0)
    # The outer 6px: a sprite's soft halo and, inside it, its own contour. The upper quartile is
    # the contour's contrast when there is one, rather than the halo's
    rim = foot & ~erode(foot, 6)
    edge = float(np.percentile(d[rim], 75)) if rim.any() else 0.0
    body = float(np.median(d[foot]))
    strong = 1000.0 * float((d > 20.0).sum()) / d.size
    return dict(n=n, edge=edge, body=body, strong=strong)


def sheet(root, rows, path, title):
    if not rows:
        return
    cells = []
    for r in rows:
        a, b, _ = Pair.load(root, r["file"])
        x0, y0, x1, y1 = (max(0, int(r[k])) for k in ("x0", "y0", "x1", "y1"))
        pad = 12
        box = (max(0, x0 - pad), max(0, y0 - pad), x1 + pad, y1 + pad)
        ca = Image.fromarray(a).crop(box)
        cb = Image.fromarray(b).crop(box)
        cell = Image.new("RGB", (ca.width * 2 + 4, ca.height + 22), (20, 20, 24))
        cell.paste(ca, (0, 22))
        cell.paste(cb, (ca.width + 4, 22))
        dr = ImageDraw.Draw(cell)
        dr.text((4, 4), f"{r['name']} / {r['map']} {r['ground']}  edge {r['edge']:.0f} body {r['body']:.0f}"
                        f" strong {r['strong']:.0f}", fill=(235, 235, 235))
        cells.append(cell)
    cols = 4
    cw = max(c.width for c in cells)
    ch = max(c.height for c in cells)
    rows_n = (len(cells) + cols - 1) // cols
    out = Image.new("RGB", (cw * cols, ch * rows_n + 24), (10, 10, 12))
    ImageDraw.Draw(out).text((6, 6), title, fill=(255, 255, 255))
    for i, c in enumerate(cells):
        out.paste(c, ((i % cols) * cw, 24 + (i // cols) * ch))
    out.save(path)


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)
    root = sys.argv[1]
    worst_n = 24
    if "--worst" in sys.argv:
        worst_n = int(sys.argv[sys.argv.index("--worst") + 1])
    with open(os.path.join(root, "index.csv")) as f:
        rows = list(csv.DictReader(f))
    for r in rows:
        r.update(measure(root, r))

    lines = []
    by_kind = defaultdict(list)
    for r in rows:
        by_kind[r["kind"]].append(r)

    for kind, key in (("char", "edge"), ("proj", "strong")):
        rs = by_kind.get(kind, [])
        if not rs:
            continue
        label = "characters" if kind == "char" else "projectiles"
        lines.append(f"== {label}: {len(rs)} measurements ==")
        per_ground = defaultdict(list)
        for r in rs:
            per_ground[(r["map"], r["ground"])].append(r[key])
        for (m, g), vals in sorted(per_ground.items()):
            v = np.array(vals)
            lines.append(f"  {m:14s} {g:10s} {key} median {np.median(v):6.1f}  p10 {np.percentile(v, 10):6.1f}"
                         f"  min {v.min():6.1f}")
        worst = sorted(rs, key=lambda r: r[key])[:worst_n]
        lines.append(f"  worst by {key}:")
        for r in worst:
            lines.append(f"    {r['name']:22s} {r['map']:14s} {r['ground']:10s} edge {r['edge']:5.1f}"
                         f"  body {r['body']:5.1f}  strong {r['strong']:6.1f}  px {r['n']}")
        sheet(root, worst, os.path.join(root, f"worst_{label}.png"), f"worst {label} by {key}")
        # Per map as well: a character that is fine everywhere but one ground is still a problem there
        for m in sorted({r["map"] for r in rs}):
            mw = sorted([r for r in rs if r["map"] == m], key=lambda r: r[key])[:12]
            sheet(root, mw, os.path.join(root, f"worst_{label}_{m}.png"), f"worst {label} on {m} by {key}")

    report = "\n".join(lines)
    with open(os.path.join(root, "report.txt"), "w") as f:
        f.write(report + "\n")
    print(report)


if __name__ == "__main__":
    main()
