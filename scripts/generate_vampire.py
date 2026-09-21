#!/usr/bin/env python3
"""Generate sprites/vampire.png -- the Vampire.

A gothic count: slicked-back black hair with a widow's peak, pointed ears,
pale skin, red eyes and fangs; a black cape lined with crimson whose tall
collar stands up behind his head like a pair of wings; a tailcoat over a red
waistcoat and a white cravat pinned with a ruby.
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sprite_base import (
    Ell, Limb, Poly, RRect, arc_pts, cel, gem, generate_character, hand_at, head_face,
    head_skull, ink, lit, rig, rig_arms, rig_belt, rig_hair, rig_hand, rig_legs, rig_torso,
    shade, stroke,
)

CAPE = (48, 38, 60)
LINING = (208, 34, 56)
VEST = (168, 30, 50)
SKIN_V = (242, 234, 242)
HAIR = (46, 40, 60)
IRIS = (224, 40, 58)
CRAVAT = (246, 244, 248)
GOLD = (240, 196, 84)

SLICK = dict(vol=1.3, fringe=((0.0, 3.4),), sweep=0.0, side=2.4, back=7.0)


def _collar(r, draw):
    """The tall stand-up collar: two red-lined wings behind the head."""
    hx, hy, d = r.hx, r.head_cy, r.d
    y0 = r.sh_y - 1.0
    if d:
        wing = Poly([(hx - d * 2.0, y0), (hx - d * 11.0, hy - 3.6), (hx - d * 13.4, hy - 6.4),
                     (hx - d * 12.4, hy + 4.0), (hx - d * 6.0, y0 + 1.0)])
        cel(draw, wing, LINING, sh=(0.0, 0.0), line_color=ink(CAPE))
        return
    for s in (-1, 1):
        outer = Poly([(hx + s * 5.0, y0 + 0.6), (hx + s * 13.8, hy - 4.2), (hx + s * 15.4, hy - 7.6),
                      (hx + s * 15.8, hy + 2.0), (hx + s * 11.0, y0 + 1.4)])
        cel(draw, outer, CAPE if r.back else LINING, sh=(0.0, 0.0), line_color=ink(CAPE))
        if not r.back:
            cel(draw, Poly([(hx + s * 13.8, hy - 4.2), (hx + s * 15.4, hy - 7.6), (hx + s * 15.8, hy + 2.0),
                            (hx + s * 14.4, hy + 1.2)]), CAPE, sh=None, line=False)


def _cape_back(r, draw):
    cx, d = r.cx, r.d
    sway = r.ph * 1.4
    bottom = r.base_y - 1.4
    if d:
        cel(draw, Poly([(cx - d * 1.0, r.sh_y - 2.6), (cx - d * 5.0, r.sh_y - 1.0), (cx - d * (10.4 + abs(sway)), bottom),
                        (cx - d * 6.4, bottom - 1.6), (cx - d * 3.0, bottom), (cx + d * 1.0, r.hip_y)]),
            LINING, sh=(0.0, 0.0))
        cel(draw, Poly([(cx - d * 1.2, r.sh_y - 2.4), (cx - d * 5.0, r.sh_y - 0.8), (cx - d * (10.4 + abs(sway)), bottom),
                        (cx - d * 8.0, bottom - 0.6)]), CAPE, sh=(0.8, 0.0))
        return
    w = r.sh_w
    if r.back:
        pts = [(cx - w + 0.4, r.sh_y - 2.8), (cx + w - 0.4, r.sh_y - 2.8), (cx + w + 5.0 + sway, bottom)]
        # scalloped, bat-wing hem
        for i in range(1, 6):
            t = i / 6.0
            x = cx + w + 5.0 + sway - (2 * w + 10.0) * t
            pts.append((x, bottom - (2.2 if i % 2 else 0.0)))
        pts.append((cx - w - 5.0 + sway, bottom))
        cel(draw, Poly(pts), CAPE, sh=(1.8, 0.0))
        return
    cel(draw, Poly([(cx - w + 0.2, r.sh_y - 2.8), (cx + w - 0.2, r.sh_y - 2.8), (cx + w + 5.0 + sway, bottom),
                    (cx - w - 5.0 + sway, bottom)]), LINING, sh=(1.4, 0.0))


def draw_vampire(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=0.96)
    d = r.d
    if not r.back:
        _cape_back(r, draw)
        _collar(r, draw)
    if d:
        rig_arms(r, draw, CAPE, SKIN_V, layer="far")
    rig_legs(r, draw, CAPE, (30, 26, 36))
    rig_torso(r, draw, CAPE)
    cx = r.cx
    if not r.back:
        vx = cx + d * 1.6
        cel(draw, Poly([(vx - 3.6, r.sh_y - 1.8), (vx + 3.6, r.sh_y - 1.8), (vx + 3.0, r.waist_y + 2.6),
                        (vx, r.waist_y + 3.8), (vx - 3.0, r.waist_y + 2.6)]), VEST, sh=(0.8, 0.0))
        for y in (r.sh_y + 3.4, r.sh_y + 5.8):
            Ell(vx, y, 0.6, 0.6).draw(draw, fill=GOLD)
        # cravat, pinned with a ruby
        cel(draw, Poly([(vx - 2.6, r.sh_y - 2.4), (vx + 2.6, r.sh_y - 2.4), (vx + 1.6, r.sh_y + 1.8), (vx, r.sh_y + 2.6),
                        (vx - 1.6, r.sh_y + 1.8)]), CRAVAT, sh=(0.6, 0.4))
        gem(draw, vx, r.sh_y + 0.4, 1.2, LINING)
    rig_arms(r, draw, CAPE, SKIN_V, layer="near", cuff=CRAVAT)
    head_skull(r, draw, SKIN_V, ears=False)
    # pointed ears
    hx, hy, rx = r.hx, r.head_cy, r.head_rx
    for s in ((-1, 1) if not d else (-d,)):
        ex = hx + s * (rx - 0.6) if not d else hx - d * 3.0
        tip = (ex + s * 3.4, hy - 1.6) if not d else (ex - d * 3.0, hy - 1.4)
        cel(draw, Poly([(ex, hy + 1.0), tip, (ex + (s * 0.4 if not d else 0.0), hy + 4.4)]), SKIN_V, sh=(0.3, 0.3))
    if not r.back:
        head_face(r, draw, SKIN_V, iris=IRIS, mood="sharp", mouth="fangs", blush=False, brows=True,
                  brow_color=HAIR)
    rig_hair(r, draw, HAIR, SLICK, skin=SKIN_V)
    if r.back:
        _collar(r, draw)
        _cape_back(r, draw)


def main():
    generate_character("vampire", draw_func=draw_vampire)


if __name__ == "__main__":
    main()
