#!/usr/bin/env python3
"""Generate sprites/wraith.png -- the Wraith.

A spectral assassin: a peaked hood with nothing in it but two glowing eyes, a
cloak that frays into swaying tatters instead of legs (it floats), clawed
ghost hands, and two soul flames circling it.
"""

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sprite_base import (
    Ell, Limb, Poly, arm_pts, cel, generate_character, lit, mix, rig, rig_arms, rig_hood,
    shade, stroke,
)

CLOAK = (56, 132, 138)
WISP = (118, 210, 204)
GLOW = (160, 255, 238)
HANDS = (178, 238, 230)
VOID = (18, 30, 40)


def _cloak(r, draw):
    cx, d = r.cx, r.d
    top = r.sh_y - 2.4
    hem = r.base_y - 1.6
    notch = r.base_y - 6.4
    ph = r.frame * math.pi / 2.0
    if d:
        front, back = cx + d * 6.0, cx - d * 9.6
        pts = [(cx - d * 3.4, top), (cx + d * 2.6, top), (cx + d * 4.4, top + 1.8), (front, notch)]
        n = 4
        for i in range(n):
            t = (i + 0.5) / n
            x = front + (back - front) * t - d * 1.2 * t
            pts.append((x - d * 0.4, hem + math.sin(ph + i) * 0.8 + t * 0.6))
            if i < n - 1:
                pts.append((front + (back - front) * (i + 1.0) / n, notch + 0.6))
        pts += [(back, notch - 1.0), (cx - d * 4.6, top + 1.6)]
    else:
        w = r.sh_w
        xr, xl = cx + w + 6.4, cx - w - 6.4
        pts = [(cx - w + 1.8, top), (cx + w - 1.8, top), (cx + w - 0.3, top + 1.1), (cx + w, r.sh_y + 0.6),
               (xr, notch)]
        n = 5
        for i in range(n):
            x = xr + (xl - xr) * (i + 0.5) / n
            pts.append((x, hem + math.sin(ph + i * 1.3) * 0.9))
            if i < n - 1:
                pts.append((xr + (xl - xr) * (i + 1.0) / n, notch + (0.8 if i % 2 else 0.0)))
        pts += [(xl, notch), (cx - w, r.sh_y + 0.6), (cx - w + 0.3, top + 1.1)]
    shape = Poly(pts)
    fade = Poly([(cx - 20, notch + 1.2), (cx + 20, notch + 1.2), (cx + 20, hem + 3), (cx - 20, hem + 3)])
    cel(draw, shape, CLOAK, sh=(1.6, 0.8), regions=[(fade, WISP)])
    # a seam down the front, where the cloak closes
    if not r.back:
        x = cx + d * 2.0
        stroke(draw, [(x, r.sh_y + 0.4), (x + d * 0.6, notch)], 0.7, shade(CLOAK, 1.4))


def _claws(r, draw, side, lift):
    _, _, w, h = arm_pts(r, side, 0.0, lift)
    col = HANDS if r.near(side) else shade(HANDS, 0.6)
    cel(draw, Ell(h[0], h[1], 2.0, 1.8), col, sh=(0.5, 0.5))
    for k in (-1, 0, 1):
        x = h[0] + k * 1.2 + (r.d * 0.6 if r.d else 0)
        stroke(draw, [(x, h[1] + 0.8), (x + k * 0.4 + r.d * 0.4, h[1] + 3.0)], 0.7, col)


def _soul(draw, x, y, frame):
    flick = [0.0, 0.9, 0.0, -0.9][frame]
    cel(draw, Poly([(x - 2.8, y), (x - 1.6, y - 3.2), (x + flick, y - 6.8), (x + 1.8, y - 3.0),
                    (x + 2.8, y), (x, y + 2.8)]), WISP, sh=(0.8, 0.8), tone=shade(WISP, 0.8))
    Ell(x, y - 0.3, 1.5, 1.7).draw(draw, fill=GLOW)
    Ell(x - 0.3, y - 0.6, 0.7, 0.8).draw(draw, fill=(255, 255, 255))


def _eyes(r, draw):
    from sprite_base import face_anchor
    e1, e2, ey, _, _ = face_anchor(r)
    d = r.d
    pairs = ((-1, e1), (1, e2)) if not d else ((-d, e1), (d, e2))
    for side, ex in pairs:
        w = 1.0 if (not d or side == -d) else 0.7
        x = ex + d * 0.6
        # slanted almonds, inner corners low: a glare, not a stare
        Poly([(x - side * 2.2 * w, ey + 0.2), (x - side * 0.4 * w, ey - 1.6), (x + side * 2.0 * w, ey - 1.9),
              (x + side * 1.4 * w, ey + 0.9), (x - side * 0.8 * w, ey + 1.4)]).draw(draw, fill=GLOW)
        Ell(x + side * 0.2, ey - 0.3, 0.8 * w, 0.7).draw(draw, fill=(255, 255, 255))


def draw_wraith(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, bob=[0, -1, -2, -1][frame], step=0)
    d = r.d
    lift = [0.0, 0.6, 1.2, 0.6][frame]
    if d:
        rig_arms(r, draw, CLOAK, HANDS, layer="far", hands=False)
        _claws(r, draw, -d, lift)
    _cloak(r, draw)
    rig_arms(r, draw, CLOAK, HANDS, layer="near", hands=False)
    for side in ((-1, 1) if not d else (d,)):
        _claws(r, draw, side, lift)
    rig_hood(r, draw, CLOAK, dark=VOID, peak=3.8)
    if not r.back:
        from sprite_base import hood_opening
        cel(draw, hood_opening(r), VOID, sh=None, line=False)
        _eyes(r, draw)
    # two souls circling low around it
    a = frame * math.pi / 2.0 + 0.6
    for k in (0, 1):
        ang = a + k * math.pi
        x = r.cx + math.cos(ang) * 15.0
        y = r.hip_y + math.sin(ang) * 2.0
        _soul(draw, x, y, frame)


def main():
    generate_character("wraith", draw_func=draw_wraith)


if __name__ == "__main__":
    main()
