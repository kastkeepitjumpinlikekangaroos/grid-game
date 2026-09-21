#!/usr/bin/env python3
"""Generate sprites/tidecaller.png -- the Tidecaller.

A water priestess: long aqua hair under a wave-crest tiara with a coral
starfish, a deep blue robe trimmed with white surf, a coral trident cradling
a water orb, and bubbles drifting up around her.
"""

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sprite_base import (
    SKIN, GOLD, Ell, Limb, Poly, cel, generate_character, hand_at, lit, rig, rig_arms,
    rig_belt, rig_hair, rig_hand, rig_head, rig_legs, rig_robe, shade, sparkle, star, stroke,
    ink,
)

ROBE = (236, 244, 252)
ROBE_BLUE = (56, 116, 214)
AQUA = (64, 196, 214)
SURF = (240, 250, 255)
HAIR = (40, 138, 196)
CORAL = (255, 126, 112)
PEARL = (240, 232, 212)
ORB = (126, 222, 255)
IRIS = (36, 146, 172)

BUBBLES = (((2.8, -2.0, 1.4), (-2.6, -7.4, 1.0), (1.2, -12.0, 0.8)),
           ((2.4, -4.4, 1.4), (-2.0, -9.6, 1.0), (1.8, -14.0, 0.8)),
           ((1.8, -6.8, 1.4), (-1.2, -11.8, 1.0), (2.6, -2.0, 0.8)),
           ((2.6, -9.0, 1.4), (-2.6, -3.4, 1.0), (1.0, -5.6, 0.8)))


def _bubble(draw, x, y, rad):
    cel(draw, Ell(x, y, rad, rad), (206, 244, 255), sh=None, line_color=shade(AQUA, 1.6), lw=0.6)
    Ell(x - rad * 0.35, y - rad * 0.35, rad * 0.32, rad * 0.32).draw(draw, fill=(255, 255, 255))


def _surf(r, draw):
    """White curls along the hem."""
    cx, d = r.cx, r.d
    y = r.base_y - 3.2
    xs = [cx + i * 2.6 for i in range(-4, 5)] if not d else [cx + i * 2.6 for i in range(-3, 3)]
    for x in xs:
        cel(draw, Ell(x, y, 1.2, 1.0), SURF, sh=None, line_color=ink(ROBE_BLUE), lw=0.5)


def _tiara(r, draw):
    hx, hy, rx, ry, d = r.hx, r.head_cy, r.head_rx, r.head_ry, r.d
    if r.back:
        star(draw, hx + 7.0, hy - 6.0, 2.6, CORAL, line=ink(CORAL))
        return
    x = hx + d * 3.0
    y = hy - ry + 1.2
    # a curling wave crest standing on the crown
    crest = Poly([(x - 5.4, y + 1.6), (x - 3.8, y - 1.4), (x - 1.2, y - 3.6), (x + 1.8, y - 4.2),
                  (x + 4.2, y - 2.8), (x + 3.0, y - 2.2), (x + 1.4, y - 2.4), (x + 0.4, y - 1.0),
                  (x + 2.2, y + 0.2), (x + 5.4, y + 1.6)])
    cel(draw, crest, SURF, sh=(0.6, 0.6), tone=(190, 226, 246))
    gx = x - 0.4
    cel(draw, Ell(gx, y + 0.6, 1.2, 1.2), ORB, sh=None)
    sx = hx + (8.2 if not d else -d * 6.0)
    star(draw, sx, hy - 4.4, 2.6, CORAL, line=ink(CORAL))


def _trident(r, draw, side, out):
    h = hand_at(r, side, 0.0, 0.0, out)
    x = h[0] + 0.2
    top = r.head_cy - 4.0
    cel(draw, Limb([(x, r.base_y - 1.2), (x, top + 2.0)], [1.1, 1.0]), PEARL, sh=(0.5, 0.0))
    # three coral prongs round an orb
    cel(draw, Poly([(x - 4.2, top - 4.6), (x - 3.2, top - 4.6), (x - 2.4, top + 0.6), (x + 2.4, top + 0.6),
                    (x + 3.2, top - 4.6), (x + 4.2, top - 4.6), (x + 3.6, top + 2.4), (x - 3.6, top + 2.4)]),
        CORAL, sh=(0.5, 0.5))
    cel(draw, Poly([(x - 0.7, top - 1.0), (x, top - 7.4), (x + 0.7, top - 1.0)]), CORAL, sh=None)
    cel(draw, Ell(x, top - 2.2, 2.6, 2.6), ORB, sh=(0.8, 0.8), hi=(0.6, 0.6))
    Ell(x - 0.8, top - 3.0, 0.8, 0.8).draw(draw, fill=(255, 255, 255))
    for (bx, by, rad) in BUBBLES[r.frame]:
        _bubble(draw, x + bx * (1 if side > 0 else -1), top + by + 2.0, rad)


def draw_tidecaller(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=0.95)
    d = r.d
    staff_side = (1 if not r.back else -1) if not d else d
    out = 1.6 if not d else 0.4
    rig_hair(r, draw, HAIR, "long", layer="back")
    if r.back:
        _trident(r, draw, staff_side, out)
    if d:
        rig_arms(r, draw, ROBE_BLUE, SKIN, layer="far", cuff=SURF)
    rig_legs(r, draw, ROBE_BLUE, PEARL)
    rig_robe(r, draw, ROBE_BLUE, trim=AQUA, flare=3.2)
    # a white priestess overdress down the front, split over the blue
    if not r.back:
        cx, d = r.cx, r.d
        if d:
            front = Poly([(cx + d * 0.4, r.sh_y - 2.2), (cx + d * 4.2, r.sh_y - 0.6), (cx + d * 6.2, r.base_y - 4.8),
                          (cx + d * 1.0, r.base_y - 4.8)])
        else:
            front = Poly([(cx - 4.6, r.sh_y - 2.2), (cx + 4.6, r.sh_y - 2.2), (cx + 7.4, r.base_y - 4.6),
                          (cx + 1.6, r.base_y - 5.8), (cx, r.base_y - 4.4), (cx - 1.6, r.base_y - 5.8),
                          (cx - 7.4, r.base_y - 4.6)])
        from sprite_base import cel as _cel
        _cel(draw, front, ROBE, sh=(1.2, 0.8), tone=(200, 220, 240))
    _surf(r, draw)
    rig_belt(r, draw, AQUA, buckle=CORAL)
    rig_arms(r, draw, ROBE_BLUE, SKIN, layer="near", cuff=SURF, hands=False)
    rig_head(r, draw, SKIN, hair=HAIR, style="long", eye_color=IRIS, expression="smile")
    _tiara(r, draw)
    if not r.back:
        _trident(r, draw, staff_side, out)
    for side in ((-1, 1) if not d else (d,)):
        rig_hand(r, draw, side, SKIN, out=out if side == staff_side else 0.0)


def main():
    generate_character("tidecaller", draw_func=draw_tidecaller)


if __name__ == "__main__":
    main()
