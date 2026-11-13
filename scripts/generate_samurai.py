#!/usr/bin/env python3
"""Generate sprites/samurai.png -- the Samurai.

A wandering swordsman under an oversized straw kasa: indigo kimono over a
white collar with his crest on its back, a red obi, pleated grey hakama over
white tabi, and a drawn katana held low at his right side, its lacquered
scabbard on his left hip.
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sprite_base import (
    SKIN, STEEL, Ell, Limb, Poly, RRect, arc_pts, arm_pts, bez, blade, blob, cel,
    generate_character, hand_at, head_face, head_skull, ink, lit, rig, rig_arms, rig_belt,
    rig_hair, rig_hand, rig_legs, rig_torso, shade, stroke, xform,
)

INDIGO = (58, 72, 152)
COLLAR = (242, 242, 246)
OBI = (214, 52, 58)
HAKAMA = (78, 74, 94)
STRAW = (234, 204, 134)
HAIR = (42, 38, 52)
SAYA = (52, 42, 56)
IRIS = (64, 58, 84)
TABI = (240, 238, 232)
TSUBA = (224, 176, 70)


def _kasa(r, draw):
    """The straw hat: a tall cone, wider than his shoulders."""
    hx, hy, rx, ry, d = r.hx, r.head_cy, r.head_rx, r.head_ry, r.d
    k = rx / 12.4
    cy = hy - 6.2 * k
    brim_rx = 16.6 * k if not d else 15.4 * k
    cx0 = hx - d * 0.8
    peak = (cx0, cy - 12.4 * k)
    # the cone's sides bow out a little on their way up to the peak
    left = bez((cx0 - brim_rx, cy + 0.4), (cx0 - brim_rx * 0.5, cy - 7.4 * k), peak, 6)
    right = bez(peak, (cx0 + brim_rx * 0.5, cy - 7.4 * k), (cx0 + brim_rx, cy + 0.4), 6)
    cone = Poly(left + right[1:] + arc_pts(cx0, cy, brim_rx, 3.4 * k, 0, 180, 18)[1:-1])
    cel(draw, cone, STRAW, sh=(1.8, 1.2))
    # woven lines radiating from the peak, and one ring of binding
    for t in (-0.62, -0.3, 0.0, 0.3, 0.62):
        ex = cx0 + t * brim_rx
        ey = cy + 3.4 * k * (1 - t * t) ** 0.5 * 0.9
        stroke(draw, [(peak[0] + t * 0.8, peak[1] + 1.8), (ex, ey - 0.4)], 0.5, shade(STRAW, 1.2))
    stroke(draw, [(cx0 - brim_rx * 0.6, cy - 2.6), (cx0, cy - 1.6), (cx0 + brim_rx * 0.6, cy - 2.6)], 0.5,
           shade(STRAW, 1.2))
    cel(draw, Ell(peak[0], peak[1] + 0.9, 1.5, 1.1), shade(STRAW, 1.3), sh=None, lw=0.6)


def _sleeve(r, draw, side, reach=0.0):
    """The hanging kimono sleeve under the arm."""
    s, e, w, h = arm_pts(r, side, reach)
    col = INDIGO if r.near(side) else shade(INDIGO, 0.6)
    drop = 3.8
    out = side if not r.d else -r.d * 0.5
    pts = [(s[0], s[1] - 0.4), (w[0] + out * 1.4, w[1] - 1.8), (w[0] + out * 2.0, w[1] + drop * 0.4),
           (e[0] + out * 0.4, e[1] + drop), (s[0] - out * 1.4, s[1] + 2.0)]
    cel(draw, Poly(pts), col, sh=(0.8, 0.6))


def _scabbard(r, draw):
    """Through the obi on his left hip, its end out behind him."""
    cx, d = r.cx, r.d
    if d:
        pts = [(cx - d * 1.0, r.waist_y + 1.8), (cx - d * 12.0, r.waist_y - 2.6)]
    elif r.back:
        pts = [(cx - 3.0, r.waist_y + 1.8), (cx - 12.0, r.waist_y - 3.4)]
    else:
        pts = [(cx + 4.6, r.waist_y + 2.2), (cx + 13.0, r.waist_y + 6.2)]
    cel(draw, Limb(pts, [1.3, 1.2]), SAYA, sh=None)
    cel(draw, Ell(pts[0][0], pts[0][1], 1.6, 1.6), TSUBA, sh=None)


def _hakama(r, draw):
    """Wide pleated trousers from the obi down, a leg to each foot: the hem of
    each stops over its own instep, so the stride shows under them."""
    cx, d = r.cx, r.d
    top = r.waist_y + 0.6
    crease = shade(HAKAMA, 1.5)
    if d:
        for side in (-d, d):                      # the far leg first
            fx, fy = r.foot(side)
            near = r.near(side)
            hem = fy - 1.0
            leg = Poly([(cx - d * 4.4, top), (cx + d * 4.7, top), (fx + d * 5.0, hem), (fx - d * 5.8, hem)])
            cel(draw, leg, HAKAMA if near else shade(HAKAMA, 0.8), sh=(1.4, 0.0))
            if near:
                stroke(draw, [(cx + d * 1.2, top + 2.6), (fx + d * 1.6, hem - 0.9)], 0.6, crease)
        return
    legs, hems = [], {}
    for side in (-1, 1):
        fx, fy = r.foot(side)
        hems[side] = fy - 1.0
        legs.append(Poly([(cx - side * 0.8, top), (cx + side * (r.hip_w + 0.2), top),
                          (cx + side * (r.hip_w + 2.8), hems[side]), (cx - side * 0.4, hems[side])]))
    low = max(hems.values())
    split = Poly([(cx - 0.5, top + 2.4), (cx + 0.5, top + 2.4), (cx + 1.5, low + 1.0), (cx - 1.5, low + 1.0)])
    blob(draw, legs, HAKAMA, sh=(1.6, 0.0), regions=[(split, shade(HAKAMA, 1.8))])
    for side in (-1, 1):
        stroke(draw, [(cx + side * 3.7, top + 2.8), (cx + side * 4.9, hems[side] - 0.9)], 0.6, crease)


def _crest(r, draw):
    """His mon, white on the back of the kimono."""
    x, y = r.cx, r.sh_y + 3.7
    cel(draw, Ell(x, y, 2.4, 2.4), COLLAR, sh=None, lw=0.6)
    Ell(x, y, 1.15, 1.15).draw(draw, fill=INDIGO)


def draw_samurai(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=1.02)
    d = r.d
    # right-handed: the katana in his right hand, the scabbard on his left hip
    sword_side = (-1 if not r.back else 1) if not d else d
    if d:
        _sleeve(r, draw, -d)
        rig_arms(r, draw, INDIGO, SKIN, layer="far")
    if d or r.back:
        _scabbard(r, draw)
    rig_legs(r, draw, HAKAMA, TABI)
    rig_torso(r, draw, INDIGO)
    if not r.back:
        cx = r.cx + d * 1.8
        cel(draw, Poly([(cx - 3.2, r.sh_y - 2.2), (cx + 3.2, r.sh_y - 2.2), (cx + 0.4, r.waist_y + 0.4),
                        (cx - 0.4, r.waist_y + 0.4)]), COLLAR, sh=(0.6, 0.0))
        cel(draw, Poly([(cx - 1.6, r.sh_y - 2.2), (cx + 1.6, r.sh_y - 2.2), (cx + 0.2, r.sh_y + 1.8),
                        (cx - 0.2, r.sh_y + 1.8)]), INDIGO, sh=None, line=False)
    else:
        _crest(r, draw)
    _hakama(r, draw)
    rig_belt(r, draw, OBI, buckle=OBI)
    if not d and not r.back:
        # the obi's end, hanging from its knot
        cel(draw, Poly([(r.cx - 1.4, r.waist_y + 2.6), (r.cx + 0.6, r.waist_y + 2.6), (r.cx - 1.2, r.waist_y + 7.2),
                        (r.cx - 3.4, r.waist_y + 6.6)]), OBI, sh=(0.3, 0.3))
        _scabbard(r, draw)
    for side in ((-1, 1) if not d else (d,)):
        _sleeve(r, draw, side)
    rig_arms(r, draw, INDIGO, SKIN, layer="near", hands=False)
    head_skull(r, draw, SKIN)
    if not r.back:
        head_face(r, draw, SKIN, iris=IRIS, mood="calm", mouth="set", brows=True,
                  brow_color=HAIR, tilt=0.45)
    rig_hair(r, draw, HAIR, "short", hat=True, skin=SKIN)
    _kasa(r, draw)
    h = hand_at(r, sword_side)
    if d:
        # trailed low behind him, edge up
        blade(draw, h[0], h[1], 90 + d * 52, length=15.0, width=1.35, curve=0.5,
              hilt=TSUBA, grip=(48, 40, 60), guard=2.2, flip=-d)
    else:
        # held low and out to his side, the tip just off the ground
        blade(draw, h[0], h[1], 90 - sword_side * 46, length=14.6, width=1.35, curve=0.5,
              hilt=TSUBA, grip=(48, 40, 60), guard=2.2, flip=-sword_side)
    for side in ((-1, 1) if not d else (d,)):
        rig_hand(r, draw, side, SKIN)


def main():
    generate_character("samurai", draw_func=draw_samurai)


if __name__ == "__main__":
    main()
