#!/usr/bin/env python3
"""Generate sprites/warden.png -- the Warden.

A jailer in heavy plate: a round great helm with a barred visor and two
yellow eyes glowing behind the bars, oversized pauldrons, a chain slung across
his chest, a ring of keys at his hip, a gold padlock for a shield and a
length of chain with a manacle swinging from his fist.
"""

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sprite_base import (
    Ell, Limb, Poly, RRect, arc_pts, cel, generate_character, hand_at, head_skull, ink, lit,
    rig, rig_arms, rig_belt, rig_hand, rig_legs, rig_torso, shade, stroke,
)

STEEL = (144, 152, 176)
DARK = (84, 88, 108)
GOLD = (242, 196, 84)
CHAIN = (184, 192, 210)
GLOW = (255, 224, 110)
TABARD = (132, 42, 56)
VOID = (26, 24, 34)


def _links(draw, pts, size=1.3, color=CHAIN):
    """Chain links along a polyline, alternating face-on and edge-on."""
    total = []
    for i in range(len(pts) - 1):
        (x0, y0), (x1, y1) = pts[i], pts[i + 1]
        seg = math.hypot(x1 - x0, y1 - y0)
        n = max(1, int(seg / (size * 1.5)))
        for j in range(n):
            t = j / float(n)
            total.append((x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, math.atan2(y1 - y0, x1 - x0)))
    for k, (x, y, a) in enumerate(total):
        if k % 2 == 0:
            cel(draw, Ell(x, y, size, size * 0.8), color, sh=None, line_color=ink(color), lw=0.55)
            Ell(x, y, size * 0.45, size * 0.3).draw(draw, fill=shade(color, 2.2))
        else:
            ca, sa = math.cos(a), math.sin(a)
            stroke(draw, [(x - ca * size, y - sa * size), (x + ca * size, y + sa * size)], 0.9, shade(color, 0.8))


def _helm(r, draw):
    hx, hy, rx, ry, d = r.hx, r.head_cy, r.head_rx, r.head_ry, r.d
    cx0 = hx - d * 0.4
    helm = Ell(cx0, hy + 0.2, rx + 1.2, ry + 1.4)
    cel(draw, helm, STEEL, sh=(1.8, 1.4), hi=(0.9, 0.9))
    # ridge over the crown and a knob on top
    stroke(draw, [(cx0 - d * 2.0, hy - ry - 0.6), (cx0 - d * 2.0 if not d else cx0 + d * 6.0, hy - ry + 4.0)] if d
           else [(cx0, hy - ry - 0.8), (cx0, hy - 2.6)], 1.4, shade(STEEL, 0.8))
    cel(draw, Ell(cx0 - d * 1.0, hy - ry - 1.4, 1.8, 1.4), GOLD, sh=(0.4, 0.4))
    if r.back:
        for y in (hy + 2.0, hy + 6.0):
            stroke(draw, [(cx0 - rx + 1.0, y), (cx0 + rx - 1.0, y)], 0.7, shade(STEEL, 0.9))
        return
    # the visor: a dark slot with bars, eyes glowing behind them
    vx = cx0 + d * 3.0
    w = (rx - 1.4) if not d else rx * 0.62
    slot = RRect(vx - w, hy - 1.2, vx + w, hy + 5.0, 1.8)
    cel(draw, slot, VOID, sh=None, line_color=shade(STEEL, 1.8))
    for side, ex in (((-1, vx - 4.4), (1, vx + 4.4)) if not d else ((d, vx + d * 3.2),)):
        Poly([(ex - 2.4, hy + 1.9), (ex - 0.8, hy + 0.6), (ex + 2.2, hy + 0.9), (ex + 1.6, hy + 2.8),
              (ex - 1.2, hy + 3.0)] if side > 0 else
             [(ex + 2.4, hy + 1.9), (ex + 0.8, hy + 0.6), (ex - 2.2, hy + 0.9), (ex - 1.6, hy + 2.8),
              (ex + 1.2, hy + 3.0)]).draw(draw, fill=GLOW)
        Ell(ex, hy + 1.7, 0.7, 0.6).draw(draw, fill=(255, 255, 240))
    nbars = 5 if not d else 3
    for i in range(nbars):
        x = vx - w + (2 * w) * (i + 0.5) / nbars
        stroke(draw, [(x, hy - 1.0), (x, hy + 4.8)], 0.8, STEEL)
    # rivets round the visor
    for s in ((-1, 1) if not d else (-d,)):
        Ell(cx0 + s * (rx - 0.6), hy + 2.0, 0.7, 0.7).draw(draw, fill=lit(STEEL, 1.4))


def _pauldron(r, draw, side):
    sx, sy = r.shoulder(side)
    ox = side * 1.0 if not r.d else 0.0
    col = STEEL if r.near(side) else shade(STEEL, 0.6)
    for i in range(2):
        cel(draw, Ell(sx + ox, sy - 1.2 + i * 2.2, 4.6 - i * 0.6, 3.0 - i * 0.3), col if i == 0 else shade(col, 0.5),
            sh=(0.8, 0.8), hi=(0.5, 0.5) if i == 0 else None)
    Ell(sx + ox - 1.4, sy - 2.2, 0.6, 0.6).draw(draw, fill=GOLD)


def _padlock(r, draw, x, y, k=1.0):
    """The shield: a big gold padlock, keyhole and all."""
    cel(draw, Limb(arc_pts(x, y - 4.6 * k, 3.6 * k, 4.4 * k, 180, 360, 14), [1.3 * k] * 15), CHAIN, sh=None)
    body = RRect(x - 5.8 * k, y - 4.4 * k, x + 5.8 * k, y + 5.0 * k, 1.8 * k)
    cel(draw, body, GOLD, sh=(1.2, 1.2), hi=(0.7, 0.7))
    cel(draw, Ell(x, y - 0.4 * k, 1.5 * k, 1.5 * k), VOID, sh=None, line=False)
    Poly([(x - 0.8 * k, y), (x + 0.8 * k, y), (x + 0.5 * k, y + 2.6 * k), (x - 0.5 * k, y + 2.6 * k)]).draw(
        draw, fill=VOID)


def draw_warden(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=1.2)
    d = r.d
    lock_side = (-1 if not r.back else 1) if not d else -d
    chain_side = -lock_side if not d else d
    swing = [0.0, 1.2, 0.0, -1.2][frame]
    if d:
        h = hand_at(r, -d)
        _padlock(r, draw, r.cx - d * 4.0, r.sh_y + 3.6, 1.05)
        rig_arms(r, draw, DARK, STEEL, layer="far")
    rig_legs(r, draw, DARK, shade(STEEL, 1.2), width=1.1)
    rig_torso(r, draw, STEEL)
    cx = r.cx
    # tabard and chest chain
    if not d:
        cel(draw, Poly([(cx - 3.4, r.sh_y + 0.4), (cx + 3.4, r.sh_y + 0.4), (cx + 3.8, r.hip_y + 3.0),
                        (cx - 3.8, r.hip_y + 3.0)]), TABARD, sh=(0.8, 0.0))
    if not r.back:
        if d:
            _links(draw, [(cx - d * 3.4, r.sh_y - 1.4), (cx + d * 4.0, r.waist_y + 0.4)])
        else:
            _links(draw, [(cx - r.sh_w + 1.0, r.sh_y - 1.4), (cx + r.hip_w - 0.6, r.waist_y + 0.6)])
    rig_belt(r, draw, shade(DARK, 1.2), buckle=GOLD)
    if not r.back:
        # the key ring on the hip
        kx = cx + (4.8 if not d else d * 2.6)
        ky = r.waist_y + 4.4
        cel(draw, Ell(kx, ky, 2.0, 2.0), GOLD, sh=None, lw=0.8)
        Ell(kx, ky, 1.1, 1.1).draw(draw, fill=TABARD if not d else STEEL)
        for i, a in enumerate((-30, 10, 50)):
            ex = kx + math.cos(math.radians(90 + a)) * 4.4
            ey = ky + math.sin(math.radians(90 + a)) * 4.4
            stroke(draw, [(kx + math.cos(math.radians(90 + a)) * 1.6, ky + math.sin(math.radians(90 + a)) * 1.6),
                          (ex, ey)], 0.8, GOLD)
            Ell(ex, ey, 0.9, 0.9).draw(draw, fill=GOLD)
    rig_arms(r, draw, DARK, STEEL, layer="near", hands=False)
    for side in ((-1, 1) if not d else (d,)):
        _pauldron(r, draw, side)
    head_skull(r, draw, STEEL, ears=False)
    _helm(r, draw)
    # chain swinging from one fist, a manacle on the end
    h = hand_at(r, chain_side)
    if not r.back or True:
        end = (h[0] + (chain_side * 2.6 if not d else d * 3.4) + swing, h[1] + 8.0)
        _links(draw, [(h[0], h[1] + 1.0), ((h[0] + end[0]) / 2 + swing * 0.5, h[1] + 4.6), end], 1.15)
        cel(draw, Ell(end[0], end[1] + 1.6, 1.9, 1.7), CHAIN, sh=None, lw=0.8)
        Ell(end[0], end[1] + 1.6, 1.0, 0.9).draw(draw, fill=VOID if not r.back else shade(CHAIN, 2.0))
    for side in ((-1, 1) if not d else (d,)):
        rig_hand(r, draw, side, STEEL)
    if not d:
        hl = hand_at(r, lock_side)
        _padlock(r, draw, hl[0] + lock_side * 1.6, hl[1] - 3.0, 1.0 if not r.back else 0.95)


def main():
    generate_character("warden", draw_func=draw_warden)


if __name__ == "__main__":
    main()
