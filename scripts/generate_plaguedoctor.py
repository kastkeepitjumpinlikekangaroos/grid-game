#!/usr/bin/env python3
"""Generate sprites/plaguedoctor.png -- the Plague Doctor.

A wide-brimmed black hat over a bone-white beaked mask with two round green
lenses for eyes, a long coat with a shoulder cape and a belt of vials,
leather gloves, and a lantern burning with sickly green flame.
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sprite_base import (
    Ell, Limb, Poly, RRect, arc_pts, cel, generate_character, hand_at, head_skull, ink, lit,
    rig, rig_arms, rig_belt, rig_hand, rig_legs, rig_robe, shade, sparkle, stroke,
)

COAT = (70, 62, 84)
HAT = (50, 44, 60)
BAND = (170, 58, 60)
MASK = (238, 228, 202)
LENS = (132, 240, 120)
LEATHER = (132, 90, 62)
BRASS = (216, 172, 82)
FLAME = (170, 255, 124)
VIAL = (120, 226, 110)


def _hat(r, draw):
    hx, hy, rx, ry, d = r.hx, r.head_cy, r.head_rx, r.head_ry, r.d
    cx0 = hx - d * 0.8
    by = hy - 5.8
    brim = Ell(cx0 + d * 1.2, by, 16.2 if not d else 15.0, 4.4)
    cel(draw, brim, HAT, sh=(0.0, 1.4))
    crown = Poly([(cx0 - 7.6, by + 0.4), (cx0 - 7.0, by - 8.6), (cx0 - 5.6, by - 10.0), (cx0 + 5.6, by - 10.0),
                  (cx0 + 7.0, by - 8.6), (cx0 + 7.6, by + 0.4)])
    cel(draw, crown, HAT, sh=(1.6, 0.6), hi=(0.6, 0.6))
    cel(draw, Poly([(cx0 - 7.6, by + 0.4), (cx0 + 7.6, by + 0.4), (cx0 + 7.4, by - 2.2), (cx0 - 7.4, by - 2.2)]),
        BAND, sh=None)


def _mask(r, draw):
    hx, hy, rx, ry, d = r.hx, r.head_cy, r.head_rx, r.head_ry, r.d
    head_skull(r, draw, MASK, ears=False)
    if r.back:
        # the mask's straps and the coat's hood behind
        cel(draw, Ell(hx, hy + 1.0, rx - 0.2, ry - 0.6), shade(COAT, 0.6), sh=(1.0, 1.0))
        stroke(draw, [(hx - rx + 0.6, hy + 0.6), (hx + rx - 0.6, hy + 0.6)], 1.0, LEATHER)
        return
    if d:
        # the beak in profile: long, curved, pointing forward and down
        bx = hx + d * (rx - 1.4)
        beak = Poly([(bx - d * 2.0, hy + 1.4), (bx + d * 5.0, hy + 3.6), (bx + d * 9.4, hy + 7.6),
                     (bx + d * 10.4, hy + 9.6), (bx + d * 6.4, hy + 9.4), (bx - d * 0.6, hy + 9.8)])
        cel(draw, beak, MASK, sh=(0.0, 1.2))
        stroke(draw, [(bx + d * 0.6, hy + 5.6), (bx + d * 8.4, hy + 8.6)], 0.5, shade(MASK, 1.6))
        Ell(bx + d * 2.6, hy + 4.4, 0.5, 0.4).draw(draw, fill=shade(MASK, 2.5))
        lenses = ((hx + d * 1.6, 3.2),)
    else:
        beak = Poly([(hx - 4.2, hy + 3.4), (hx + 4.2, hy + 3.4), (hx + 3.0, hy + 8.6), (hx + 1.0, hy + 13.4),
                     (hx, hy + 14.4), (hx - 1.0, hy + 13.4), (hx - 3.0, hy + 8.6)])
        cel(draw, beak, MASK, sh=(1.4, 0.6))
        stroke(draw, [(hx, hy + 5.0), (hx, hy + 12.4)], 0.5, shade(MASK, 1.5))
        for s in (-1, 1):
            Ell(hx + s * 1.4, hy + 5.4, 0.45, 0.35).draw(draw, fill=shade(MASK, 2.5))
        lenses = ((hx - 5.0, 3.4), (hx + 5.0, 3.4))
    for (lx, rad) in lenses:
        cel(draw, Ell(lx, hy + 1.6, rad, rad), BRASS, sh=(0.5, 0.5))
        cel(draw, Ell(lx, hy + 1.6, rad - 1.0, rad - 1.0), LENS, sh=(0.6, 0.6), tone=shade(LENS, 0.8), line=False)
        Ell(lx - 0.9, hy + 0.6, 0.8, 0.8).draw(draw, fill=(255, 255, 255))


def _capelet(r, draw):
    cx, d = r.cx, r.d
    y0 = r.sh_y - 2.6
    if d:
        shape = Poly([(cx - d * 4.6, y0), (cx + d * 4.0, y0), (cx + d * 6.0, r.sh_y + 4.4), (cx - d * 7.0, r.sh_y + 4.8)])
    else:
        w = r.sh_w + 2.4
        shape = Poly([(cx - w + 2.6, y0), (cx + w - 2.6, y0), (cx + w, r.sh_y + 4.2), (cx + 2.0, r.sh_y + 5.6),
                      (cx - 2.0, r.sh_y + 5.6), (cx - w, r.sh_y + 4.2)])
    cel(draw, shape, shade(COAT, 0.5), sh=(1.2, 0.8))


def _lantern(r, draw, side, out):
    h = hand_at(r, side, 0.0, 0.0, out)
    x, y = h[0], h[1] + 1.4
    stroke(draw, [(x, y - 0.4), (x, y + 1.6)], 0.5, BRASS)
    cel(draw, Poly([(x - 2.2, y + 1.6), (x + 2.2, y + 1.6), (x + 1.4, y + 0.6), (x - 1.4, y + 0.6)]), BRASS, sh=None)
    body = RRect(x - 2.3, y + 1.6, x + 2.3, y + 7.0, 0.8)
    cel(draw, body, (220, 255, 200), sh=None, line_color=ink(BRASS))
    flick = [0.0, 0.5, 0.0, -0.5][r.frame]
    Poly([(x - 1.3, y + 6.2), (x + flick, y + 2.4), (x + 1.3, y + 6.2)]).draw(draw, fill=FLAME)
    Ell(x, y + 5.4, 0.7, 0.9).draw(draw, fill=(250, 255, 236))
    for sx in (-2.3, 2.3):
        stroke(draw, [(x + sx, y + 1.6), (x + sx, y + 7.0)], 0.6, BRASS)
    cel(draw, RRect(x - 2.6, y + 7.0, x + 2.6, y + 8.2, 0.5), BRASS, sh=None)


def draw_plaguedoctor(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, head=0.95)
    d = r.d
    lamp_side = (1 if not r.back else -1) if not d else d
    out = 1.4 if not d else 0.6
    if d:
        rig_arms(r, draw, COAT, LEATHER, layer="far")
    rig_legs(r, draw, shade(COAT, 1.0), shade(LEATHER, 1.0))
    rig_robe(r, draw, COAT, flare=3.6, split=True)
    rig_belt(r, draw, LEATHER, buckle=BRASS)
    if not r.back:
        for i, x in enumerate((-3.6, 3.8) if not d else (d * 2.4,)):
            vx = r.cx + x
            cel(draw, RRect(vx - 1.0, r.waist_y + 2.6, vx + 1.0, r.waist_y + 6.0, 0.6), VIAL, sh=(0.4, 0.0),
                line_color=ink(VIAL))
            cel(draw, RRect(vx - 0.7, r.waist_y + 1.8, vx + 0.7, r.waist_y + 2.8, 0.3), LEATHER, sh=None)
    _capelet(r, draw)
    rig_arms(r, draw, COAT, LEATHER, layer="near", hands=False, out=0.0)
    _mask(r, draw)
    _hat(r, draw)
    if not r.back:
        _lantern(r, draw, lamp_side, out)
    for side in ((-1, 1) if not d else (d,)):
        rig_hand(r, draw, side, LEATHER, out=out if side == lamp_side else 0.0)


def main():
    generate_character("plaguedoctor", draw_func=draw_plaguedoctor)


if __name__ == "__main__":
    main()
