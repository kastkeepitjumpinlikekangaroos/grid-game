#!/usr/bin/env python3
"""Generate sprites/raptor.png -- the Raptor.

A bird of prey, specifically a bald eagle, so that it can never be mistaken
for the Hawk (a slate-blue falcon), the Griffin or the Harpy: a big white head
whose feathers end in a ragged hood over the shoulders, a heavy brow over
yellow eyes with black pupils, a huge hooked yellow beak, a dark brown body,
folded wings with their primaries fanned at the tips, a white tail fan (seen
in profile and from behind, never head-on) and yellow taloned feet.
"""

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sprite_base import (
    DOWN, UP, LEFT, RIGHT, Ell, Limb, Poly, arc_pts, blob, cel, generate_character, ink, lit,
    shade, stroke,
)

WHITE_F = (248, 246, 240)
WHITE_SH = (208, 212, 228)
WHITE_LINE = (124, 126, 160)       # the head's line: a slate, lighter than the kit's ink for white
BROWN = (118, 78, 50)
BROWN_LT = (170, 122, 76)
WING = (92, 60, 44)
BEAK = (252, 198, 56)
FEET = (248, 190, 64)
TALON = (58, 48, 52)
IRIS = (255, 216, 60)
PUPIL = (34, 26, 30)
BROW = (72, 54, 62)


def _rot(pts, cx, cy, deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    return [(cx + (x - cx) * c - (y - cy) * s, cy + (x - cx) * s + (y - cy) * c) for (x, y) in pts]


def _tuft(x, y, ang, length, half):
    """One feather tip: a triangle standing on (x, y), pointing along `ang`."""
    a = math.radians(ang)
    ca, sa = math.cos(a), math.sin(a)
    return Poly([(x - half * sa, y + half * ca), (x + length * ca, y + length * sa), (x + half * sa, y - half * ca)])


def _head_shape(hx, hy, rx, ry, d, back=False):
    """A round head whose white feathers end in a ragged hood: tips along its
    lower edge, lying over the shoulders. The crown is smooth."""
    parts = [Ell(hx, hy, rx, ry)]
    if d:
        # (where on the head, which way it points, how long), facing right
        spec = ((66, 84, 2.4), (98, 96, 3.0), (124, 116, 3.2), (150, 142, 3.0), (176, 168, 2.2))
        for (a, dirn, ln) in spec:
            if d < 0:
                a, dirn = 180 - a, 180 - dirn
            x = hx + rx * 0.9 * math.cos(math.radians(a))
            y = hy + ry * 0.9 * math.sin(math.radians(a))
            parts.append(_tuft(x, y, dirn, ln + 1.0, 2.2))
        return parts
    spec = [(26, 46, 2.2), (50, 68, 3.0), (73, 83, 3.2)]
    if back:
        spec.append((90, 90, 3.4))
    for (a, dirn, ln) in spec:
        for s in ((1, -1) if a != 90 else (1,)):
            aa, dd = (a, dirn) if s > 0 else (180 - a, 180 - dirn)
            x = hx + rx * 0.9 * math.cos(math.radians(aa))
            y = hy + ry * 0.9 * math.sin(math.radians(aa))
            parts.append(_tuft(x, y, dd, ln + 1.0, 2.2))
    return parts


def _head(draw, hx, hy, rx, ry, d, back=False):
    blob(draw, _head_shape(hx, hy, rx, ry, d, back), WHITE_F, sh=(1.3, 1.1), tone=WHITE_SH,
         line_color=WHITE_LINE, lw=0.8)


def _eye(draw, x, y, look=0.0, w=4.8, h=5.2):
    """An eagle's eye: all yellow iris round a black pupil, no white."""
    rx, ry = w / 2.0, h / 2.0
    cel(draw, Ell(x, y, rx, ry), IRIS, sh=None, line_color=BROW, lw=0.6)
    px = x + look * rx * 0.3
    Ell(px, y + 0.4, rx * 0.46, ry * 0.52).draw(draw, fill=PUPIL)
    Ell(px - rx * 0.3, y - ry * 0.16, 0.75, 0.75).draw(draw, fill=(255, 255, 255))


def _brow(draw, outer, inner, drop=2.0):
    """The brow ridge that makes an eagle look like an eagle: a white shelf
    over the eye, sloping down to the beak, with its shadow under it."""
    (ox_, oy_), (ix, iy) = outer, inner
    Poly([(ox_, oy_ - drop), (ix, iy - drop - 0.4), (ix, iy), (ox_, oy_)]).draw(draw, fill=WHITE_F)
    stroke(draw, [(ox_, oy_ - 0.1), (ix, iy + 0.1)], 1.0, BROW)


def _wing(draw, sx, sy, s, swing=0.0, back=False, near=True):
    """A folded wing hanging from the shoulder (sx, sy) on side `s`, seen
    head-on or from behind: darker than the body, a paler scalloped row of
    coverts across the shoulder, and three primaries fanned at the tip.
    From behind the two wings close over the back and meet at the spine."""
    if back:
        local = [(-5.4, -1.0), (1.6, -1.2), (4.8, 2.4), (5.3, 7.0), (4.4, 10.6), (2.6, 12.6), (1.6, 11.4),
                 (-0.6, 13.4), (-1.8, 11.6), (-3.6, 11.8), (-5.6, 8.0)]
        coverts = [(-3.6, 2.6, 2.6, 3.4), (0.4, 2.8, 2.6, 3.6), (3.6, 2.4, 2.4, 3.2), (0.0, -0.6, 7.0, 2.4)]
        splits = [((1.6, 11.4), (1.8, 7.0)), ((-1.8, 11.6), (-1.8, 7.2))]
    else:
        local = [(-2.0, -0.8), (2.4, -1.4), (5.0, 2.6), (5.6, 7.4), (5.3, 12.2), (4.2, 11.4), (3.2, 14.4),
                 (2.0, 12.6), (0.8, 13.6), (-1.0, 10.0), (-2.6, 4.6)]
        coverts = [(3.8, 2.2, 2.4, 3.3), (1.0, 2.6, 2.4, 3.5), (-1.6, 2.2, 2.2, 3.1), (1.0, -0.6, 5.0, 2.4)]
        splits = [((4.2, 11.4), (4.2, 7.0)), ((2.0, 12.6), (1.6, 7.4))]
    deg = -s * swing * 9.0

    def place(pts):
        return _rot([(sx + s * u, sy + v) for (u, v) in pts], sx, sy, deg)

    col = WING if near else shade(WING, 0.6)
    regions = []
    for (u, v, rx, ry) in coverts:
        c = place([(u, v)])[0]
        regions.append((Ell(c[0], c[1], rx, ry), BROWN_LT if near else shade(BROWN_LT, 0.6)))
    cel(draw, Poly(place(local)), col, sh=(0.9, 0.9), regions=regions)
    for seg in splits:
        stroke(draw, place(list(seg)), 0.6, shade(col, 1.6))


def _wing_side(draw, cx, base, d, lift=0.0):
    """The near wing in profile, folded along the body: wrist at the chest,
    primaries reaching back over the root of the tail."""
    local = [(3.8, -20.6), (-3.0, -22.0), (-9.0, -17.0), (-12.8, -10.4), (-11.0, -10.6), (-11.8, -7.6),
             (-9.6, -8.4), (-9.4, -5.8), (-5.6, -7.4), (1.0, -9.6), (4.8, -14.6)]
    px, py = cx + d * 2.0, base - 20.0
    deg = d * lift * 5.0

    def place(pts):
        return _rot([(cx + d * u, base + v) for (u, v) in pts], px, py, deg)

    regions = []
    for (u, v, rx, ry) in ((1.4, -17.2, 4.4, 3.6), (-3.2, -18.4, 4.0, 3.2), (3.2, -14.2, 2.6, 2.8)):
        c = place([(u, v)])[0]
        regions.append((Ell(c[0], c[1], rx, ry), BROWN_LT))
    cel(draw, Poly(place(local)), WING, sh=(0.8, 1.0), regions=regions)
    for seg in (((-11.0, -10.6), (-6.6, -13.6)), ((-9.6, -8.4), (-4.6, -11.6))):
        stroke(draw, place(list(seg)), 0.6, shade(WING, 1.6))


def _foot(draw, x, y, d, near=True, back=False):
    col = FEET if near else shade(FEET, 0.6)
    if back:
        cel(draw, Ell(x, y + 0.7, 2.2, 1.4), shade(FEET, 0.4), sh=None, lw=0.7)
        return
    if d:
        cel(draw, Limb([(x, y + 0.2), (x - d * 1.9, y + 1.0)], [0.9, 0.6]), col, sh=None, lw=0.6)
        for k in (0.0, 1.0, 2.0):
            tx = x + d * (1.8 + k * 1.2)
            cel(draw, Limb([(x, y), (tx, y + 1.0 - k * 0.2)], [1.0, 0.8]), col, sh=None, lw=0.6)
            Poly([(tx, y + 0.3), (tx + d * 1.4, y + 1.0), (tx, y + 1.6)]).draw(draw, fill=TALON)
        return
    for k in (-1, 0, 1):
        tx = x + k * 2.0
        cel(draw, Limb([(x, y - 0.4), (tx, y + 0.9)], [1.05, 0.9]), col, sh=None, lw=0.6)
        Poly([(tx - 0.6, y + 1.2), (tx + 0.6, y + 1.2), (tx + k * 0.3, y + 2.4)]).draw(draw, fill=TALON)


def _beak_front(draw, hx, hy):
    """Head-on: wide at the cere between the eyes, hooking down past the chin."""
    upper = Poly([(hx - 3.4, hy + 1.8), (hx - 2.2, hy + 0.4), (hx + 2.2, hy + 0.4), (hx + 3.4, hy + 1.8),
                  (hx + 4.2, hy + 4.4), (hx + 3.2, hy + 7.4), (hx + 1.4, hy + 10.4), (hx, hy + 11.8),
                  (hx - 1.4, hy + 10.4), (hx - 3.2, hy + 7.4), (hx - 4.2, hy + 4.4)])
    cel(draw, upper, BEAK, sh=(1.1, 0.7), hi=(0.5, 0.5))
    for s in (-1, 1):
        Ell(hx + s * 1.5, hy + 2.5, 0.55, 0.35).draw(draw, fill=shade(BEAK, 1.5))
    # where the hooked tip closes over the lower beak
    stroke(draw, [(hx - 3.2, hy + 7.0), (hx, hy + 8.9), (hx + 3.2, hy + 7.0)], 0.6, shade(BEAK, 2.0))


def _beak_side(draw, hx, hy, d):
    lower = Poly([(hx + d * 6.6, hy + 3.4), (hx + d * 12.8, hy + 4.0), (hx + d * 12.0, hy + 5.9), (hx + d * 7.0, hy + 6.1)])
    cel(draw, lower, shade(BEAK, 0.7), sh=None)
    upper = Poly([(hx + d * 5.8, hy - 2.6), (hx + d * 10.4, hy - 2.4), (hx + d * 14.2, hy - 0.8),
                  (hx + d * 16.2, hy + 2.2), (hx + d * 15.6, hy + 6.8), (hx + d * 14.0, hy + 4.2),
                  (hx + d * 6.2, hy + 3.8)])
    cel(draw, upper, BEAK, sh=(0.0, 1.0), hi=(0.5, 0.5))
    Ell(hx + d * 9.8, hy + 0.2, 0.7, 0.45).draw(draw, fill=shade(BEAK, 1.6))
    # the line of the mouth under the hook
    stroke(draw, [(hx + d * 6.4, hy + 3.9), (hx + d * 13.8, hy + 4.1)], 0.6, shade(BEAK, 2.0))


def _tail(draw, x, y, d, back=False, sway=0.0):
    """The white tail fan: a wedge behind him in profile, hanging below the
    wing tips from behind."""
    if d:
        local = [(0.0, -2.8), (6.0, -2.4), (11.4, -0.2), (10.2, 1.8), (11.0, 4.0), (8.8, 4.4), (8.6, 6.6),
                 (4.2, 5.6), (0.0, 2.8)]
        pts = _rot([(x - d * u, y + v) for (u, v) in local], x, y, -d * sway * 4.0)
        cel(draw, Poly(pts), WHITE_F, sh=(0.6, 0.9), tone=WHITE_SH, line_color=WHITE_LINE)
        for (a, b) in (((3.0, 0.6), (10.0, 1.9)), ((2.6, 2.6), (8.6, 4.5))):
            stroke(draw, _rot([(x - d * a[0], y + a[1]), (x - d * b[0], y + b[1])], x, y, -d * sway * 4.0), 0.5,
                   WHITE_SH)
        return
    pts = [(x - 2.6, y), (x + 2.6, y), (x + 7.4, y + 8.6), (x + 5.6, y + 10.4), (x + 3.7, y + 8.8), (x + 1.9, y + 10.6),
           (x, y + 9.0), (x - 1.9, y + 10.6), (x - 3.7, y + 8.8), (x - 5.6, y + 10.4), (x - 7.4, y + 8.6)]
    pts = [(px + sway * (py - y) * 0.12, py) for (px, py) in pts]
    cel(draw, Poly(pts), WHITE_F, sh=(1.0, 0.8), tone=WHITE_SH, line_color=WHITE_LINE)
    for k in (-1, 1):
        stroke(draw, [(x + k * 1.2, y + 3.6), (x + k * 3.7 + sway * 0.9, y + 8.6)], 0.5, WHITE_SH)


def draw_raptor(draw, ox, oy, direction, frame):
    bob = [0, -1, 0, -1][frame]
    ph = [0, 1, 0, -1][frame]
    base = oy + 54 + bob
    cx = ox + 32
    d = {DOWN: 0, UP: 0, LEFT: -1, RIGHT: 1}[direction]
    back = direction == UP
    hy = base - 31.0
    if d:
        # the head leads on each step, as a walking bird's does
        hx = cx + d * (2.8 + abs(ph) * 0.7)
        bcx, bcy = cx - d * 1.2, base - 13.2
        _tail(draw, bcx - d * 6.0, base - 11.8, d, sway=ph)
        for side in (-1, 1):
            near = side == 1
            fwd = ph if near else -ph
            fx = cx + d * (0.8 + fwd * 3.0)
            fy = base - 1.4 - (0.8 if fwd < 0 else 0)
            col = FEET if near else shade(FEET, 0.6)
            cel(draw, Limb([(cx + d * (0.4 if near else -0.8), base - 7.0), (fx, fy)], [1.3, 1.1]), col, sh=None, lw=0.7)
            _foot(draw, fx, fy, d, near)
        body = Poly(_rot(arc_pts(bcx, bcy, 9.8, 8.6, 0, 360)[:-1], bcx, bcy, -d * 14))
        cel(draw, body, BROWN, sh=(1.4, 1.2))
        _wing_side(draw, cx, base, d, lift=abs(ph))
        _head(draw, hx, hy, 10.6, 10.0, d)
        _beak_side(draw, hx, hy, d)
        ex, ey = hx + d * 3.4, hy - 0.2
        _eye(draw, ex, ey, look=d, w=4.6, h=5.0)
        _brow(draw, (ex - d * 4.0, ey - 3.3), (ex + d * 4.4, ey - 1.1))
        return
    hx = cx
    bcx, bcy = cx, base - 13.4
    feet = []
    for side in (-1, 1):
        fwd = ph * side
        fx = cx + side * 3.4
        fy = base - 1.4 + (0.4 if fwd > 0 else (-1.2 if fwd < 0 else 0.0))
        feet.append((fx, fy))
        cel(draw, Limb([(cx + side * 3.0, base - 7.0), (fx, fy)], [1.3, 1.1]), FEET, sh=None, lw=0.7)
        _foot(draw, fx, fy, 0, back=back)
    cel(draw, Ell(bcx, bcy, 8.8, 9.6), BROWN, sh=(1.6, 1.2))
    if back:
        _tail(draw, cx, base - 13.0, 0, back=True, sway=ph)
    else:
        # three rows of breast feathers, bold enough to survive the game's size
        for (x, y) in ((-3.2, -0.6), (3.2, -0.6), (0.0, 2.8), (-3.2, 6.0), (3.2, 6.0)):
            stroke(draw, [(bcx + x - 1.7, bcy + y - 0.5), (bcx + x, bcy + y + 0.9), (bcx + x + 1.7, bcy + y - 0.5)],
                   0.8, BROWN_LT)
    for side in (-1, 1):
        # the wings swing a little with the stride, one then the other
        _wing(draw, cx + side * 6.6, base - 21.0, side, swing=1.0 if ph * side < 0 else 0.0, back=back)
    _head(draw, hx, hy, 11.2, 10.2, 0, back)
    if back:
        return
    for side in (-1, 1):
        ex, ey = hx + side * 5.2, hy + 0.4
        _eye(draw, ex, ey)
        _brow(draw, (ex + side * 3.8, ey - 3.1), (ex - side * 3.2, ey - 1.2))
    _beak_front(draw, hx, hy)


def main():
    generate_character("raptor", draw_func=draw_raptor)


if __name__ == "__main__":
    main()
