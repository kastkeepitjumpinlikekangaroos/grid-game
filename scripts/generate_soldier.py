#!/usr/bin/env python3
"""Generate sprites/soldier.png -- the Soldier.

A cute army grunt: a round green steel helmet with goggles strapped to it,
olive drab fatigues under a tactical vest with chest pouches, dog tags, big
brown boots, and a chunky wooden rifle held at the ready (slung across his
back from behind).
"""

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sprite_base import (
    SKIN, Ell, Limb, Poly, RRect, arc_pts, arm_pts, cel, generate_character, hand_at, head_face,
    head_skull, ink, lit, rig, rig_arms, rig_belt, rig_hair, rig_hand, rig_legs, rig_torso,
    shade, stroke, xform,
)

# Three greens a clear step apart: the helmet a deep, full green (on the grass
# it was close to the lawn's own), the fatigues a paler, yellower drab, the
# vest the darkest of the three. Helmet and uniform were one olive.
HELMET = (92, 126, 68)
OLIVE = (146, 150, 92)
VEST = (90, 104, 62)
KHAKI = (210, 188, 134)
BOOT = (98, 72, 52)
# The rifle is mostly wood, a warm brown a step lighter than anything it lies
# across, with its steel light enough to be seen on the vest (dark steel on the
# dark vest, the whole rifle was lost head-on but for its stock).
GUN = (128, 136, 158)
WOOD = (186, 124, 70)
HAIR = (116, 78, 52)
LENS = (140, 214, 236)
IRIS = (92, 76, 58)
PLASTER = (240, 200, 156)


def _helmet(r, draw):
    hx, hy, rx, ry, d = r.hx, r.head_cy, r.head_rx, r.head_ry, r.d
    cx0 = hx - d * 0.6
    dome = arc_pts(cx0, hy - 1.2, rx + 1.6, ry + 0.8, 180, 360, 30)
    lip = [(cx0 + rx + 2.4, hy - 0.4), (cx0 - rx - 2.4, hy - 0.4)]
    cel(draw, Poly(dome + lip), HELMET, sh=(1.5, 1.0), hi=(0.9, 0.9))
    # the rim, a band of shadow under the dome
    cel(draw, RRect(cx0 - rx - 2.6, hy - 2.0, cx0 + rx + 2.6, hy + 0.2, 1.0), shade(HELMET, 1.0), sh=None)
    # the goggles' strap goes all the way round the helmet
    strap = shade(HELMET, 2.2)
    stroke(draw, [(cx0 - rx - 0.6, hy - 4.2), (cx0 + rx + 0.6, hy - 4.2)] if not d else
           [(cx0 - d * (rx + 0.8), hy - 4.0), (cx0 + d * (rx + 0.4), hy - 4.6)], 1.1, strap)
    if r.back:
        # ...to its buckle at the back
        cel(draw, RRect(cx0 - 1.7, hy - 5.4, cx0 + 1.7, hy - 3.0, 0.5), (196, 200, 212), sh=(0.4, 0.4), lw=0.7)
        return
    # goggles pushed up onto the helmet
    gx = cx0 + d * 3.6
    for k in ((-2.4, 2.4) if not d else (0.6,)):
        lx = gx + k * (1 if not d else d)
        cel(draw, Ell(lx, hy - 4.6, 2.3, 2.0), (70, 74, 88), sh=None)
        cel(draw, Ell(lx, hy - 4.6, 1.5, 1.3), LENS, sh=None, line=False)
        Ell(lx - 0.5, hy - 5.1, 0.5, 0.5).draw(draw, fill=(255, 255, 255))
    # the chin strap, down the edge of each cheek. Not in profile: there it
    # stopped in mid cheek behind the eye, a green tear streak.
    if not d:
        for s in (-1, 1):
            x = cx0 + s * (rx - 0.6)
            stroke(draw, [(x, hy - 0.2), (x - s * 1.2, hy + 6.6)], 0.7, strap)


def _rifle(draw, x, y, ang, flip=1.0):
    """A chunky wooden rifle gripped at (x, y), pointing along `ang`: a deep
    stock, a steel receiver with a box magazine, a long wooden fore-end and
    the barrel's end past it, 26 units from butt to muzzle."""
    def at(pts):
        return xform(pts, x, y, ang, 1.0, flip)
    # steel is lined a step softer than the kit's ink, or its small parts are all line
    steel_dk, line = shade(GUN, 1.2), shade(GUN, 2.6)
    cel(draw, Limb(at([(12.0, -0.3), (17.2, -0.3)]), [1.05, 1.05]), GUN, sh=None, line_color=line)
    cel(draw, Poly(at([(15.2, -1.2), (16.2, -1.2), (16.0, -2.5), (15.4, -2.5)])), steel_dk, sh=None, lw=0.6, line_color=line)
    cel(draw, Poly(at([(1.2, 0.6), (3.5, 0.6), (4.0, 3.8), (1.9, 4.0)])), steel_dk, sh=None, line_color=line)
    cel(draw, Poly(at([(-8.4, -1.0), (-1.6, -1.8), (-0.6, 1.2), (-2.6, 1.7), (-8.4, 3.2)])), WOOD, sh=None,
        regions=[(Poly(at([(-8.4, 1.4), (-0.6, 0.2), (-0.6, 3.4), (-8.4, 3.4)])), shade(WOOD, 0.8))])
    cel(draw, Poly(at([(4.6, -1.6), (12.6, -1.4), (12.6, 1.0), (4.6, 1.3)])), WOOD, sh=None,
        regions=[(Poly(at([(4.6, 0.1), (12.6, 0.0), (12.6, 1.4), (4.6, 1.4)])), shade(WOOD, 0.8))])
    cel(draw, Poly(at([(-1.9, -2.0), (5.0, -2.0), (5.0, 1.0), (-1.9, 1.0)])), GUN, sh=None, line_color=line,
        regions=[(Poly(at([(-1.9, -0.1), (5.0, -0.1), (5.0, 1.0), (-1.9, 1.0)])), shade(GUN, 0.8))])
    cel(draw, Poly(at([(0.8, -1.9), (2.6, -1.9), (2.4, -3.0), (1.0, -3.0)])), steel_dk, sh=None, lw=0.6, line_color=line)
    stroke(draw, at([(-1.2, -1.3), (4.4, -1.3)]), 0.5, lit(GUN, 1.6))


def _arm(r, draw, sh, el, hand, far=False):
    """A sleeve from its shoulder to a hand placed on the rifle (the rig's arms
    hang to the hip, and swing: the rifle went back and forth with every
    stride)."""
    wr = (hand[0] + (el[0] - hand[0]) * 0.22, hand[1] + (el[1] - hand[1]) * 0.22)
    aw = 2.2 * min(r.build, 1.3)
    cel(draw, Limb([sh, el, wr], [aw, aw * 0.9, aw * 0.82]), OLIVE if not far else shade(OLIVE, 0.6), sh=(0.8, 0.4))


def _hand(draw, h, far=False):
    cel(draw, Ell(h[0], h[1], 2.2, 2.2), SKIN if not far else shade(SKIN, 0.5), sh=(0.6, 0.6))


def draw_soldier(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=1.05)
    d = r.d
    cx, sy, wy = r.cx, r.sh_y, r.waist_y
    # The rifle at the ready. Head-on it lies across him from the butt at his
    # right hip up to the muzzle past his left shoulder, his right hand at the
    # grip and his left under the fore-end; in profile it is levelled ahead of
    # him, the stock under his arm. Either way it is where his hands are, and
    # his hands do not swing.
    if d:
        grip, ang, flip = (cx + d * 0.6, wy + 0.6), (7.0 if d > 0 else 173.0), float(d)
    else:
        grip, ang, flip = (cx - 5.2, wy + 1.2), -22.0, 1.0
    fore = xform([(9.6 if not d else 8.4, 0.9)], grip[0], grip[1], ang, 1.0, flip)[0]
    if d:
        _arm(r, draw, r.shoulder(-d), (cx + d * 3.6, sy + 5.2), fore, far=True)
    rig_legs(r, draw, OLIVE, BOOT)
    rig_torso(r, draw, OLIVE)
    # tactical vest over the shirt
    if d:
        vest = Poly([(cx - d * 3.0, sy - 1.4), (cx + d * 3.4, sy - 1.0), (cx + d * 4.6, wy + 1.0), (cx - d * 4.2, wy + 1.0)])
    else:
        w = r.sh_w - 0.6
        vest = Poly([(cx - w + 1.2, sy - 1.8), (cx - 2.2, sy - 1.8), (cx, sy + 1.0),
                     (cx + 2.2, sy - 1.8), (cx + w - 1.2, sy - 1.8), (cx + w + 0.2, wy + 1.2),
                     (cx - w - 0.2, wy + 1.2)]) if not r.back else \
            Poly([(cx - w + 1.2, sy - 1.8), (cx + w - 1.2, sy - 1.8), (cx + w + 0.2, wy + 1.2), (cx - w - 0.2, wy + 1.2)])
    cel(draw, vest, VEST, sh=(1.2, 0.8))
    if not r.back:
        for s in ((-1, 1) if not d else (d,)):
            px = cx + s * 3.2 if not d else cx + d * 1.8
            cel(draw, RRect(px - 1.7, sy + 1.6, px + 1.7, sy + 4.6, 0.6), shade(VEST, 0.6), sh=(0.0, 0.5))
            stroke(draw, [(px - 1.5, sy + 2.4), (px + 1.5, sy + 2.4)], 0.5, shade(VEST, 1.6))
        # dog tags
        if not d:
            stroke(draw, [(cx - 1.8, sy - 1.8), (cx, sy + 0.8), (cx + 1.8, sy - 1.8)], 0.4, (200, 204, 214))
            cel(draw, RRect(cx - 0.8, sy + 0.6, cx + 0.8, sy + 2.6, 0.4), (214, 218, 228), sh=None)
    rig_belt(r, draw, KHAKI, buckle=(196, 196, 206))
    if r.back:
        # from behind the rifle is slung across his back, muzzle up behind
        # his right shoulder: on its own, it hangs from its sling, which is
        # round his front (the two used to cross on his back as an X)
        rig_arms(r, draw, OLIVE, SKIN, layer="near")
        _rifle(draw, cx - 1.6, wy + 0.6, -52.0, flip=-1.0)
    elif d:
        _arm(r, draw, r.shoulder(d), (cx - d * 1.8, sy + 4.0), grip)
    else:
        _arm(r, draw, r.shoulder(-1), (cx - 9.2, sy + 4.6), grip)
        _arm(r, draw, r.shoulder(1), (cx + 9.0, sy + 5.6), fore)
    head_skull(r, draw, SKIN)
    if not r.back:
        head_face(r, draw, SKIN, iris=IRIS, mood="sharp", mouth="set", brows=True, brow_color=shade(HAIR, 0.8))
        # a sticking plaster on the cheek: a plaster's own tan, lined softly
        # (lined in ink it was a brown smudge)
        bx, by = r.hx + (5.8 if not d else d * 4.2), r.head_cy + 6.3
        cel(draw, Poly(xform([(-1.8, -0.9), (1.8, -0.9), (1.8, 0.9), (-1.8, 0.9)], bx, by, -14.0 if d >= 0 else 14.0)), PLASTER,
            sh=None, lw=0.5, line_color=shade(PLASTER, 1.8))
    rig_hair(r, draw, HAIR, "crop", hat=True, skin=SKIN)
    _helmet(r, draw)
    if r.back:
        return
    _rifle(draw, grip[0], grip[1], ang, flip)
    _hand(draw, grip)
    _hand(draw, fore, far=bool(d))


def main():
    generate_character("soldier", draw_func=draw_soldier)


if __name__ == "__main__":
    main()
