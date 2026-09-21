#!/usr/bin/env python3
"""Generate the Spaceman character sprite sheet (sprites/character.png).

A space explorer in a glass fishbowl helmet, with his face -- big eyes, a mop
of ginger hair -- visible inside it. The helmet, the chunky white boots and
gloves and the life-support pack are the oversized gear; the suit keeps the
blue and cyan the character has always worn.
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sprite_base import (
    HAIR_STYLES, SKIN, Ell, Limb, Poly, RRect, arc_pts, cel, generate_character, head_face,
    head_skull, ink, lit, rig, rig_arms, rig_belt, rig_hair, rig_legs, rig_torso,
    shade, sparkle,
)

SUIT = (84, 132, 216)
TRIM = (238, 242, 250)
ACCENT = (98, 228, 226)
ORANGE = (252, 158, 66)
GLASS = (204, 238, 252)
HAIR = (206, 110, 60)
PACK = (212, 220, 236)
IRIS = (64, 118, 196)


def _pack(r, draw):
    cx, d = r.cx, r.d
    if d:
        x0 = cx - d * 4.0
        shape = RRect(min(x0, x0 - d * 5.2), r.sh_y - 3.0, max(x0, x0 - d * 5.2), r.waist_y + 1.6, 1.8)
        cel(draw, shape, PACK, sh=(1.0, 1.0))
        tx = x0 - d * 2.6
        cel(draw, RRect(tx - 1.3, r.sh_y - 4.6, tx + 1.3, r.sh_y - 1.8, 0.6), shade(PACK, 1.4), sh=None)
        return
    if r.back:
        shape = RRect(cx - 6.2, r.sh_y - 3.2, cx + 6.2, r.waist_y + 1.8, 2.0)
        cel(draw, shape, PACK, sh=(1.2, 1.2))
        for i, col in enumerate((ACCENT, ORANGE, ACCENT)):
            Ell(cx - 2.6 + i * 2.6, r.sh_y + 1.4, 0.9, 0.9).draw(
                draw, fill=lit(col, 1.2) if (r.frame + i) % 3 == 0 else col)
        cel(draw, RRect(cx - 3.8, r.waist_y - 3.4, cx + 3.8, r.waist_y - 1.2, 0.8), shade(PACK, 1.2), sh=None)
        return
    # head-on the pack only shows past the shoulders
    cel(draw, RRect(cx - 8.4, r.sh_y - 2.8, cx + 8.4, r.waist_y + 0.6, 2.0), PACK, sh=(1.0, 1.0))


def _helmet_back(r, draw):
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    cel(draw, Ell(hx, hy + 0.4, rx + 2.9, ry + 3.0), GLASS, sh=(1.6, 1.4), line_color=ink(shade(GLASS, 1.5)))


def _helmet_front(r, draw):
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    ox, oy, orx, ory = hx, hy + 0.4, rx + 2.9, ry + 3.0
    # glare across the glass, over the face
    a0, a1 = (196, 246) if r.d >= 0 else (294, 344)
    if r.back:
        a0, a1 = 200, 250
    outer = arc_pts(ox, oy, orx - 1.1, ory - 1.1, a0, a1, 10)
    inner = arc_pts(ox, oy, orx - 2.6, ory - 2.6, a1, a0, 10)
    Poly(outer + inner).draw(draw, fill=(255, 255, 255))
    gx, gy = arc_pts(ox, oy, orx - 1.9, ory - 1.9, a1 + 14, a1 + 14, 1)[0]
    Ell(gx, gy, 0.9, 0.9).draw(draw, fill=(255, 255, 255))
    # metal collar where the bubble meets the suit
    cy = hy + ry + 2.2
    w = 8.6 if not r.d else 7.4
    cx0 = hx - r.d * 0.6
    cel(draw, RRect(cx0 - w, cy - 1.4, cx0 + w, cy + 1.7, 1.4), TRIM, sh=(0.0, 0.8))
    if not r.back:
        lx = cx0 + r.d * 3.0
        Ell(lx, cy + 0.15, 1.0, 1.0).draw(draw, fill=lit(ACCENT, 1.3) if r.frame % 2 == 0 else ACCENT)
    # antenna on the side of the bubble, blinking
    side = 1 if not r.d else -r.d
    if r.back:
        side = -1
    base_a = 322 if side > 0 else 218
    ax, ay = arc_pts(ox, oy, orx - 0.2, ory - 0.2, base_a, base_a, 1)[0]
    cel(draw, Ell(ax, ay, 1.5, 1.2), TRIM, sh=(0.0, 0.5))
    tip = (ax + side * 2.6, ay - 6.2)
    cel(draw, Limb([(ax, ay), tip], [0.7, 0.5]), shade(TRIM, 1.2), sh=None)
    blink = r.frame % 2 == 0
    cel(draw, Ell(tip[0], tip[1], 1.6, 1.6), ORANGE if blink else shade(ORANGE, 0.6), sh=None)
    if blink:
        sparkle(draw, tip[0], tip[1], 2.6, lit(ORANGE, 1.4), core=(255, 250, 230))


def _chest(r, draw):
    cx, d = r.cx, r.d
    if r.back:
        return
    px = cx + d * 2.0
    cel(draw, RRect(px - 3.4, r.sh_y + 0.2, px + 3.4, r.sh_y + 4.6, 1.0), TRIM, sh=(0.0, 0.6))
    lights = (ACCENT, ORANGE, ACCENT) if not d else (ACCENT, ORANGE)
    for i, col in enumerate(lights):
        x = px - 1.9 * (len(lights) - 1) / 2.0 + i * 1.9
        Ell(x, r.sh_y + 2.4, 0.8, 0.8).draw(draw, fill=lit(col, 1.3) if (r.frame + i) % 3 == 0 else col)


def draw_spaceman(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, head=0.92)
    d = r.d
    if not r.back:
        _pack(r, draw)
    if d:
        rig_arms(r, draw, SUIT, TRIM, layer="far", hand_r=2.5, cuff=TRIM)
    rig_legs(r, draw, SUIT, TRIM, width=1.05)
    rig_torso(r, draw, SUIT)
    # white suit panels down the sides
    if not d:
        for s in (-1, 1):
            cel(draw, Poly([(r.cx + s * (r.sh_w - 1.2), r.sh_y - 1.4), (r.cx + s * (r.sh_w - 0.1), r.sh_y + 0.6),
                            (r.cx + s * (r.hip_w - 0.2), r.hip_y - 0.2), (r.cx + s * (r.hip_w - 2.2), r.hip_y - 0.2),
                            (r.cx + s * (r.sh_w - 2.6), r.sh_y + 0.8)]), TRIM, sh=None, line=False)
    _chest(r, draw)
    rig_belt(r, draw, shade(SUIT, 1.8), buckle=ACCENT)
    if r.back:
        _pack(r, draw)
    rig_arms(r, draw, SUIT, TRIM, layer="near", hand_r=2.5, cuff=TRIM)
    _helmet_back(r, draw)
    head_skull(r, draw, SKIN)
    if not r.back:
        head_face(r, draw, SKIN, iris=IRIS, mouth="o" if frame in (1, 3) else "smile")
    rig_hair(r, draw, HAIR, dict(HAIR_STYLES["short"], vol=1.2), skin=SKIN)
    _helmet_front(r, draw)


def main():
    generate_character("character", draw_func=draw_spaceman)


if __name__ == "__main__":
    main()
