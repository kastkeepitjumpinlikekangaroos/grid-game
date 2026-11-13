#!/usr/bin/env python3
"""Generate sprites/assassin.png -- the Assassin.

A MapleStory night lord: a ninja wrap over the whole head with only a band of
face and a pair of red eyes showing, a steel brow plate on a pale headband
knotted behind with two tails, a long red scarf whose tails stream out behind,
a dark violet gi with bound forearms and shins, and a big throwing star held
by one blade in his left hand.
"""

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sprite_base import (
    SKIN, STEEL, Ell, Limb, Poly, RRect, arc_pts, bez, cel, clipped, generate_character, hand_at,
    head_face, head_skull, ink, lit, rig, rig_arms, rig_belt, rig_hand, rig_legs, rig_torso, shade,
    stroke, xform, arm_pts,
)

GI = (74, 58, 112)
WRAP = (172, 162, 194)
SCARF = (226, 52, 64)
IRIS = (214, 44, 64)
TABI = (46, 40, 58)


def _scarf_tails(r, draw):
    """The two long tails, streaming away behind (or to one side head-on):
    broad ribbons, the under one shorter and in shade."""
    cx, d = r.cx, r.d
    wave = [0.0, 1.0, 0.0, -1.0][r.frame]
    y0 = r.sh_y - 2.0
    if d:
        s, bx = -d, cx - d * 3.0
    else:
        s = 1 if not r.back else -1
        bx = cx + s * 3.6
    under = [(bx, y0 + 1.4), (bx + s * 4.6, y0 + 3.6 - wave), (bx + s * 9.0, y0 + 5.6 + wave),
             (bx + s * 11.8, y0 + 4.6 + wave * 0.5)]
    over = [(bx, y0), (bx + s * 5.4, y0 - 0.4 + wave), (bx + s * 10.8, y0 + 0.8 - wave),
            (bx + s * 15.2, y0 - 0.6 - wave * 0.4)]
    cel(draw, Limb(under, [1.9, 2.1, 1.8, 0.6]), shade(SCARF, 0.7), sh=(0.0, 0.8))
    cel(draw, Limb(over, [2.0, 2.4, 2.1, 0.7]), SCARF, sh=(0.0, 0.9))


def _scarf_wrap(r, draw):
    cx, d, y = r.cx, r.d, r.sh_y - 2.0
    shape = RRect(cx - 7.2, y - 1.8, cx + 7.2, y + 2.0, 1.8) if not d else RRect(cx - 5.4, y - 1.8, cx + 5.8, y + 2.0, 1.8)
    cel(draw, shape, SCARF, sh=(0.8, 0.8))
    if not r.back:
        stroke(draw, [(cx - 5.0 if not d else cx - 3.6, y + 0.2), (cx + 5.0 if not d else cx + 4.0, y + 0.6)], 0.5,
               shade(SCARF, 1.2))


def _headband(r, draw):
    """The band the brow plate is riveted to, round the head; from the side
    and behind, its knot and two tails."""
    hx, hy, rx, ry, d = r.hx, r.head_cy, r.head_rx, r.head_ry, r.d
    y0, y1 = hy - 6.0, hy - 2.7
    top = bez((hx - rx - 1, y0 + 0.9), (hx, y0 - 0.5), (hx + rx + 1, y0 + 0.9), 8)
    bottom = bez((hx + rx + 1, y1 + 0.9), (hx, y1 - 0.5), (hx - rx - 1, y1 + 0.9), 8)
    skull = Ell(hx, hy, rx - 0.5, ry - 0.5)

    def paint(l):
        Poly(top + bottom).draw(l, fill=WRAP)
        # its shadow side, then a line along each edge
        clipped(l, Poly(top + bottom), lambda m: Poly(
            [(hx + rx * 0.45, y0 - 2), (hx + rx + 2, y0 - 2), (hx + rx + 2, y1 + 3), (hx + rx * 0.62, y1 + 3)]).draw(
                m, fill=shade(WRAP)))
        stroke(l, top, 0.6, ink(WRAP))
        stroke(l, bottom, 0.6, ink(WRAP))

    clipped(draw, skull, paint)
    if r.front:
        return
    sway = [0.0, 1.0, 0.0, -1.0][r.frame]
    if d:
        kx, ky = hx - d * (rx - 0.2), hy - 3.9
        tails = [[(kx, ky), (kx - d * 3.6, ky + 1.6 + sway * 0.6), (kx - d * 6.2, ky + 4.8 + sway)],
                 [(kx, ky + 0.6), (kx - d * 2.2, ky + 3.4 - sway * 0.5), (kx - d * 3.6, ky + 7.0 - sway)]]
    else:
        kx, ky = hx, hy - 4.2
        tails = [[(kx - 0.6, ky), (kx - 2.6, ky + 3.4), (kx - 3.0 + sway, ky + 7.6)],
                 [(kx + 0.6, ky), (kx + 2.4, ky + 3.6), (kx + 3.4 + sway, ky + 6.6)]]
    for t in tails:
        cel(draw, Limb(t, [1.4, 1.3, 0.6]), WRAP, sh=(0.4, 0.5), lw=0.7)
    cel(draw, Ell(kx, ky + 0.2, 2.1, 1.9), WRAP, sh=(0.5, 0.5), lw=0.7)


def _head(r, draw):
    hx, hy, rx, ry, d = r.hx, r.head_cy, r.head_rx, r.head_ry, r.d
    head_skull(r, draw, GI, ears=False)
    _headband(r, draw)
    if r.back:
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
    # steel brow plate on the headband
    px = hx + d * 3.0
    cel(draw, RRect(px - 4.6, hy - 6.2, px + 4.6, hy - 2.5, 1.0), STEEL, sh=(0.0, 0.8), hi=(0.0, 0.5))
    stroke(draw, [(px - 1.6, hy - 4.4), (px + 1.6, hy - 4.4)], 0.5, shade(STEEL, 1.6))


def _shuriken(draw, x, y, rad, spin):
    """A four-pointed throwing star: broad bright blades, so it is steel and
    not an outline at the size the game shows it."""
    pts = []
    for i in range(8):
        a = math.radians(spin + i * 45.0)
        rr = rad if i % 2 == 0 else rad * 0.4
        pts.append((x + rr * math.cos(a), y + rr * math.sin(a)))
    cel(draw, Poly(pts), STEEL, sh=None, lw=0.65,
        regions=[(Poly([(x, y)] + pts[0:3]), shade(STEEL, 0.9)), (Poly([(x, y)] + pts[4:7]), shade(STEEL, 0.9))])
    cel(draw, Ell(x, y, rad * 0.2, rad * 0.2), shade(STEEL, 2.2), sh=None, lw=0.5)


def _wraps(r, draw, side):
    """The forearm's binding, elbow to wrist."""
    _, e, w, h = arm_pts(r, side)
    col = WRAP if r.near(side) else shade(WRAP, 0.6)
    # the forearm is short and the hand hides the wrist: start just above the elbow
    top = (e[0] - (w[0] - e[0]) * 0.3, e[1] - (w[1] - e[1]) * 0.3)
    cel(draw, Limb([top, w], [2.4, 2.2]), col, sh=(0.4, 0.3))
    mx, my = (top[0] + e[0]) / 2.0, (top[1] + e[1]) / 2.0 + 1.0
    stroke(draw, [(mx - 2.0, my - 0.3), (mx + 2.0, my + 0.3)], 0.5, shade(col, 1.5))


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
        cel(draw, RRect(fx - 2.5, fy - 6.6, fx + 2.5, fy - 2.3, 0.8), col, sh=(0.3, 0.0))
        stroke(draw, [(fx - 2.0, fy - 4.6), (fx + 2.0, fy - 4.2)], 0.5, shade(col, 1.5))
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
    # the star in his left hand, held still by one blade: the fingers close over it
    star_side = (1 if not r.back else -1) if not d else d
    h = hand_at(r, star_side)
    out = star_side if not d else d
    _shuriken(draw, h[0] + out * 2.7, h[1] - 2.3, 5.0, 22.5 if out > 0 else 67.5)
    for side in ((-1, 1) if not d else (d,)):
        rig_hand(r, draw, side, SKIN)


def main():
    generate_character("assassin", draw_func=draw_assassin)


if __name__ == "__main__":
    main()
