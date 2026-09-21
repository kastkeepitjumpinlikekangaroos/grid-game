#!/usr/bin/env python3
"""Generate sprites/soldier.png -- the Soldier.

A cute army grunt: a round olive helmet with goggles strapped to it, a
tactical vest with chest pouches, dog tags, big brown boots, and a chunky
rifle held at the ready (slung across his back from behind).
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sprite_base import (
    SKIN, Ell, Limb, Poly, RRect, arc_pts, cel, generate_character, hand_at, head_face,
    head_skull, ink, lit, rig, rig_arms, rig_belt, rig_hair, rig_hand, rig_legs, rig_torso,
    shade, stroke, xform,
)

OLIVE = (118, 140, 76)
VEST = (90, 104, 62)
KHAKI = (210, 188, 134)
BOOT = (98, 72, 52)
GUN = (78, 82, 94)
WOOD = (154, 102, 60)
HAIR = (116, 78, 52)
LENS = (140, 214, 236)
IRIS = (92, 76, 58)


def _helmet(r, draw):
    hx, hy, rx, ry, d = r.hx, r.head_cy, r.head_rx, r.head_ry, r.d
    cx0 = hx - d * 0.6
    dome = arc_pts(cx0, hy - 1.2, rx + 1.6, ry + 0.8, 180, 360, 30)
    lip = [(cx0 + rx + 2.4, hy - 0.4), (cx0 - rx - 2.4, hy - 0.4)]
    cel(draw, Poly(dome + lip), OLIVE, sh=(1.5, 1.0), hi=(0.9, 0.9))
    # the rim, a band of shadow under the dome
    cel(draw, RRect(cx0 - rx - 2.6, hy - 2.0, cx0 + rx + 2.6, hy + 0.2, 1.0), shade(OLIVE, 1.0), sh=None)
    if not r.back:
        # goggles pushed up onto the helmet
        gx = cx0 + d * 3.6
        stroke(draw, [(cx0 - rx - 0.6, hy - 4.2), (cx0 + rx + 0.6, hy - 4.2)] if not d else
               [(cx0 - d * (rx + 0.8), hy - 4.0), (cx0 + d * (rx + 0.4), hy - 4.6)], 1.0, shade(OLIVE, 2.0))
        for k in ((-2.4, 2.4) if not d else (0.6,)):
            lx = gx + k * (1 if not d else d)
            cel(draw, Ell(lx, hy - 4.6, 2.3, 2.0), shade(GUN, 0.4), sh=None)
            cel(draw, Ell(lx, hy - 4.6, 1.5, 1.3), LENS, sh=None, line=False)
            Ell(lx - 0.5, hy - 5.1, 0.5, 0.5).draw(draw, fill=(255, 255, 255))
        # chin strap
        for s in ((-1, 1) if not d else (-d,)):
            x = cx0 + s * (rx - 0.6) if not d else hx - d * 1.4
            stroke(draw, [(x, hy - 0.2), (x + (s * -1.2 if not d else d * 0.6), hy + 6.6)], 0.6, shade(OLIVE, 2.2))


def _rifle(draw, x, y, ang, flip=1.0):
    """A chunky rifle gripped at (x, y), pointing along `ang`."""
    stock = [(-7.0, -1.2), (-1.6, -1.6), (-1.0, 1.4), (-6.2, 2.4)]
    cel(draw, Poly(xform(stock, x, y, ang, 1.0, flip)), WOOD, sh=None)
    body = [(-1.8, -1.9), (4.6, -1.9), (4.6, 1.2), (-1.8, 1.2)]
    cel(draw, Poly(xform(body, x, y, ang, 1.0, flip)), GUN, sh=None,
        regions=[(Poly(xform([(-1.8, 0.0), (4.6, 0.0), (4.6, 1.2), (-1.8, 1.2)], x, y, ang, 1.0, flip)),
                  shade(GUN, 0.8))])
    mag = [(0.8, 1.0), (2.8, 1.0), (3.4, 4.2), (1.4, 4.4)]
    cel(draw, Poly(xform(mag, x, y, ang, 1.0, flip)), shade(GUN, 0.8), sh=None)
    guard = [(4.4, -1.5), (9.6, -1.3), (9.6, 1.1), (4.4, 1.2)]
    cel(draw, Poly(xform(guard, x, y, ang, 1.0, flip)), WOOD, sh=None)
    barrel = xform([(9.4, -0.4), (13.6, -0.4)], x, y, ang, 1.0, flip)
    cel(draw, Limb(barrel, [0.75, 0.75]), GUN, sh=None)
    sight = [(1.0, -1.8), (2.6, -1.8), (2.4, -3.0), (1.2, -3.0)]
    cel(draw, Poly(xform(sight, x, y, ang, 1.0, flip)), GUN, sh=None)


def draw_soldier(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=1.05)
    d = r.d
    if r.back:
        pass
    if d:
        rig_arms(r, draw, OLIVE, SKIN, layer="far")
    rig_legs(r, draw, OLIVE, BOOT)
    rig_torso(r, draw, OLIVE)
    # tactical vest over the shirt
    cx = r.cx
    if d:
        vest = Poly([(cx - d * 3.0, r.sh_y - 1.4), (cx + d * 3.4, r.sh_y - 1.0), (cx + d * 4.6, r.waist_y + 1.0),
                     (cx - d * 4.2, r.waist_y + 1.0)])
    else:
        w = r.sh_w - 0.6
        vest = Poly([(cx - w + 1.2, r.sh_y - 1.8), (cx - 2.2, r.sh_y - 1.8), (cx, r.sh_y + 1.0),
                     (cx + 2.2, r.sh_y - 1.8), (cx + w - 1.2, r.sh_y - 1.8), (cx + w + 0.2, r.waist_y + 1.2),
                     (cx - w - 0.2, r.waist_y + 1.2)]) if not r.back else \
            Poly([(cx - w + 1.2, r.sh_y - 1.8), (cx + w - 1.2, r.sh_y - 1.8), (cx + w + 0.2, r.waist_y + 1.2),
                  (cx - w - 0.2, r.waist_y + 1.2)])
    cel(draw, vest, VEST, sh=(1.2, 0.8))
    if not r.back:
        for s in ((-1, 1) if not d else (d,)):
            px = cx + s * 3.2 if not d else cx + d * 1.8
            cel(draw, RRect(px - 1.7, r.sh_y + 1.6, px + 1.7, r.sh_y + 4.6, 0.6), shade(VEST, 0.6), sh=(0.0, 0.5))
            stroke(draw, [(px - 1.5, r.sh_y + 2.4), (px + 1.5, r.sh_y + 2.4)], 0.5, shade(VEST, 1.6))
        # dog tags
        if not d:
            stroke(draw, [(cx - 1.8, r.sh_y - 1.8), (cx, r.sh_y + 0.8), (cx + 1.8, r.sh_y - 1.8)], 0.4, (200, 204, 214))
            cel(draw, RRect(cx - 0.8, r.sh_y + 0.6, cx + 0.8, r.sh_y + 2.6, 0.4), (214, 218, 228), sh=None)
    rig_belt(r, draw, KHAKI, buckle=(196, 196, 206))
    if r.back:
        _rifle(draw, cx - 7.0, r.hip_y - 1.0, -35.0)
        stroke(draw, [(cx - 5.0, r.sh_y - 1.6), (cx + 5.4, r.waist_y + 0.6)], 0.9, KHAKI)
    # arms: head-on the rifle is held across the body at the ready
    if d:
        rig_arms(r, draw, OLIVE, SKIN, layer="near", reach=0.5, hands=False)
    elif not r.back:
        rig_arms(r, draw, OLIVE, SKIN, sides=(1,), reach=0.35, hands=False)
        rig_arms(r, draw, OLIVE, SKIN, sides=(-1,), reach=0.9, hands=False)
    else:
        rig_arms(r, draw, OLIVE, SKIN, layer="near")
    head_skull(r, draw, SKIN)
    if not r.back:
        head_face(r, draw, SKIN, iris=IRIS, mood="sharp", mouth="set", brows=True,
                  brow_color=shade(HAIR, 0.8))
        # a sticking plaster on the cheek
        bx = r.hx + (5.6 if not d else d * 4.0)
        cel(draw, RRect(bx - 1.3, r.head_cy + 5.6, bx + 1.3, r.head_cy + 6.9, 0.5), (246, 226, 196), sh=None)
    rig_hair(r, draw, HAIR, "crop", hat=True, skin=SKIN)
    _helmet(r, draw)
    if d:
        h = hand_at(r, d, 0.5)
        _rifle(draw, h[0], h[1], 12.0 if d > 0 else 168.0, flip=1.0 if d > 0 else -1.0)
        rig_hand(r, draw, d, SKIN, 0.5)
    elif not r.back:
        hr = hand_at(r, 1, 0.35)
        hl = hand_at(r, -1, 0.9, 0.0)
        import math
        ang = math.degrees(math.atan2(hl[1] - hr[1], hl[0] - hr[0]))
        _rifle(draw, hr[0], hr[1], ang, flip=-1.0)
        rig_hand(r, draw, 1, SKIN, 0.35)
        rig_hand(r, draw, -1, SKIN, 0.9)


def main():
    generate_character("soldier", draw_func=draw_soldier)


if __name__ == "__main__":
    main()
