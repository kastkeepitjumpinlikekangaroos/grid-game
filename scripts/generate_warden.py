#!/usr/bin/env python3
"""Generate sprites/warden.png -- the Warden.

A jailer in heavy plate: a round great helm whose visor is a barred cell
window, two yellow eyes glowing between the bars, oversized pauldrons, a
crimson tabard, one big gold key at his belt, a gold padlock for a shield on
his right arm and a length of chain ending in an open manacle swinging from
his left fist. In profile the padlock is on the arm nearest the camera and
covers him as a shield does, the chain held out ahead of him.
"""

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sprite_base import (
    Ell, Limb, Poly, RRect, arc_pts, blob, cel, generate_character, hand_at, head_skull, ink, lit,
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
        n = max(1, int(round(seg / (size * 1.5))))
        for j in range(n):
            t = j / float(n)
            total.append((x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, math.atan2(y1 - y0, x1 - x0)))
    for k, (x, y, a) in enumerate(total):
        if k % 2 == 0:
            cel(draw, Ell(x, y, size, size * 0.85), color, sh=None, line_color=ink(color), lw=0.55)
            Ell(x, y, size * 0.42, size * 0.3).draw(draw, fill=shade(color, 2.4))
        else:
            ca, sa = math.cos(a), math.sin(a)
            stroke(draw, [(x - ca * size * 0.9, y - sa * size * 0.9), (x + ca * size * 0.9, y + sa * size * 0.9)],
                   size * 0.72, shade(color, 1.0))


def _manacle(draw, x, y, r=1.9):
    """The open cuff on the end of the chain."""
    cel(draw, Limb(arc_pts(x, y, r, r, -50, 230, 14), [0.8] * 15, cap=True, cap0=True), CHAIN, sh=None, lw=0.6)


def _helm(r, draw):
    hx, hy, rx, ry, d = r.hx, r.head_cy, r.head_rx, r.head_ry, r.d
    cx0 = hx - d * 0.4
    helm = Ell(cx0, hy + 0.2, rx + 1.2, ry + 1.4)
    regions = []
    if r.back:
        # the neck guard: a darker lame under two riveted bands
        regions.append((Poly([(cx0 - 20, hy + 6.4), (cx0 + 20, hy + 6.4), (cx0 + 20, hy + 20), (cx0 - 20, hy + 20)]),
                        shade(STEEL, 0.8)))
    cel(draw, helm, STEEL, sh=(1.8, 1.4), hi=(0.9, 0.9), regions=regions)
    # ridge over the crown and a knob on top
    stroke(draw, [(cx0 - d * 2.0, hy - ry - 0.6), (cx0 - d * 2.0 if not d else cx0 + d * 6.0, hy - ry + 4.0)] if d
           else [(cx0, hy - ry - 0.8), (cx0, hy - 2.6)], 1.4, shade(STEEL, 0.8))
    cel(draw, Ell(cx0 - d * 1.0, hy - ry - 1.6, 2.1, 1.7), GOLD, sh=(0.4, 0.4))
    if r.back:
        for y in (hy + 1.6, hy + 6.4):
            k = (1.0 - ((y - hy - 0.2) / (ry + 1.4)) ** 2) ** 0.5
            w = (rx + 1.2) * k - 0.8
            stroke(draw, [(cx0 - w, y), (cx0 + w, y)], 1.0, shade(STEEL, 1.3))
            for s in (-1, 0, 1):
                Ell(cx0 + s * w * 0.62, y - 1.5, 0.7, 0.7).draw(draw, fill=lit(STEEL, 1.4))
        return
    # the visor: a cell window. A dark opening, the eyes glowing in it, and
    # the bars set so that each eye shows whole between two of them
    vx = cx0 + d * 3.0
    w = (rx - 1.4) if not d else rx * 0.62
    slot = RRect(vx - w, hy - 1.7, vx + w, hy + 5.5, 1.8)
    cel(draw, slot, VOID, sh=None, line_color=shade(STEEL, 1.9), lw=1.1)
    eyes = ((-1, vx - 4.4), (1, vx + 4.4)) if not d else ((d, vx + d * 3.2),)
    for side, ex in eyes:
        Poly([(ex - side * 1.6, hy + 1.3), (ex + side * 1.6, hy + 0.1), (ex + side * 1.6, hy + 3.2),
              (ex - side * 1.6, hy + 3.9)]).draw(draw, fill=GLOW)
        Ell(ex + side * 0.2, hy + 2.1, 0.85, 0.8).draw(draw, fill=(255, 255, 236))
    bars = (vx - 6.6, vx - 2.2, vx + 2.2, vx + 6.6) if not d else (vx - d * 4.9, vx + d * 0.5, vx + d * 5.9)
    for x in bars:
        stroke(draw, [(x, hy - 1.4), (x, hy + 5.2)], 1.2, STEEL)
        stroke(draw, [(x + 0.45, hy - 1.2), (x + 0.45, hy + 5.0)], 0.4, shade(STEEL, 1.2))
    # rivets beside the window
    for s in ((-1, 1) if not d else (-d,)):
        Ell(cx0 + s * (rx - 0.4) if not d else vx + s * (w + 1.6), hy + 1.9, 0.7, 0.7).draw(draw, fill=lit(STEEL, 1.4))


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


def _key(draw, x, y, behind, turn=1):
    """One big key hanging by its bow from the belt at (x, y); `behind` is the
    colour seen through the bow."""
    blob(draw, [Ell(x, y + 2.0, 2.2, 2.2), RRect(x - 0.7, y + 3.6, x + 0.7, y + 9.0, 0.4),
                RRect(x, y + 6.0, x + turn * 2.4, y + 7.0, 0.2), RRect(x, y + 7.8, x + turn * 2.0, y + 8.9, 0.2)],
         GOLD, sh=(0.5, 0.5), lw=0.6)
    Ell(x, y + 2.0, 0.95, 0.95).draw(draw, fill=behind)


def draw_warden(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=1.2)
    d = r.d
    cx = r.cx
    swing = [0.0, 1.2, 0.0, -1.2][frame]
    if d:
        _draw_profile(r, draw, swing)
        return
    lock_side = -1 if not r.back else 1          # his right arm
    chain_side = -lock_side                      # his left fist
    rig_legs(r, draw, DARK, shade(STEEL, 1.2), width=1.1)
    rig_torso(r, draw, STEEL)
    cel(draw, Poly([(cx - 3.4, r.sh_y + 0.4), (cx + 3.4, r.sh_y + 0.4), (cx + 3.8, r.hip_y + 3.0),
                    (cx - 3.8, r.hip_y + 3.0)]), TABARD, sh=(0.8, 0.0))
    rig_belt(r, draw, shade(DARK, 1.2), buckle=GOLD)
    # the key on his left hip
    _key(draw, cx + chain_side * 4.4, r.waist_y + 2.0, TABARD if not r.back else DARK, turn=chain_side)
    rig_arms(r, draw, DARK, STEEL, layer="near", hands=False)
    for side in (-1, 1):
        _pauldron(r, draw, side)
    head_skull(r, draw, STEEL, ears=False)
    _helm(r, draw)
    # chain swinging from his left fist, the manacle on its end
    h = hand_at(r, chain_side)
    end = (h[0] + chain_side * 3.4 + swing * 1.3, h[1] + 5.8)
    _links(draw, [(h[0], h[1] + 1.0), ((h[0] + end[0]) / 2 - chain_side * 0.6, h[1] + 4.0), end], 1.4)
    _manacle(draw, end[0] + chain_side * 0.5, end[1] + 2.0)
    for side in (-1, 1):
        rig_hand(r, draw, side, STEEL)
    hl = hand_at(r, lock_side)
    _padlock(r, draw, hl[0] + lock_side * 1.6, hl[1] - 3.0, 1.0 if not r.back else 0.95)


def _draw_profile(r, draw, swing):
    """The padlock on the arm nearest the camera, covering him as a shield
    does; the chain held out ahead of him in the far fist."""
    d, cx = r.d, r.cx
    far = dict(reach=0.5, out=6.6)
    rig_arms(r, draw, DARK, STEEL, layer="far", **far)
    h = hand_at(r, -d, **far)
    end = (h[0] + d * 0.8 + swing, h[1] + 7.6)
    _links(draw, [(h[0], h[1] + 1.0), ((h[0] + end[0]) / 2 + swing * 0.4, h[1] + 4.4), end], 1.4)
    _manacle(draw, end[0] + swing * 0.2, end[1] + 2.0)
    rig_hand(r, draw, -d, STEEL, **far)
    rig_legs(r, draw, DARK, shade(STEEL, 1.2), width=1.1)
    rig_torso(r, draw, STEEL)
    rig_belt(r, draw, shade(DARK, 1.2), buckle=GOLD)
    rig_arms(r, draw, DARK, STEEL, layer="near", hands=False)
    _pauldron(r, draw, d)
    _padlock(r, draw, cx + d * (0.6 - r.ph * 1.0), r.waist_y + 1.4, 0.92)
    head_skull(r, draw, STEEL, ears=False)
    _helm(r, draw)


def main():
    generate_character("warden", draw_func=draw_warden)


if __name__ == "__main__":
    main()
