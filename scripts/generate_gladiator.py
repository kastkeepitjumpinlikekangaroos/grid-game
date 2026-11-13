#!/usr/bin/env python3
"""Generate sprites/gladiator.png -- the Gladiator.

A Roman arena fighter: a bronze galea whose red horsehair crest is the
silhouette (a tall plume head-on, a brush from brow to nape in profile, its
tail hanging down the back of the helmet), a muscled cuirass over a red tunic,
a skirt of leather strips, sandals, a big round shield and a gladius held up
at the ready, where its blade is seen against the ground and not his legs.

In profile the shield is up in front of him on the far arm and the sword in
the near hand; the helmet leaves the eye in an opening between its brow and a
cheek guard that follows the jaw (no hair is drawn under it there: a dark lock
beside the eye was a hole in his face).
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sprite_base import (
    SKIN_TAN, STEEL, GOLD, LEATHER, Ell, Limb, Poly, arc_pts, bez, blade, cel, generate_character,
    hand_at, head_face, head_skull, ink, lit, rig, rig_arms, rig_hair, rig_hand, rig_legs,
    rig_torso, round_shield, shade, star, stroke, arm_pts,
)

BRONZE = (234, 176, 76)
PLATE = (208, 142, 60)      # the body's armour, a tone under the helmet's: the helmet is the bright thing
RED = (212, 58, 54)
HAIR = (92, 60, 44)
IRIS = (112, 70, 44)


def _crest(r, draw):
    """The horsehair crest. Drawn before the dome head-on and in profile (the
    dome covers its root) and after it from behind, where its tail hangs down
    the back of the helmet."""
    hx, hy, rx, ry, d = r.hx, r.head_cy, r.head_rx, r.head_ry, r.d
    top = hy - ry
    sway = r.ph * 0.8
    if d:
        # a brush along the ridge, tallest behind the middle, falling to a tail
        pts = [(rx * 0.80, top + 1.8)] + bez((rx * 0.92, top - 1.8), (rx * 0.74, top - 7.6), (rx * 0.1, top - 9.2), 5) + \
            bez((rx * 0.1, top - 9.2), (-rx * 0.86, top - 11.4), (-rx - 2.4, top - 4.8), 6)[1:] + \
            bez((-rx - 2.4, top - 4.8), (-rx - 4.6, top + 1.6), (-rx - 4.0 - sway * 0.5, top + 7.4), 4)[1:] + \
            [(-rx - 3.0 - sway, top + 11.6), (-rx - 1.4 - sway * 0.4, top + 7.6), (-rx - 0.4, top + 2.0)]
        cel(draw, Poly([(hx + d * u, y) for (u, y) in pts]), RED, sh=(0.0, 1.4))
        for (u0, y0, u1, y1) in ((rx * 0.56, -6.0, rx * 0.5, 0.0), (rx * 0.1, -8.0, rx * 0.16, -0.6),
                                 (-rx * 0.42, -8.6, -rx * 0.26, -0.8), (-rx * 0.92, -6.4, -rx * 0.74, 0.4)):
            stroke(draw, [(hx + d * u0, top + y0), (hx + d * u1, top + y1)], 0.6, shade(RED, 1.2))
        return
    plume = [(-2.2, -9.6), (0.0, -10.6), (2.2, -9.6), (4.6, -6.6), (3.8, -2.4)]
    if r.back:
        # seen from behind: the brush end on, and its tail down the helmet's back
        tail = [(3.0, 3.0), (2.4 + sway, 9.6), (0.2 + sway, 14.6), (-2.2 + sway * 0.5, 9.6), (-3.0, 3.0)]
        pts = plume + tail + [(-3.8, -2.4), (-4.6, -6.6)]
    else:
        pts = plume + [(2.2, 1.4), (-2.2, 1.4), (-3.8, -2.4), (-4.6, -6.6)]
    cel(draw, Poly([(hx + u, top + v) for (u, v) in pts]), RED, sh=(1.2, 0.8))
    stroke(draw, [(hx - 1.4, top - 8.6), (hx - 1.7, top - 1.6)], 0.7, lit(RED, 1.0))
    stroke(draw, [(hx + 1.6, top - 8.2), (hx + 1.7, top + (7.0 if r.back else 0.2))], 0.6, shade(RED, 1.3))


def _dome(r, draw):
    """The bronze bowl, down to the brow, with its cheek guards."""
    hx, hy, rx, ry, d = r.hx, r.head_cy, r.head_rx, r.head_ry, r.d
    brow = hy - 2.6
    dome = arc_pts(hx, hy - 0.4, rx + 1.1, ry + 1.2, 182, 358, 30)
    band = shade(BRONZE, 1.3)
    if d:
        # the brow, then a cheek guard hinged behind the eye that follows the
        # jaw forward under it, then the neck guard flicking out at the nape
        low = [(rx + 0.8, brow + 0.2), (-1.6, brow + 0.6), (-2.2, hy + 3.2), (0.6, hy + 5.6), (2.6, hy + 8.0),
               (1.4, hy + 9.6), (-2.6, hy + 9.4), (-5.2, hy + 7.4), (-rx * 0.75, hy + 7.0), (-rx - 2.8, hy + 8.2),
               (-rx - 2.2, hy + 5.4), (-rx - 1.2, hy + 2.0)]
        low = [(hx + d * u, y) for (u, y) in low]
        shape = Poly(dome + (low if d > 0 else low[::-1]))
        cel(draw, shape, BRONZE, sh=(1.4, 1.1), hi=(0.8, 0.8))
        stroke(draw, [(hx - d * 1.0, brow), (hx + d * (rx + 0.2), brow - 0.2)], 1.0, band)
        stroke(draw, [(hx - d * (rx + 0.6), hy + 4.4), (hx - d * 6.0, hy + 5.6)], 0.8, band)
        Ell(hx - d * 3.6, hy + 1.6, 0.8, 0.8).draw(draw, fill=lit(BRONZE, 1.4))
        return
    if r.back:
        # the bowl, flaring into a neck guard
        shape = Poly(dome + [(hx + rx + 1.3, hy + 4.0), (hx + rx + 3.0, hy + 7.6), (hx + rx + 2.2, hy + 9.4)] +
                     bez((hx + rx + 2.2, hy + 9.4), (hx, hy + 11.4), (hx - rx - 2.2, hy + 9.4), 8)[1:] +
                     [(hx - rx - 3.0, hy + 7.6), (hx - rx - 1.3, hy + 4.0)])
        cel(draw, shape, BRONZE, sh=(1.4, 1.1), hi=(0.8, 0.8))
        stroke(draw, bez((hx - rx - 1.6, hy + 5.6), (hx, hy + 8.2), (hx + rx + 1.6, hy + 5.6), 10), 0.9, band)
        for s in (-1, 1):
            Ell(hx + s * (rx - 1.8), hy + 3.0, 0.8, 0.8).draw(draw, fill=lit(BRONZE, 1.4))
        return
    shape = Poly(dome + [(hx + rx + 1.2, hy + 1.0), (hx + rx - 0.4, hy + 7.4), (hx + rx - 3.2, hy + 7.0),
                         (hx + rx - 3.6, brow + 1.4), (hx - rx + 3.6, brow + 1.4),
                         (hx - rx + 3.2, hy + 7.0), (hx - rx + 0.4, hy + 7.4), (hx - rx - 1.2, hy + 1.0)])
    cel(draw, shape, BRONZE, sh=(1.4, 1.1), hi=(0.8, 0.8))
    stroke(draw, [(hx - rx + 3.4, brow), (hx + rx - 3.4, brow)], 1.0, band)
    for x in (hx - rx + 1.6, hx + rx - 1.6):
        Ell(x, hy + 2.0, 0.7, 0.7).draw(draw, fill=lit(BRONZE, 1.4))


def _skirt(r, draw):
    cx, d = r.cx, r.d
    top, bot = r.waist_y + 0.8, r.hip_y + 3.4
    if d:
        pts = [(cx - d * 4.6, top), (cx + d * 4.8, top), (cx + d * 5.8, bot), (cx - d * 5.6, bot)]
    else:
        w = r.hip_w + 0.2
        pts = [(cx - w, top), (cx + w, top), (cx + w + 1.4, bot), (cx - w - 1.4, bot)]
    # the red tunic's hem showing under the leather
    cel(draw, Poly([pts[0], pts[1], (pts[2][0], bot + 1.5), (pts[3][0], bot + 1.5)]), RED, sh=(1.0, 0.0))
    shape = Poly(pts)
    cel(draw, shape, LEATHER, sh=(1.0, 0.6))
    n = 5 if not d else 4
    x0, x1 = pts[3][0], pts[2][0]
    for i in range(1, n):
        t = i / float(n)
        xt = pts[0][0] + (pts[1][0] - pts[0][0]) * t
        xb = x0 + (x1 - x0) * t
        stroke(draw, [(xt, top + 0.6), (xb, bot - 0.4)], 0.6, shade(LEATHER, 1.6))
    for i in range(n):
        t = (i + 0.5) / float(n)
        xt = pts[0][0] + (pts[1][0] - pts[0][0]) * t
        Ell(xt, top + 1.2, 0.6, 0.6).draw(draw, fill=GOLD)


def _cuirass(r, draw):
    cx, d = r.cx, r.d
    top, bot = r.sh_y - 2.0, r.waist_y + 1.2
    if d:
        shape = Poly([(cx - d * 3.2, top), (cx + d * 2.8, top), (cx + d * 4.6, top + 2.0),
                      (cx + d * 4.4, bot), (cx - d * 4.2, bot), (cx - d * 4.6, top + 1.6)])
    else:
        w = r.sh_w - 0.4
        shape = Poly([(cx - w + 1.6, top), (cx + w - 1.6, top), (cx + w, top + 1.4), (cx + w - 0.6, bot),
                      (cx - w + 0.6, bot), (cx - w, top + 1.4)])
    cel(draw, shape, PLATE, sh=(1.3, 0.8), hi=(0.7, 0.7))
    line = shade(PLATE, 1.3)
    if r.back:
        # the back plate: a spine and the shoulder blades
        stroke(draw, [(cx, r.sh_y + 0.6), (cx, bot - 0.8)], 0.6, line)
        for s in (-1, 1):
            stroke(draw, [(cx + s * 4.4, r.sh_y + 1.2), (cx + s * 1.6, r.sh_y + 2.6)], 0.6, line)
    elif not d:
        # sculpted chest and abdomen
        stroke(draw, [(cx - 4.2, r.sh_y + 1.8), (cx - 1.0, r.sh_y + 2.8), (cx, r.sh_y + 1.6)], 0.6, line)
        stroke(draw, [(cx + 4.2, r.sh_y + 1.8), (cx + 1.0, r.sh_y + 2.8), (cx, r.sh_y + 1.6)], 0.6, line)
        stroke(draw, [(cx, r.sh_y + 3.4), (cx, bot - 0.8)], 0.55, line)


def _pauldron(r, draw, side):
    sx, sy = r.shoulder(side)
    k = 1 if not r.d else 0.9
    for i, dy in enumerate((0.0, 1.8)):
        cel(draw, Ell(sx + (side if not r.d else 0) * 0.6, sy - 0.8 + dy, 3.6 * k - i * 0.4, 2.4 - i * 0.3),
            PLATE if i == 0 else shade(PLATE, 0.5), sh=(0.6, 0.6))


def _vambrace(r, draw, side, reach=0.0):
    _, e, w, h = arm_pts(r, side, reach)
    px, py = (e[0] + w[0] * 2) / 3.0, (e[1] + w[1] * 2) / 3.0
    cel(draw, Limb([((e[0] + px) / 2, (e[1] + py) / 2), (w[0], w[1] - 0.2)], [2.2, 2.1]),
        PLATE if r.near(side) else shade(PLATE, 0.6), sh=(0.5, 0.3))


def _shield(r, draw, side):
    h = hand_at(r, side)
    if r.back:
        # its leather back, held out in front of him: the arm goes over it
        round_shield(draw, h[0] + side * 0.6, h[1] - 3.4, 7.2, 7.2, LEATHER, rim=BRONZE, boss=None)
        return
    if r.d:
        # up in front of him on the far arm, turned three quarters to the camera
        round_shield(draw, r.cx + r.d * 6.2, r.sh_y + 5.4, 4.6, 7.4, RED, rim=BRONZE, boss=BRONZE)
        return

    def eagle(dr, x, y):
        star(dr, x, y - 0.2, 4.6, lit(RED, 0.6), points=4, inner=0.28)

    round_shield(draw, h[0] + side * 1.8, h[1] - 3.2, 7.4, 7.4, RED, rim=BRONZE, boss=BRONZE, emblem=eagle)


def _sword(r, draw, side, reach=0.0):
    """The gladius, held blade up at the ready: out to his side head-on, ahead
    of him in profile."""
    h = hand_at(r, side, reach)
    ang = -90 + side * 35 if not r.d else -90 + r.d * 58
    blade(draw, h[0], h[1], ang, length=12.5, width=1.9)
    rig_hand(r, draw, side, SKIN_TAN, reach)


def draw_gladiator(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=1.12, head=0.95)
    rs = rig(ox, oy, direction, frame, build=1.12, head=0.95, step=0)     # the arms that carry do not swing
    d = r.d
    shield_side = (1 if not r.back else -1) if not d else -d
    sword_side = -shield_side if not d else d
    if d:
        rig_arms(rs, draw, SKIN_TAN, SKIN_TAN, layer="far", reach=0.6)       # behind the shield it holds
        _shield(r, draw, shield_side)
    elif r.back:
        _shield(r, draw, shield_side)
    rig_legs(r, draw, SKIN_TAN, LEATHER)
    for side in ((-1, 1) if not d else (-d, d)):
        fx, fy = r.foot(side)
        for k in (2.6, 4.6):
            stroke(draw, [(fx - 1.9, fy - k), (fx + 1.9, fy - k - 0.8)], 0.6,
                   LEATHER if r.near(side) else shade(LEATHER, 0.6))
    rig_torso(r, draw, RED)
    _skirt(r, draw)
    _cuirass(r, draw)
    reach = 0.35 if d else 0.0
    if d:
        rig_arms(rs, draw, SKIN_TAN, SKIN_TAN, layer="near", reach=reach, hands=False)
        _vambrace(rs, draw, d, reach)
    else:
        rig_arms(r, draw, SKIN_TAN, SKIN_TAN, layer="near")
        for side in (-1, 1):
            _vambrace(r, draw, side)
    _pauldron(r, draw, sword_side if not d else d)
    if not r.back:
        _crest(r, draw)
    head_skull(r, draw, SKIN_TAN, ears=False)
    if not r.back:
        head_face(r, draw, SKIN_TAN, iris=IRIS, mood="sharp", mouth="smirk", brows=True,
                  brow_color=shade(HAIR, 0.6), blush=not d)
        if not d:
            rig_hair(r, draw, HAIR, "crop", hat=True, skin=SKIN_TAN)
    _dome(r, draw)
    if r.back:
        _crest(r, draw)
    _sword(rs if d else r, draw, sword_side, reach)
    if not d and not r.back:
        _shield(r, draw, shield_side)


def main():
    generate_character("gladiator", draw_func=draw_gladiator)


if __name__ == "__main__":
    main()
