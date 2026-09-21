#!/usr/bin/env python3
"""Generate sprites/raptor.png -- the Raptor.

A bird of prey, specifically a bald eagle, so that it can never be mistaken
for the Hawk (a slate-blue falcon), the Griffin or the Harpy: a big white head
with a huge hooked yellow beak and a fierce brow over golden eyes, a dark
brown body, feathered wings for arms, a white tail fan and yellow taloned feet.
"""

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sprite_base import (
    DOWN, UP, LEFT, RIGHT, Ell, Limb, Poly, arc_pts, blob, cel, generate_character, ink, lit,
    ms_eye, shade, stroke, xform,
)

WHITE_F = (248, 246, 240)
WHITE_SH = (208, 212, 228)
BROWN = (112, 74, 48)
BROWN_LT = (156, 110, 70)
BEAK = (252, 198, 56)
FEET = (248, 190, 64)
TALON = (58, 48, 52)
IRIS = (246, 180, 36)


def _rot(pts, cx, cy, deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    return [(cx + (x - cx) * c - (y - cy) * s, cy + (x - cx) * s + (y - cy) * c) for (x, y) in pts]


def _head_shape(hx, hy, rx, ry, d, back=False):
    """A round head with feather tufts standing off the back of the crown."""
    parts = [Ell(hx, hy, rx, ry)]
    if d:
        for (u, v, a) in ((-rx * 0.8, -ry * 0.55, 205), (-rx * 0.98, -ry * 0.05, 185), (-rx * 0.45, -ry * 0.92, 240)):
            x, y = hx + d * u, hy + v
            ang = a if d > 0 else 180 - a
            tip = (x + math.cos(math.radians(ang)) * 4.2, y + math.sin(math.radians(ang)) * 4.2)
            parts.append(Poly([(x - 1.8 * math.sin(math.radians(ang)), y + 1.8 * math.cos(math.radians(ang))),
                               tip, (x + 1.8 * math.sin(math.radians(ang)), y - 1.8 * math.cos(math.radians(ang)))]))
    else:
        for (u, a) in ((-0.94, 196), (0.94, 344), (-0.9, 158), (0.9, 22)):
            x = hx + rx * math.cos(math.radians(a)) * 0.94
            y = hy + ry * math.sin(math.radians(a)) * 0.94
            tip = (x + math.cos(math.radians(a)) * 3.6, y + math.sin(math.radians(a)) * 3.6)
            parts.append(Poly([(x - 1.6 * math.sin(math.radians(a)), y + 1.6 * math.cos(math.radians(a))),
                               tip, (x + 1.6 * math.sin(math.radians(a)), y - 1.6 * math.cos(math.radians(a)))]))
    return parts


def _wing(draw, sx, sy, side, flap, near=True, back=False):
    """A folded wing hanging from the shoulder, primaries fanned at the tip."""
    col = BROWN if near else shade(BROWN, 0.6)
    tip_x = sx + side * (4.2 + flap * 2.4)
    tip_y = sy + 12.0 - flap * 2.0
    body = Poly([(sx - side * 1.6, sy - 1.4), (sx + side * 3.4, sy - 0.6), (tip_x + side * 1.6, tip_y - 3.0),
                 (tip_x + side * 1.2, tip_y + 1.0), (tip_x - side * 1.0, tip_y + 2.2),
                 (tip_x - side * 3.0, tip_y + 0.6), (sx - side * 2.4, sy + 6.0)])
    cel(draw, body, col, sh=(0.8, 0.8))
    # a lighter row of coverts, and the feather splits at the tip
    cel(draw, Poly([(sx - side * 0.6, sy + 0.2), (sx + side * 3.0, sy + 0.6), (sx + side * 3.6, sy + 4.0),
                    (sx - side * 1.0, sy + 3.2)]), BROWN_LT if near else shade(BROWN_LT, 0.6), sh=None, line=False)
    for k in (0.0, 1.6):
        stroke(draw, [(tip_x - side * (1.4 + k), tip_y - 4.4 + k), (tip_x - side * (1.0 + k * 0.8), tip_y + 0.8)],
               0.5, shade(col, 1.4))


def _feet(draw, x, y, d, near=True):
    col = FEET if near else shade(FEET, 0.6)
    if d:
        for k in (0.0, 1.0, 2.0):
            tx = x + d * (1.6 + k * 1.1)
            cel(draw, Limb([(x, y), (tx, y + 1.0 - k * 0.2)], [0.9, 0.7]), col, sh=None, lw=0.7)
            Poly([(tx, y + 0.3), (tx + d * 1.4, y + 1.0), (tx, y + 1.6)]).draw(draw, fill=TALON)
        return
    for k in (-1, 0, 1):
        tx = x + k * 1.7
        cel(draw, Limb([(x, y - 0.4), (tx, y + 1.0)], [0.95, 0.8]), col, sh=None, lw=0.7)
        Poly([(tx - 0.6, y + 1.4), (tx + 0.6, y + 1.4), (tx + k * 0.3, y + 2.8)]).draw(draw, fill=TALON)


def _beak_front(draw, hx, hy):
    upper = Poly([(hx - 3.8, hy + 1.4), (hx - 2.6, hy - 0.2), (hx + 2.6, hy - 0.2), (hx + 3.8, hy + 1.4),
                  (hx + 3.0, hy + 5.2), (hx + 1.2, hy + 8.2), (hx, hy + 9.2), (hx - 1.2, hy + 8.2), (hx - 3.0, hy + 5.2)])
    cel(draw, upper, BEAK, sh=(1.0, 0.6), hi=(0.5, 0.5))
    for s in (-1, 1):
        Ell(hx + s * 1.4, hy + 2.0, 0.5, 0.35).draw(draw, fill=shade(BEAK, 2.4))
    stroke(draw, [(hx - 2.2, hy + 5.6), (hx, hy + 6.6), (hx + 2.2, hy + 5.6)], 0.5, shade(BEAK, 1.6))


def _beak_side(draw, hx, hy, d):
    lower = Poly([(hx + d * 6.2, hy + 3.4), (hx + d * 12.2, hy + 3.8), (hx + d * 11.6, hy + 5.4), (hx + d * 6.8, hy + 5.6)])
    cel(draw, lower, shade(BEAK, 0.6), sh=None)
    upper = Poly([(hx + d * 5.6, hy - 1.8), (hx + d * 10.6, hy - 1.2), (hx + d * 14.4, hy + 0.8), (hx + d * 15.6, hy + 3.4),
                  (hx + d * 14.8, hy + 5.6), (hx + d * 13.4, hy + 3.8), (hx + d * 6.0, hy + 3.8)])
    cel(draw, upper, BEAK, sh=(0.0, 0.9), hi=(0.5, 0.5))
    Ell(hx + d * 9.4, hy + 0.6, 0.6, 0.4).draw(draw, fill=shade(BEAK, 2.4))
    stroke(draw, [(hx + d * 6.4, hy + 3.7), (hx + d * 13.2, hy + 3.9)], 0.5, shade(BEAK, 1.8))


def _tail(draw, x, y, d, back=False):
    if d:
        pts = [(x, y - 2.6), (x - d * 6.4, y - 1.4), (x - d * 8.4, y + 1.2), (x - d * 6.6, y + 2.2),
               (x - d * 7.6, y + 3.6), (x - d * 5.0, y + 3.6), (x, y + 2.2)]
    else:
        pts = [(x - 3.4, y - 1.0), (x + 3.4, y - 1.0), (x + 5.6, y + 5.6), (x + 3.4, y + 4.6), (x + 1.6, y + 6.4),
               (x, y + 5.0), (x - 1.6, y + 6.4), (x - 3.4, y + 4.6), (x - 5.6, y + 5.6)]
    cel(draw, Poly(pts), WHITE_F, sh=(0.6, 0.8), tone=WHITE_SH)


def draw_raptor(draw, ox, oy, direction, frame):
    bob = [0, -1, 0, -1][frame]
    ph = [0, 1, 0, -1][frame]
    flap = [0.0, 0.6, 0.0, 0.6][frame]
    base = oy + 54 + bob
    cx = ox + 32
    d = {DOWN: 0, UP: 0, LEFT: -1, RIGHT: 1}[direction]
    back = direction == UP
    hy = base - 31.0
    if d:
        hx = cx + d * 2.8
        bcx, bcy = cx - d * 1.2, base - 13.2
        _tail(draw, bcx - d * 7.0, base - 11.0, d)
        _wing(draw, cx - d * 1.4, base - 20.4, -d, flap * 0.4, near=False)
        # legs: stepping
        for side in (-1, 1):
            near = side == 1
            fwd = ph if near else -ph
            fx = cx + d * (0.8 + fwd * 3.0)
            fy = base - 1.4 - (0.8 if fwd < 0 else 0)
            col = FEET if near else shade(FEET, 0.6)
            cel(draw, Limb([(cx + d * (0.4 if near else -0.8), base - 7.0), (fx, fy)], [1.3, 1.1]), col, sh=None, lw=0.7)
            _feet(draw, fx, fy, d, near)
        body = Poly(_rot(arc_pts(bcx, bcy, 9.8, 8.6, 0, 360)[:-1], bcx, bcy, -d * 14))
        cel(draw, body, BROWN, sh=(1.4, 1.2))
        blob(draw, _head_shape(hx, hy, 10.6, 10.0, d), WHITE_F, sh=(1.2, 1.0), tone=WHITE_SH)
        _beak_side(draw, hx, hy, d)
        ms_eye(draw, hx + d * 3.4, hy - 0.2, 4.0, 4.6, IRIS, (d, 0.0), "sharp", skin=WHITE_F, side=-d)
        # the brow ridge that makes an eagle look like an eagle
        cel(draw, Poly([(hx + d * 0.6, hy - 3.8), (hx + d * 6.8, hy - 2.4), (hx + d * 6.4, hy - 1.2),
                        (hx + d * 1.0, hy - 2.4)]), WHITE_SH, sh=None, line_color=ink(WHITE_F), lw=0.6)
        wing = Poly([(cx + d * 3.6, base - 21.0), (cx - d * 2.6, base - 21.4), (cx - d * 11.0, base - 10.6 - flap * 2),
                     (cx - d * 12.6, base - 7.4 - flap * 2), (cx - d * 9.0, base - 8.2), (cx - d * 6.4, base - 6.6),
                     (cx + d * 1.6, base - 10.6)])
        cel(draw, wing, BROWN, sh=(0.8, 1.0))
        cel(draw, Poly([(cx + d * 3.2, base - 20.4), (cx - d * 2.2, base - 20.8), (cx - d * 4.0, base - 16.6),
                        (cx + d * 2.0, base - 15.4)]), BROWN_LT, sh=None, line=False)
        for k in (0.0, 2.2, 4.4):
            stroke(draw, [(cx - d * (4.0 + k), base - 14.4 + k * 0.6), (cx - d * (8.4 + k * 0.6), base - 9.4 - flap * 1.6)],
                   0.5, shade(BROWN, 1.3))
        return
    hx = cx
    bcx, bcy = cx, base - 13.4
    if back:
        _tail(draw, cx, base - 7.4, 0, back=True)
    for side in (-1, 1):
        fwd = ph * side
        fx = cx + side * 3.2
        fy = base - 1.4 + (0.4 if fwd > 0 else (-1.2 if fwd < 0 else 0.0))
        cel(draw, Limb([(cx + side * 3.0, base - 7.0), (fx, fy)], [1.3, 1.1]), FEET, sh=None, lw=0.7)
        if not back:
            _feet(draw, fx, fy, 0)
        else:
            cel(draw, Ell(fx, fy + 0.8, 2.0, 1.2), shade(FEET, 0.5), sh=None, lw=0.7)
    if not back:
        _tail(draw, cx, base - 7.0, 0)
    cel(draw, Ell(bcx, bcy, 8.8, 9.6), BROWN, sh=(1.6, 1.2))
    if not back:
        for (x, y) in ((-2.6, -3.0), (2.6, -3.0), (0.0, 0.4), (-2.6, 3.4), (2.6, 3.4)):
            stroke(draw, [(bcx + x - 1.2, bcy + y), (bcx + x, bcy + y + 1.0), (bcx + x + 1.2, bcy + y)], 0.5,
                   BROWN_LT)
    for side in (-1, 1):
        _wing(draw, cx + side * 6.6, base - 20.6, side, flap)
    blob(draw, _head_shape(hx, hy, 11.2, 10.2, 0), WHITE_F, sh=(1.2, 1.0), tone=WHITE_SH)
    if back:
        return
    for side in (-1, 1):
        ex = hx + side * 5.2
        ms_eye(draw, ex, hy + 0.2, 4.0, 4.8, IRIS, (0.0, 0.0), "sharp", skin=WHITE_F, side=side)
        cel(draw, Poly([(ex - side * 2.8, hy - 1.6), (ex - side * 1.4, hy - 3.8), (ex + side * 3.0, hy - 3.4),
                        (ex + side * 2.8, hy - 2.2)]), WHITE_SH, sh=None, line_color=ink(WHITE_F), lw=0.6)
    _beak_front(draw, hx, hy + 1.6)


def main():
    generate_character("raptor", draw_func=draw_raptor)


if __name__ == "__main__":
    main()
