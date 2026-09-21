#!/usr/bin/env python3
"""Generate sprites/gladiator.png -- the Gladiator.

A Roman arena fighter: a bronze galea whose red horsehair crest is the
silhouette, a muscled cuirass over a red tunic, a skirt of leather strips,
sandals, a big round shield and a gladius. In profile the shield rides on the
far arm behind him and the sword is in front.
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sprite_base import (
    SKIN_TAN, STEEL, GOLD, LEATHER, Ell, Limb, Poly, arc_pts, blade, cel, generate_character,
    hand_at, head_face, head_skull, ink, lit, rig, rig_arms, rig_hair, rig_hand, rig_legs,
    rig_torso, round_shield, shade, star, stroke, arm_pts,
)

BRONZE = (234, 176, 76)
RED = (212, 58, 54)
HAIR = (92, 60, 44)
IRIS = (112, 70, 44)


def _helmet(r, draw):
    hx, hy, rx, ry, d = r.hx, r.head_cy, r.head_rx, r.head_ry, r.d
    # crest first: it stands on the dome, and the dome covers its root
    if d:
        pts = arc_pts(hx - d * 1.6, hy - ry + 0.6, rx + 0.4, 7.4, 180, 360, 24)
        pts = [(x, y) for (x, y) in pts]
        crest = Poly(pts + [(hx + d * rx * 0.7, hy - ry + 1.4), (hx - d * (rx + 1.6), hy - ry + 1.4)])
        cel(draw, crest, RED, sh=(0.0, 1.4))
        for i in range(5):
            x = hx - d * 1.6 + (i - 2) * 3.4
            stroke(draw, [(x, hy - ry - 4.8 + abs(i - 2) * 1.2), (x - d * 0.8, hy - ry + 0.2)], 0.5,
                   shade(RED, 1.2))
    else:
        crest = Ell(hx, hy - ry - 2.0, 4.2, 6.6)
        cel(draw, crest, RED, sh=(1.2, 0.8))
        stroke(draw, [(hx - 1.2, hy - ry - 6.6), (hx - 1.6, hy - ry - 1.0)], 0.7, lit(RED, 1.0))
        stroke(draw, [(hx + 1.4, hy - ry - 6.0), (hx + 1.6, hy - ry + 0.6)], 0.6, shade(RED, 1.3))
    # dome down to the brow, with cheek guards
    brow = hy - 2.6
    dome = arc_pts(hx, hy - 0.4, rx + 1.1, ry + 1.2, 182, 358, 30)
    if d:
        # the cheek guard hangs behind the near eye, in front of the ear
        cheek = [(hx + d * (rx + 0.8), brow + 0.2), (hx - d * 0.4, brow + 0.4), (hx - d * 0.9, hy + 6.4),
                 (hx - d * 4.6, hy + 7.0), (hx - d * (rx + 1.4), hy + 4.0)]
        shape = Poly(dome + (cheek if d > 0 else cheek[::-1]))
    elif r.back:
        shape = Poly(dome + [(hx + rx + 1.2, hy + 7.0), (hx + rx - 1.0, hy + 9.0),
                             (hx - rx + 1.0, hy + 9.0), (hx - rx - 1.2, hy + 7.0)])
    else:
        shape = Poly(dome + [(hx + rx + 1.2, hy + 1.0), (hx + rx - 0.4, hy + 7.4), (hx + rx - 3.2, hy + 7.0),
                             (hx + rx - 3.6, brow + 1.4), (hx - rx + 3.6, brow + 1.4),
                             (hx - rx + 3.2, hy + 7.0), (hx - rx + 0.4, hy + 7.4), (hx - rx - 1.2, hy + 1.0)])
    cel(draw, shape, BRONZE, sh=(1.4, 1.1), hi=(0.8, 0.8))
    # brow band and rivets
    if not r.back:
        y = brow
        x0, x1 = (hx - rx + 3.4, hx + rx - 3.4) if not d else sorted((hx + d * 0.2, hx + d * (rx - 0.6)))
        stroke(draw, [(x0, y), (x1, y)], 1.0, shade(BRONZE, 1.3))
        for x in ((hx - rx + 1.6, hx + rx - 1.6) if not d else (hx - d * 1.6,)):
            Ell(x, hy + 2.0, 0.7, 0.7).draw(draw, fill=lit(BRONZE, 1.4))


def _skirt(r, draw):
    cx, d = r.cx, r.d
    top, bot = r.waist_y + 0.8, r.hip_y + 3.4
    if d:
        pts = [(cx - d * 4.6, top), (cx + d * 4.8, top), (cx + d * 5.8, bot), (cx - d * 5.6, bot)]
    else:
        w = r.hip_w + 0.2
        pts = [(cx - w, top), (cx + w, top), (cx + w + 1.4, bot), (cx - w - 1.4, bot)]
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
    cel(draw, shape, BRONZE, sh=(1.3, 0.8), hi=(0.7, 0.7))
    if not r.back and not d:
        # sculpted chest and abdomen
        stroke(draw, [(cx - 4.2, r.sh_y + 1.8), (cx - 1.0, r.sh_y + 2.8), (cx, r.sh_y + 1.6)], 0.6,
               shade(BRONZE, 1.3))
        stroke(draw, [(cx + 4.2, r.sh_y + 1.8), (cx + 1.0, r.sh_y + 2.8), (cx, r.sh_y + 1.6)], 0.6,
               shade(BRONZE, 1.3))
        stroke(draw, [(cx, r.sh_y + 3.4), (cx, bot - 0.8)], 0.55, shade(BRONZE, 1.3))


def _pauldron(r, draw, side):
    sx, sy = r.shoulder(side)
    k = 1 if not r.d else 0.9
    for i, dy in enumerate((0.0, 1.8)):
        cel(draw, Ell(sx + (side if not r.d else 0) * 0.6, sy - 0.8 + dy, 3.6 * k - i * 0.4, 2.4 - i * 0.3),
            BRONZE if i == 0 else shade(BRONZE, 0.5), sh=(0.6, 0.6))


def _vambrace(r, draw, side, reach=0.0):
    _, e, w, h = arm_pts(r, side, reach)
    px, py = (e[0] + w[0] * 2) / 3.0, (e[1] + w[1] * 2) / 3.0
    cel(draw, Limb([((e[0] + px) / 2, (e[1] + py) / 2), (w[0], w[1] - 0.2)], [2.2, 2.1]),
        BRONZE if r.near(side) else shade(BRONZE, 0.6), sh=(0.5, 0.3))


def _shield(r, draw, side):
    h = hand_at(r, side)
    if r.back:
        round_shield(draw, h[0] + side * 0.6, h[1] - 3.4, 7.2, 7.2, LEATHER, rim=BRONZE, boss=None)
        stroke(draw, [(h[0] - 2.4, h[1] - 3.4), (h[0] + 3.0, h[1] - 3.4)], 1.2, shade(LEATHER, 1.6))
        return
    if r.d:
        x = r.cx - r.d * 3.4
        round_shield(draw, x, r.sh_y + 3.4, 7.4, 7.6, RED, rim=BRONZE, boss=BRONZE)
        return

    def eagle(dr, x, y):
        star(dr, x, y - 0.2, 4.6, lit(RED, 0.6), points=4, inner=0.28)

    round_shield(draw, h[0] + side * 1.8, h[1] - 3.2, 7.4, 7.4, RED, rim=BRONZE, boss=BRONZE, emblem=eagle)


def _sword(r, draw, side):
    h = hand_at(r, side)
    if r.d:
        blade(draw, h[0], h[1], 90 - r.d * 38, length=11.5, width=1.6)
    elif r.back:
        blade(draw, h[0], h[1], 90 + side * 22, length=11.5, width=1.6)
    else:
        blade(draw, h[0], h[1], 90 + side * 30, length=11.5, width=1.6)


def draw_gladiator(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=1.12, head=0.95)
    d = r.d
    shield_side = (1 if not r.back else -1) if not d else -d
    sword_side = -shield_side if not d else d
    if d:
        _shield(r, draw, shield_side)
        rig_arms(r, draw, SKIN_TAN, SKIN_TAN, layer="far")
    rig_legs(r, draw, SKIN_TAN, LEATHER)
    for side in ((-1, 1) if not d else (-d, d)):
        fx, fy = r.foot(side)
        for k in (2.6, 4.6):
            stroke(draw, [(fx - 1.9, fy - k), (fx + 1.9, fy - k - 0.8)], 0.6,
                   LEATHER if r.near(side) else shade(LEATHER, 0.6))
    rig_torso(r, draw, RED)
    _skirt(r, draw)
    _cuirass(r, draw)
    rig_arms(r, draw, SKIN_TAN, SKIN_TAN, layer="near")
    for side in ((-1, 1) if not d else (d,)):
        _vambrace(r, draw, side)
    _pauldron(r, draw, sword_side if not d else d)
    head_skull(r, draw, SKIN_TAN, ears=False)
    if not r.back:
        head_face(r, draw, SKIN_TAN, iris=IRIS, mood="sharp", mouth="smirk", brows=True,
                  brow_color=shade(HAIR, 0.6))
    if not r.back:
        rig_hair(r, draw, HAIR, "crop", hat=True, skin=SKIN_TAN)
    _helmet(r, draw)
    if not d:
        _sword(r, draw, sword_side)
        rig_hand(r, draw, sword_side, SKIN_TAN)
        _shield(r, draw, shield_side)
    else:
        _sword(r, draw, sword_side)
        rig_hand(r, draw, sword_side, SKIN_TAN)


def main():
    generate_character("gladiator", draw_func=draw_gladiator)


if __name__ == "__main__":
    main()
