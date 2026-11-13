#!/usr/bin/env python3
"""Generate the Spaceman character sprite sheet (sprites/character.png).

A space explorer in a glass fishbowl helmet, with his face -- big eyes, a mop
of ginger hair -- visible inside it. The helmet, the tall white boots and
gloves and the life-support pack are the oversized gear; the suit keeps the
blue and cyan the character has always worn.

From behind he is the pack: a white box as wide as his back, from under the
bubble to his hips, with a dark panel of status lights. It is drawn over the
arms there (it is nearer the camera than they are) and behind the body in
profile, where it stands well out past his back.
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sprite_base import (
    HAIR_STYLES, SKIN, Ell, Limb, Poly, RRect, arc_pts, blob, cel, generate_character, head_face,
    head_skull, ink, lit, rig, rig_arms, rig_hair, rig_hand, rig_legs, rig_torso,
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
BELT = shade(SUIT, 1.8)
PANEL = (44, 58, 104)       # the dark face of a panel of lights: the suit's blue, nearly night


def _lights(r, draw, pts, rad=1.0):
    """Status lights, cyan and orange by turns; one of them is lit brighter each frame."""
    for i, (x, y) in enumerate(pts):
        col = (ACCENT, ORANGE)[i % 2]
        Ell(x, y, rad, rad).draw(draw, fill=lit(col, 1.3) if (r.frame + i) % 3 == 0 else col)


def _tank(draw, x0, x1, top, bot, near=True):
    """One air tank of the pack: a white cylinder with an orange band round it."""
    band = RRect(x0 - 1, top + 6.9, x1 + 1, top + 8.8, 0.1)
    col = PACK if near else shade(PACK, 0.6)
    cel(draw, RRect(x0, top, x1, bot, 3.0), col, sh=(1.2, 0.9), hi=(0.7, 0.6),
        regions=[(band, ORANGE if near else shade(ORANGE, 0.6))])


def _pack(r, draw):
    """The life-support pack: twin air tanks, from under the bubble to his hips."""
    cx, d = r.cx, r.d
    top, bot = r.sh_y - 3.0, r.hip_y + 1.0
    if d:
        # the near tank, standing well out past his back
        x0, x1 = cx - d * 3.6, cx - d * 10.3
        _tank(draw, min(x0, x1), max(x0, x1), top, bot)
        return
    if not r.back:
        return
    # the frame the two tanks are strapped to shows between them, with the regulator's light on it
    cel(draw, RRect(cx - 3.0, top, cx + 3.0, bot - 1.6, 1.0), shade(PACK, 1.9), sh=None, lw=0.7)
    _tank(draw, cx - 7.0, cx - 1.3, top, bot)
    _tank(draw, cx + 1.3, cx + 7.0, top, bot)
    Ell(cx, top + 7.85, 1.0, 1.0).draw(draw, fill=lit(ACCENT, 1.3) if r.frame % 2 == 0 else ACCENT)


def _helmet_back(r, draw):
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    cel(draw, Ell(hx, hy + 0.4, rx + 2.9, ry + 3.0), GLASS, sh=(1.6, 1.4), line_color=ink(shade(GLASS, 1.5)))


def _helmet_front(r, draw):
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    ox, oy, orx, ory = hx, hy + 0.4, rx + 2.9, ry + 3.0
    # glare across the glass, on the side the light comes from (upper left) whichever way he faces
    a0, a1 = (200, 250) if r.back else (196, 246)
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
    n = 3 if not d else 2
    _lights(r, draw, [(px - 1.9 * (n - 1) / 2.0 + i * 1.9, r.sh_y + 2.4) for i in range(n)], 0.85)


def _belt(r, draw):
    """The suit's belt. Its light is on the front: in profile a buckle is a dark block poking
    out from behind the glove."""
    cx, y = r.cx, r.waist_y + 1.0
    w = r.hip_w + 0.6 if not r.d else 4.55
    cel(draw, RRect(cx - w, y, cx + w, y + 2.2, 0.6), BELT, sh=(0.0, 0.6))
    if r.front:
        cel(draw, RRect(cx - 1.8, y - 0.5, cx + 1.8, y + 2.7, 0.9), ACCENT, sh=None, line_color=ink(BELT), lw=0.5)
        Ell(cx - 0.3, y + 0.8, 0.8, 0.6).draw(draw, fill=lit(ACCENT, 2.2))


def _legs(r, draw):
    """Suit legs in tall white boots: the kit's shoe with a shaft up the shin, one mass."""
    for side in ((-r.d, r.d) if r.d else (-1, 1)):
        rig_legs(r, draw, SUIT, TRIM, sides=(side,), width=1.05)
        (hx0, hy0), (fx, fy) = r.hip(side), r.foot(side)
        t = 0.6
        shaft = Limb([(hx0 + (fx - hx0) * t, hy0 + (fy - 1.2 - hy0) * t), (fx, fy - 1.2)], [2.8, 2.6], cap=False)
        if r.d:
            x0, x1 = fx - r.d * 2.4, fx + r.d * 3.7
            foot = RRect(min(x0, x1), fy - 2.1, max(x0, x1), fy + 1.4, 1.6)
        else:
            foot = RRect(fx - 2.9, fy - 2.0, fx + 2.9, fy + 1.4, 1.6)
        blob(draw, [shaft, foot], TRIM if r.near(side) else shade(TRIM, 0.5), sh=(0.0, 0.7))


def draw_spaceman(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, head=0.92)
    d = r.d
    if d:
        rig_arms(r, draw, SUIT, TRIM, layer="far", hand_r=2.5, cuff=TRIM)
        _pack(r, draw)
    _legs(r, draw)
    rig_torso(r, draw, SUIT)
    # white suit panels down the sides
    if r.front:
        for s in (-1, 1):
            cel(draw, Poly([(r.cx + s * (r.sh_w - 1.2), r.sh_y - 1.4), (r.cx + s * (r.sh_w - 0.1), r.sh_y + 0.6),
                            (r.cx + s * (r.hip_w - 0.2), r.hip_y - 0.2), (r.cx + s * (r.hip_w - 2.2), r.hip_y - 0.2),
                            (r.cx + s * (r.sh_w - 2.6), r.sh_y + 0.8)]), TRIM, sh=None, line=False)
    _chest(r, draw)
    _belt(r, draw)
    if r.back:
        # the pack lies over the arms, and the gloves over its corners
        rig_arms(r, draw, SUIT, TRIM, layer="near", hand_r=2.5, cuff=TRIM, hands=False)
        _pack(r, draw)
        for s in (-1, 1):
            rig_hand(r, draw, s, TRIM, hand_r=2.5)
    else:
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
