#!/usr/bin/env python3
"""Generate sprites/wraith.png -- the Wraith.

A spectral assassin: a sharply peaked hood with nothing in it but two glowing
eyes, a cloak that frays into swaying tatters instead of legs, clawed ghost
hands held out in front of it, and two soul flames circling.

It floats: the tatters end three units and more over the ground in every
frame (the whole figure bobs on top of that), so there is daylight between it
and its shadow. From behind, where there is no face to carry it, the hood is
ridged down its seam (a lit plane and a shaded one) and two folds fall down
the cloak; in profile the hem is swept back, as if it were always drifting.
"""

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sprite_base import (
    Ell, Limb, Poly, arm_pts, blob, cel, clipped, face_anchor, generate_character, hood_opening,
    hood_shape, ink, rig, rig_hood, shade, stroke,
)

CLOAK = (56, 132, 138)
WISP = (118, 210, 204)
GLOW = (160, 255, 238)
HANDS = (178, 238, 230)
VOID = (18, 30, 40)

PEAK = 6.0          # how far the hood's point stands off its dome
FLOAT = 9.6         # the cloak's tatters start this far over the ground


def _cloak(r, draw):
    cx, d = r.cx, r.d
    top = r.sh_y - 2.4
    notch = r.base_y - FLOAT
    ph = r.frame * math.pi / 2.0
    fade = [Poly([(cx - 24, notch + 1.2), (cx + 24, notch + 1.2), (cx + 24, notch + 9), (cx - 24, notch + 9)])]
    if d:
        front = cx + d * 5.6
        pts = [(cx - d * 3.4, top), (cx + d * 2.6, top), (cx + d * 4.4, top + 1.8), (front, notch)]
        # swept back: each tatter longer than the one before it and its tip
        # behind its root, the last trailing
        for i, (u, drop, nu, ny) in enumerate(((2.2, 3.0, 3.8, 0.2), (6.4, 4.2, 7.8, 0.7), (10.8, 5.0, 11.6, 1.2),
                                               (15.4, 3.8, 0.0, 0.0))):
            pts.append((front - d * u, notch + drop + math.sin(ph + i) * 0.8))
            if nu:
                pts.append((front - d * nu, notch + ny))
        pts += [(cx - d * 8.8, notch - 2.4), (cx - d * 4.6, top + 1.6)]
    else:
        w = r.sh_w
        xr, xl = cx + w + 6.0, cx - w - 6.0
        pts = [(cx - w + 1.8, top), (cx + w - 1.8, top), (cx + w - 0.3, top + 1.1), (cx + w, r.sh_y + 0.6),
               (xr, notch)]
        drops = (4.6, 3.2, 5.0, 3.2, 4.6)       # torn to different lengths
        n = len(drops)
        for i in range(n):
            x = xr + (xl - xr) * (i + 0.5) / n
            pts.append((x, notch + drops[i] + math.sin(ph + i * 1.3) * 0.9))
            if i < n - 1:
                pts.append((xr + (xl - xr) * (i + 1.0) / n, notch + (0.8 if i % 2 else 0.0)))
        pts += [(xl, notch), (cx - w, r.sh_y + 0.6), (cx - w + 0.3, top + 1.1)]
    shape = Poly(pts)
    under = (Ell(r.hx - d * 1.0, top + 4.6, 10.5, 3.2), shade(CLOAK))      # the hood's shadow on it
    cel(draw, shape, CLOAK, sh=(1.6, 0.8), regions=[under] + [(f, WISP) for f in fade])
    if d:
        return
    if r.back:
        # two folds falling from the shoulders to the tears in the hem
        for s in (-1, 1):
            stroke(draw, [(cx + s * 2.6, r.sh_y + 5.0), (cx + s * 4.9, notch + 0.6)], 0.7, shade(CLOAK, 1.4))
    else:
        # the seam down the front, where the cloak closes
        stroke(draw, [(cx, r.sh_y + 4.0), (cx, notch + 0.4)], 0.7, shade(CLOAK, 1.4))


def _sleeve(r, draw, side, reach, lift):
    """A sleeve widening to its mouth: the claw hangs out of it."""
    s, e, w, _ = arm_pts(r, side, reach, lift)
    col = CLOAK if r.near(side) else shade(CLOAK, 0.6)
    cel(draw, Limb([s, e, (w[0], w[1] + 0.8)], [2.2, 2.5, 3.0], cap=False), col, sh=(0.8, 0.4))


def _claw(r, draw, side, reach, lift):
    """A pale hand of three pointed fingers, as one shape."""
    _, _, _, h = arm_pts(r, side, reach, lift)
    col = HANDS if r.near(side) else shade(HANDS, 0.6)
    lean = r.d * 1.0 if r.d else -side * 0.5       # the fingers curl toward the prey
    parts = [Ell(h[0], h[1], 2.4, 2.1)]
    for k in (-1, 0, 1):
        bx = h[0] + k * 1.5
        parts.append(Poly([(bx - 0.95, h[1] + 0.6), (bx + 0.95, h[1] + 0.6),
                           (bx + k * 0.6 + lean, h[1] + 4.6 - abs(k) * 0.7)]))
    blob(draw, parts, col, sh=(0.5, 0.5), line_color=ink(CLOAK), lw=0.8)


def _soul(draw, x, y, frame):
    flick = [0.0, 0.9, 0.0, -0.9][frame]
    cel(draw, Poly([(x - 2.8, y), (x - 1.6, y - 3.2), (x + flick, y - 6.8), (x + 1.8, y - 3.0),
                    (x + 2.8, y), (x, y + 2.8)]), WISP, sh=(0.8, 0.8), tone=shade(WISP, 0.8))
    Ell(x, y - 0.3, 1.5, 1.7).draw(draw, fill=GLOW)
    Ell(x - 0.3, y - 0.6, 0.7, 0.8).draw(draw, fill=(255, 255, 255))


def _souls(r, draw, near):
    """Two souls circling it at the waist. The half of the circle on the far
    side is drawn before the body and the near half after, so they pass
    behind it."""
    a = r.frame * math.pi / 2.0 + 0.6
    for k in (0, 1):
        ang = a + k * math.pi
        if (math.sin(ang) > 0) == near:
            _soul(draw, r.cx + math.cos(ang) * 15.0, r.base_y - 13.0 + math.sin(ang) * 2.0, r.frame)


def _eyes(r, draw):
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


def _hood_back(r, draw):
    """The back of the hood: ridged down its seam from the point to the nape,
    the plane away from the light a tone darker."""
    hx, hy = r.hx, r.head_cy
    shape = hood_shape(r, PEAK)
    seam = [(hx, hy - 21.0), (hx + 0.5, hy - 10.0), (hx + 0.2, hy), (hx - 0.3, hy + 8.0), (hx, r.sh_y + 3.0)]
    plane = Poly(seam + [(hx + 30, r.sh_y + 3.0), (hx + 30, hy - 21.0)])
    clipped(draw, shape, lambda l: plane.draw(l, fill=shade(CLOAK, 0.75)))
    shape.draw(draw, fill=None, outline=ink(CLOAK), width=1.0)


def draw_wraith(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, bob=[0, -1, -2, -1][frame], step=0)
    d = r.d
    lift = [0.0, 0.6, 1.2, 0.6][frame]
    # both claws held out in front: head-on at the chest, in profile ahead of it
    # (the far one further, so the two show); from behind the body hides them
    reach = {side: (0.7 if not d or r.near(side) else 1.15) for side in (-1, 1)}
    _souls(r, draw, near=False)
    if d:
        _sleeve(r, draw, -d, reach[-d], lift)
        _claw(r, draw, -d, reach[-d], lift)
    elif r.back:
        for side in (-1, 1):
            _sleeve(r, draw, side, reach[side], lift)
    _cloak(r, draw)
    if not r.back:
        for side in ((-1, 1) if not d else (d,)):
            _sleeve(r, draw, side, reach[side], lift)
    if not r.back:
        for side in ((-1, 1) if not d else (d,)):
            _claw(r, draw, side, reach[side], lift)
    rig_hood(r, draw, CLOAK, dark=VOID, peak=PEAK)
    if r.back:
        _hood_back(r, draw)
    else:
        cel(draw, hood_opening(r), VOID, sh=None, line=False)
        _eyes(r, draw)
    _souls(r, draw, near=True)


def main():
    generate_character("wraith", draw_func=draw_wraith)


if __name__ == "__main__":
    main()
