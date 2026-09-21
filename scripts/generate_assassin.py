#!/usr/bin/env python3
"""Generate sprites/assassin.png -- the Assassin.

A MapleStory night lord: a ninja wrap over the whole head with only a band of
face and a pair of red eyes showing, a steel brow plate, a long red scarf whose
tails stream out behind, a dark violet gi with bound forearms and shins, and a
big throwing star in hand.
"""

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sprite_base import (
    SKIN, STEEL, Ell, Limb, Poly, RRect, cel, generate_character, hand_at, head_face,
    head_skull, ink, lit, rig, rig_arms, rig_belt, rig_hand, rig_legs, rig_torso, shade,
    stroke, xform, arm_pts,
)

GI = (74, 58, 112)
WRAP = (172, 162, 194)
SCARF = (226, 52, 64)
IRIS = (214, 44, 64)
TABI = (46, 40, 58)


def _scarf_tails(r, draw):
    """The two long tails, streaming away behind (or to one side head-on)."""
    cx, d = r.cx, r.d
    wave = [0.0, 1.0, 0.0, -1.0][r.frame]
    y0 = r.sh_y - 2.0
    if d:
        bx = cx - d * 3.0
        tails = [[(bx, y0), (bx - d * 6.0, y0 - 1.4 + wave), (bx - d * 12.0, y0 + 0.4 - wave), (bx - d * 16.0, y0 - 1.2)],
                 [(bx, y0 + 1.0), (bx - d * 5.0, y0 + 2.4 - wave), (bx - d * 10.0, y0 + 4.0 + wave),
                  (bx - d * 13.0, y0 + 3.0)]]
    else:
        s = 1 if not r.back else -1
        bx = cx + s * 3.6
        tails = [[(bx, y0), (bx + s * 5.4, y0 + 1.0 + wave), (bx + s * 10.6, y0 - 0.4 - wave), (bx + s * 14.6, y0 + 1.2)],
                 [(bx, y0 + 1.2), (bx + s * 4.4, y0 + 4.0 - wave), (bx + s * 8.6, y0 + 6.2 + wave),
                  (bx + s * 11.2, y0 + 5.0)]]
    for t in tails:
        cel(draw, Limb(t, [1.6, 1.5, 1.3, 0.5]), SCARF, sh=(0.0, 0.8))


def _scarf_wrap(r, draw):
    cx, d, y = r.cx, r.d, r.sh_y - 2.0
    shape = RRect(cx - 7.2, y - 1.8, cx + 7.2, y + 2.0, 1.8) if not d else RRect(cx - 5.4, y - 1.8, cx + 5.8, y + 2.0, 1.8)
    cel(draw, shape, SCARF, sh=(0.8, 0.8))
    if not r.back:
        stroke(draw, [(cx - 5.0 if not d else cx - 3.6, y + 0.2), (cx + 5.0 if not d else cx + 4.0, y + 0.6)], 0.5,
               shade(SCARF, 1.2))


def _head(r, draw):
    hx, hy, rx, ry, d = r.hx, r.head_cy, r.head_rx, r.head_ry, r.d
    head_skull(r, draw, GI, ears=False)
    if r.back:
        # the knot at the back of the wrap
        cel(draw, Ell(hx, hy + 1.0, 2.2, 1.8), shade(GI, 0.6), sh=None)
        for s in (-1, 1):
            cel(draw, Limb([(hx, hy + 1.0), (hx + s * 3.0, hy + 5.4 + [0, 1, 0, -1][r.frame] * s)], [1.1, 0.6]),
                shade(GI, 0.6), sh=None)
        return
    # the band of face the wrap leaves open
    if d:
        band = RRect(hx - d * 1.6 - 3.4, hy - 1.9, hx + d * rx + 0.4, hy + 5.4, 2.2) if d > 0 else \
            RRect(hx - rx - 0.4, hy - 1.9, hx + 1.6 + 3.4, hy + 5.4, 2.2)
    else:
        band = RRect(hx - rx + 1.4, hy - 1.9, hx + rx - 1.4, hy + 5.4, 2.6)
    cel(draw, band, SKIN, sh=(0.0, 0.8), line_color=ink(GI))
    head_face(r, draw, SKIN, iris=IRIS, mood="sharp", mouth=None, blush=False, brows=True,
              brow_color=shade(GI, 1.4))
    # steel brow plate on the wrap
    px = hx + d * 3.0
    cel(draw, RRect(px - 4.6, hy - 5.8, px + 4.6, hy - 2.6, 1.0), STEEL, sh=(0.0, 0.7))
    stroke(draw, [(px - 1.6, hy - 4.2), (px + 1.6, hy - 4.2)], 0.5, shade(STEEL, 1.6))


def _shuriken(draw, x, y, rad, spin):
    pts = []
    for i in range(8):
        a = math.radians(spin + i * 45.0)
        rr = rad if i % 2 == 0 else rad * 0.34
        pts.append((x + rr * math.cos(a), y + rr * math.sin(a)))
    cel(draw, Poly(pts), STEEL, sh=None, regions=[(Poly([(x, y)] + pts[0:3]), shade(STEEL, 0.9)),
                                                  (Poly([(x, y)] + pts[4:7]), shade(STEEL, 0.9))])
    cel(draw, Ell(x, y, rad * 0.24, rad * 0.24), shade(STEEL, 2.0), sh=None)


def _wraps(r, draw, side):
    _, e, w, h = arm_pts(r, side)
    col = WRAP if r.near(side) else shade(WRAP, 0.6)
    cel(draw, Limb([((e[0] + w[0]) / 2, (e[1] + w[1]) / 2), w], [2.1, 2.0]), col, sh=(0.4, 0.3))
    mx, my = (e[0] + w[0] * 3) / 4.0, (e[1] + w[1] * 3) / 4.0
    stroke(draw, [(mx - 1.8, my - 0.4), (mx + 1.8, my + 0.4)], 0.5, shade(col, 1.4))


def draw_assassin(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=0.95)
    d = r.d
    if not r.back:
        _scarf_tails(r, draw)
    if d:
        rig_arms(r, draw, GI, SKIN, layer="far")
    rig_legs(r, draw, GI, TABI)
    for side in ((-1, 1) if not d else (-d, d)):
        fx, fy = r.foot(side)
        col = WRAP if r.near(side) else shade(WRAP, 0.6)
        cel(draw, RRect(fx - 2.4, fy - 5.6, fx + 2.4, fy - 2.4, 0.8), col, sh=(0.3, 0.0))
    rig_torso(r, draw, GI)
    if not r.back:
        cx = r.cx + d * 1.6
        cel(draw, Poly([(cx - 3.6, r.sh_y - 2.0), (cx + 3.6, r.sh_y - 2.0), (cx + 0.6, r.waist_y + 0.6),
                        (cx - 0.6, r.waist_y + 0.6)]), lit(GI, 0.8), sh=None, line=False)
    rig_belt(r, draw, shade(GI, 2.0), buckle=SCARF)
    rig_arms(r, draw, GI, SKIN, layer="near", hands=False)
    for side in ((-1, 1) if not d else (d,)):
        _wraps(r, draw, side)
    _head(r, draw)
    _scarf_wrap(r, draw)
    if r.back:
        _scarf_tails(r, draw)
    star_side = (1 if not r.back else -1) if not d else d
    for side in ((-1, 1) if not d else (d,)):
        h = rig_hand(r, draw, side, SKIN)
        if side == star_side and not r.back:
            _shuriken(draw, h[0] + (2.4 if not d else d * 2.6), h[1] - 2.4, 4.4, r.frame * 22.5)


def main():
    generate_character("assassin", draw_func=draw_assassin)


if __name__ == "__main__":
    main()
