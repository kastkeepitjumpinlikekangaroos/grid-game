#!/usr/bin/env python3
"""Generate sprites/samurai.png -- the Samurai.

A wandering swordsman under an oversized straw kasa: indigo kimono over a
white collar, a red obi, pleated grey hakama, and a drawn katana held low,
its lacquered scabbard at his hip.
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sprite_base import (
    SKIN, STEEL, Ell, Limb, Poly, RRect, arc_pts, blade, cel, generate_character, hand_at,
    head_face, head_skull, ink, lit, rig, rig_arms, rig_belt, rig_hair, rig_hand, rig_legs,
    rig_robe, rig_torso, shade, stroke, xform,
)

INDIGO = (58, 72, 152)
COLLAR = (242, 242, 246)
OBI = (214, 52, 58)
HAKAMA = (78, 74, 94)
STRAW = (234, 204, 134)
HAIR = (42, 38, 52)
SAYA = (52, 42, 56)
IRIS = (64, 58, 84)


def _kasa(r, draw):
    hx, hy, rx, ry, d = r.hx, r.head_cy, r.head_rx, r.head_ry, r.d
    k = rx / 12.4
    cy = hy - 6.2 * k
    brim_rx = 16.6 * k if not d else 15.4 * k
    cx0 = hx - d * 0.8
    # a shallow cone: its outline from the brim's ends up to the peak
    peak = (cx0, cy - 8.2 * k)
    cone = Poly([(cx0 - brim_rx, cy + 0.4)] + [peak] + [(cx0 + brim_rx, cy + 0.4)] +
                arc_pts(cx0, cy, brim_rx, 3.4 * k, 0, 180, 18)[1:-1])
    cel(draw, cone, STRAW, sh=(1.6, 1.4))
    # woven lines radiating from the peak
    for t in (-0.62, -0.3, 0.0, 0.3, 0.62):
        ex = cx0 + t * brim_rx
        ey = cy + 3.4 * k * (1 - t * t) ** 0.5 * 0.9
        stroke(draw, [(peak[0] + t * 0.8, peak[1] + 1.4), (ex, ey - 0.4)], 0.45, shade(STRAW, 1.2))
    stroke(draw, [(cx0 - brim_rx * 0.72, cy - 1.8), (cx0 + brim_rx * 0.72, cy - 1.8)], 0.45, shade(STRAW, 1.2))


def _sleeve(r, draw, side, reach=0.0):
    """The hanging kimono sleeve under the arm."""
    from sprite_base import arm_pts
    s, e, w, h = arm_pts(r, side, reach)
    col = INDIGO if r.near(side) else shade(INDIGO, 0.6)
    drop = 3.8
    out = side if not r.d else -r.d * 0.5
    pts = [(s[0], s[1] - 0.4), (w[0] + out * 1.4, w[1] - 1.8), (w[0] + out * 2.0, w[1] + drop * 0.4),
           (e[0] + out * 0.4, e[1] + drop), (s[0] - out * 1.4, s[1] + 2.0)]
    cel(draw, Poly(pts), col, sh=(0.8, 0.6))


def _scabbard(r, draw):
    cx, d = r.cx, r.d
    if d:
        pts = [(cx - d * 1.0, r.waist_y + 1.8), (cx - d * 12.0, r.waist_y - 2.6)]
    elif r.back:
        pts = [(cx + 3.0, r.waist_y + 1.8), (cx + 12.0, r.waist_y - 3.4)]
    else:
        pts = [(cx - 4.6, r.waist_y + 2.2), (cx - 13.0, r.waist_y + 6.2)]
    cel(draw, Limb(pts, [1.2, 1.1]), SAYA, sh=None)
    cel(draw, Ell(pts[0][0], pts[0][1], 1.6, 1.6), (224, 176, 70), sh=None)


def draw_samurai(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=1.02)
    d = r.d
    sword_side = (1 if not r.back else -1) if not d else d
    if d:
        _sleeve(r, draw, -d)
        rig_arms(r, draw, INDIGO, SKIN, layer="far")
    if d or r.back:
        _scabbard(r, draw)
    rig_legs(r, draw, HAKAMA, (58, 50, 58))
    rig_torso(r, draw, INDIGO)
    if not r.back:
        cx = r.cx + d * 1.8
        cel(draw, Poly([(cx - 3.2, r.sh_y - 2.2), (cx + 3.2, r.sh_y - 2.2), (cx + 0.4, r.waist_y + 0.4),
                        (cx - 0.4, r.waist_y + 0.4)]), COLLAR, sh=(0.6, 0.0))
        cel(draw, Poly([(cx - 1.6, r.sh_y - 2.2), (cx + 1.6, r.sh_y - 2.2), (cx + 0.2, r.sh_y + 1.8),
                        (cx - 0.2, r.sh_y + 1.8)]), INDIGO, sh=None, line=False)
    # hakama: wide pleated trousers from the obi down
    hk = rig_robe(r, draw, HAKAMA, top=r.waist_y + 0.6, hem=r.base_y - 2.0, flare=2.6, split=True)
    if not r.back and not d:
        for x in (-3.6, 3.6):
            stroke(draw, [(r.cx + x, r.waist_y + 3.4), (r.cx + x * 1.3, r.base_y - 2.8)], 0.5, shade(HAKAMA, 1.3))
    rig_belt(r, draw, OBI, buckle=OBI)
    if not d and not r.back:
        cel(draw, Poly([(r.cx + 4.4, r.waist_y + 2.4), (r.cx + 7.4, r.waist_y + 6.4), (r.cx + 5.4, r.waist_y + 6.8),
                        (r.cx + 3.4, r.waist_y + 3.2)]), OBI, sh=(0.3, 0.3))
        _scabbard(r, draw)
    for side in ((-1, 1) if not d else (d,)):
        _sleeve(r, draw, side)
    rig_arms(r, draw, INDIGO, SKIN, layer="near", hands=False)
    head_skull(r, draw, SKIN)
    if not r.back:
        head_face(r, draw, SKIN, iris=IRIS, mood="calm", mouth="set", brows=True,
                  brow_color=HAIR)
    rig_hair(r, draw, HAIR, "short", hat=True, skin=SKIN)
    _kasa(r, draw)
    h = hand_at(r, sword_side)
    if d:
        blade(draw, h[0], h[1], 90 + d * 52, length=15.0, width=1.35, curve=0.5,
              hilt=(224, 176, 70), grip=(48, 40, 60), guard=2.2, flip=-d)
    else:
        blade(draw, h[0], h[1], 90 + sword_side * 24, length=15.0, width=1.35, curve=0.5,
              hilt=(224, 176, 70), grip=(48, 40, 60), guard=2.2, flip=sword_side)
    for side in ((-1, 1) if not d else (d,)):
        rig_hand(r, draw, side, SKIN)


def main():
    generate_character("samurai", draw_func=draw_samurai)


if __name__ == "__main__":
    main()
