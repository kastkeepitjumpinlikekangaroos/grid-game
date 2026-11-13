#!/usr/bin/env python3
"""Mythological character sprite generators (IDs 87-101).

Fifteen creatures of legend, in MapleStory's style. Many are made of parts of
other things -- a bull's head on a man, a woman on a snake, a man on a horse,
a lion with a pharaoh's head, an eagle's head on a lion, a lion with a goat
and a snake growing out of it -- and each is drawn so the parts read at 77px:
the thing that makes it itself is the biggest shape in the frame.
"""

import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from sprite_base import (
    DOWN, UP, LEFT, RIGHT, FIRE, GOLD, LEATHER, STEEL, WOOD, SKIN, SKIN_TAN, SKIN_BROWN, TOOTH,
    HAIR_STYLES, Ell, Limb, Poly, RRect, arc_pts, blade, blob, bolt, bow, cel, cloud, creature_setup,
    crystal, draw_paw, eye_pair, flame, flame_pts, gem, generate_character, hand_at, head_face,
    head_skull, ink, leaf, lit, mix, ms_eye, ms_mouth, quad_legs, rig, rig_arms, rig_belt, rig_cape,
    rig_hair, rig_hand, rig_head, rig_hood, rig_legs, rig_robe, rig_torso, rot_pts, shade, sparkle,
    spear, star, stroke, xform, arm_pts, face_anchor, bez, clipped, EYE_WHITE,
)

VOID = (34, 26, 40)


def _sides(r):
    return (-1, 1) if not r.d else (r.d,)


def _hold_side(r):
    return (1 if not r.back else -1) if not r.d else r.d


def _horn(draw, base, side, color, length=1.0, curl=1.0, width=2.4):
    """A horn curving out from `base` to `side`, then up."""
    x, y = base
    pts = [(x, y), (x + side * 4.4 * length, y - 1.4 * curl), (x + side * 7.4 * length, y - 4.6 * curl),
           (x + side * 7.6 * length, y - 8.4 * curl)]
    cel(draw, Limb(pts, [width, width * 0.8, width * 0.5, 0.3]), color, sh=(0.4, 0.6))
    for t in (1, 2):
        p = pts[t]
        stroke(draw, [(p[0] - 1.2, p[1] + side * 0.4), (p[0] + 1.2, p[1] - side * 0.4)], 0.4, shade(color, 1.3))


# ===================================================================
# MINOTAUR (87) -- a bull's head, great horns, a nose ring, a labrys
# ===================================================================

from sprite_base import torso_shape as _mino_kit_torso  # noqa: E402  (the baldric is clipped to it)

MINO_FUR = (128, 78, 56)        # a dark bay: (152, 96, 62) was the boardwalk's own brown, and the Centaur's horse
MINO_DK = (76, 46, 44)          # the mane down the back of his neck, the forelock, the tuft on his tail, the baldric
MINO_LT = (214, 170, 124)
MINO_HORN = (242, 232, 208)
MINO_KILT = (182, 48, 50)
MINO_HOOF = (70, 60, 66)
MINO_STEEL = (172, 180, 202)    # the axe's cheeks, a step under its bright edge: pale steel all over was the snow's colour
MINO_EDGE = (240, 243, 250)


def _mino_labrys(draw, gx, gy, ang, up=16.3, down=10.4):
    """The labrys, gripped at (gx, gy): a long haft, `down` of it behind the grip and `up` ahead
    along `ang`, and near its top the double axe -- two crescent bits back to back, each a dark
    cheek and a bright ground edge, under a spike. Drawn before the hand that holds it."""
    c = up - 5.9                                # the middle of the axe head, along the haft

    def P(pts):
        return Poly(xform(pts, gx, gy, ang))
    cel(draw, P([(up - 1.8, -1.0), (up + 2.8, 0.0), (up - 1.8, 1.0)]), MINO_STEEL, sh=None, lw=0.6)
    for sgn in (1, -1):
        edge = bez((c + 6.2, 5.8 * sgn), (c, 11.8 * sgn), (c - 6.2, 5.8 * sgn), 8)
        inner = bez((c + 5.3, 4.9 * sgn), (c, 8.4 * sgn), (c - 5.3, 4.9 * sgn), 8)
        bit = bez((c + 1.6, 0.5 * sgn), (c + 2.0, 4.0 * sgn), (c + 6.2, 5.8 * sgn), 4) + edge[1:] + \
            bez((c - 6.2, 5.8 * sgn), (c - 2.0, 4.0 * sgn), (c - 1.6, 0.5 * sgn), 4)[1:]
        cel(draw, P(bit), MINO_STEEL, sh=(0.7, 0.7), regions=[(P(edge + inner[::-1]), MINO_EDGE)])
    cel(draw, Limb(xform([(-down, 0.0), (up - 1.2, 0.0)], gx, gy, ang), [0.95, 0.95], cap=False), WOOD, sh=None, lw=0.7)
    cel(draw, P([(c - 1.9, -1.6), (c + 1.9, -1.6), (c + 1.9, 1.6), (c - 1.9, 1.6)]), GOLD, sh=None, lw=0.6)
    bx, by = xform([(-down, 0.0)], gx, gy, ang)[0]
    cel(draw, Ell(bx, by, 1.4, 1.4), GOLD, sh=None, lw=0.6)


def _mino_chest(r):
    """His torso in profile. The kit's is one depth whatever the build, and under a bull's head
    that was a stick: this one is a barrel chest, deepest under the shoulder, in to the belt."""
    cx, d = r.cx, r.d
    top, bot = r.sh_y - 2.4, r.hip_y + 0.5
    return Poly([(cx - d * 4.4, top), (cx + d * 3.4, top), (cx + d * 6.4, top + 2.4), (cx + d * 7.0, top + 5.2),
                 (cx + d * 5.0, r.waist_y + 0.8), (cx + d * 4.5, bot), (cx - d * 4.3, bot), (cx - d * 4.8, r.waist_y + 0.8),
                 (cx - d * 6.0, top + 4.6), (cx - d * 5.6, top + 1.6)])


def _mino_horn_side(draw, hx, hy, ry, d, far=False):
    """A horn in profile: up out of the temple and hooked forward to its point, as a bull's is.
    Both sweep the way he faces (the far one shows ahead of the near): one forward and one back
    was a V of antlers on top of his head."""
    if far:
        pts = bez((3.4, hy - ry + 3.0), (2.4, hy - ry - 5.0), (12.0, hy - ry - 5.2), 6)
    else:
        pts = bez((-4.2, hy - ry + 6.0), (-5.6, hy - ry - 5.4), (5.4, hy - ry - 6.8), 6)
    cel(draw, Limb([(hx + d * u, y) for (u, y) in pts], [2.5, 2.4, 2.2, 1.9, 1.4, 0.9, 0.3], cap0=True),
        shade(MINO_HORN, 0.9) if far else MINO_HORN, sh=(0.4, 0.6))


def _mino_tuft(draw, x, y, ang):
    """The dark tuft on the end of his tail, pointing along `ang`."""
    pts = [(-1.0, -0.9), (1.2, -1.9), (3.2, -1.5), (5.2, 0.0), (3.2, 1.5), (1.2, 1.9), (-1.0, 0.9)]
    cel(draw, Poly(xform(pts, x, y, ang)), MINO_DK, sh=(0.4, 0.4))


def draw_minotaur(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=1.36, head=1.0)
    d = r.d
    cx = r.cx
    ax_side = _hold_side(r)
    out = 3.4                                   # the axe hand is held off the hip: the axe stands clear of him
    sway = [0.0, 1.0, 0.0, -1.0][frame]
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    if d:
        # a bull's tail behind him, and the far arm
        tail = bez((cx - d * 4.2, r.hip_y - 2.0), (cx - d * (12.0 + sway * 0.6), r.hip_y - 3.4), (cx - d * (10.2 + sway), r.base_y - 7.4), 6)
        cel(draw, Limb(tail, [1.3, 1.2, 1.1, 1.0, 1.0, 0.9, 0.9]), MINO_FUR, sh=(0.4, 0.3))
        _mino_tuft(draw, tail[-1][0], tail[-1][1] - 0.6, 90 + d * (8 + sway * 8))
        rig_arms(r, draw, MINO_FUR, MINO_FUR, layer="far", hand_r=2.4)
    rig_legs(r, draw, MINO_FUR, MINO_HOOF, width=1.14)
    # the torso, a pale patch on the chest, and the baldric over his right shoulder
    if d:
        chest = _mino_chest(r)
        cel(draw, chest, MINO_FUR, sh=(1.5, 0.9),
            regions=[(Ell(cx + d * 5.4, r.sh_y + 3.4, 3.0, 4.4), MINO_LT), (Ell(hx, chest.pts[0][1] + 0.4, rx * 0.62, 2.3), shade(MINO_FUR))])
        strap = [(cx - d * 5.6, r.sh_y - 1.6), (cx + d * 6.6, r.waist_y + 0.2)]
    else:
        chest = _mino_kit_torso(r)
        rig_torso(r, draw, MINO_FUR)
        if not r.back:
            cel(draw, Ell(cx, r.sh_y + 3.4, 4.6, 4.0), MINO_LT, sh=None, line=False)
        k = -1 if not r.back else 1             # his right shoulder, whichever way he faces
        strap = [(cx + k * (r.sh_w - 1.2), r.sh_y - 2.6), (cx - k * (r.hip_w - 0.4), r.waist_y + 0.8)]
    clipped(draw, chest, lambda l: stroke(l, strap, 1.7, MINO_DK))
    rig_robe(r, draw, MINO_KILT, top=r.waist_y + 0.6, hem=r.hip_y + 4.0, flare=2.0, split=True, trim=GOLD)
    rig_belt(r, draw, LEATHER, buckle=GOLD)
    if r.back:
        # the tail, out from under the belt and down over the kilt
        tail = bez((cx, r.waist_y + 3.0), (cx + sway * 1.4, r.hip_y + 2.0), (cx + sway * 2.6, r.base_y - 6.6), 5)
        cel(draw, Limb(tail, [1.2, 1.1, 1.0, 1.0, 0.9, 0.9]), MINO_FUR, sh=(0.4, 0.3))
        _mino_tuft(draw, tail[-1][0], tail[-1][1] - 0.6, 90 - sway * 14)
    # the arms, a gold band on each wrist, and the labrys: in his left hand head-on, the near one in profile
    if d:
        # carried low at his side, the axe ahead of his chest and the haft back past his hip: upright in
        # the near hand it stood in front of him and hid him. The hand that carries it does not swing
        grip = (cx + d * 5.6, r.base_y - 11.8)
        s0 = r.shoulder(d)
        el = ((s0[0] + grip[0]) / 2.0 - d * 1.0, (s0[1] + grip[1]) / 2.0 + 0.8)
        wr = (grip[0] - d * 0.6, grip[1] - 0.9)
        aw = 2.2 * 1.3
        cel(draw, Limb([s0, el, wr], [aw, aw * 0.9, aw * 0.82]), MINO_FUR, sh=(0.8, 0.4))
        bands = [(el, wr)]
        ang = -90 + d * 58
    else:
        rig_arms(r, draw, MINO_FUR, MINO_FUR, layer="near", hands=False, sides=(-ax_side,))
        rig_arms(r, draw, MINO_FUR, MINO_FUR, layer="near", hands=False, sides=(ax_side,), out=out)
        bands = [arm_pts(r, side, 0.0, 0.0, out if side == ax_side else 0.0)[1:3] for side in (-1, 1)]
        grip = hand_at(r, ax_side, out=out)
        ang = -90 + ax_side * 4
    for (e, w) in bands:
        cel(draw, Ell((e[0] + w[0]) / 2, (e[1] + w[1]) / 2 + 0.6, 2.8, 1.2), GOLD, sh=None, lw=0.6)
    _mino_labrys(draw, grip[0], grip[1], ang, *((17.8, 8.9) if d else (16.3, 10.4)))
    # horns first: the head covers their roots
    if d:
        _mino_horn_side(draw, hx, hy, ry, d, far=True)
    else:
        for s in (-1, 1):
            _horn(draw, (hx + s * (rx - 2.4), hy - ry + 4.4), s, MINO_HORN, 1.1, 1.0, 2.8)
        # ears, sideways under the horns
        for s in (-1, 1):
            ex = hx + s * (rx + 0.6)
            ear = Poly([(ex - s * 1.0, hy - 3.0), (ex + s * 4.4, hy - 4.2), (ex + s * 3.4, hy - 1.0), (ex - s * 1.0, hy + 0.4)])
            cel(draw, ear, MINO_FUR, sh=(0.4, 0.4),
                regions=[] if r.back else [(Poly([(ex, hy - 2.4), (ex + s * 3.4, hy - 3.4), (ex + s * 2.6, hy - 1.4), (ex, hy - 0.6)]), MINO_LT)])
    cel(draw, Ell(hx, hy, rx, ry), MINO_FUR, sh=(1.2, 1.0), hi=(0.6, 0.6))
    if r.back:
        # a dark mane from between the horns down the back of his neck, ragged over the shoulders
        mane = [(-3.0, -ry - 0.2), (-1.6, -ry - 2.3), (0.0, -ry - 0.8), (1.6, -ry - 2.3), (3.0, -ry - 0.2), (3.3, -4.0), (2.7, 1.0),
                (3.5, 5.0), (5.6, 8.4), (4.7, 11.6), (2.8, 9.6), (1.0, 13.2), (-0.8, 9.8), (-2.9, 12.4), (-4.7, 9.2), (-5.6, 8.0), (-3.5, 5.0),
                (-2.7, 1.0), (-3.3, -4.0)]
        cel(draw, Poly([(hx + u, hy + v) for (u, v) in mane]), MINO_DK, sh=(0.9, 0.9))
    elif d:
        # a ragged ridge of mane down the back of the skull and the forelock on his brow, then the near ear and the near horn
        ridge = [(a, 1.1 if i % 2 else 0.2) for i, a in enumerate(range(258, 137, -10))]
        mane = [(math.cos(math.radians(a)) * (rx + 0.5 + k), math.sin(math.radians(a)) * (ry + 0.4 + k)) for (a, k) in ridge] + \
            [(-8.6, ry + 0.4), (-6.8, ry - 1.2), (-5.8, ry + 2.6), (-4.0, ry - 1.4)] + \
            [(math.cos(math.radians(a)) * (rx - 1.4 - (236 - a) * 0.022), math.sin(math.radians(a)) * (ry - 1.2 - (236 - a) * 0.018))
             for a in range(126, 259, 12)]
        cel(draw, Poly([(hx + d * u, hy + v) for (u, v) in mane]), MINO_DK, sh=(0.6, 0.7))
        lock = [(-3.0, -ry - 0.5), (0.4, -ry - 1.7), (3.4, -ry - 0.2), (6.4, -ry + 3.4), (3.8, -ry + 2.8), (2.6, -ry + 5.2), (0.6, -ry + 3.4),
                (-1.2, -ry + 4.6), (-2.6, -ry + 2.2)]
        cel(draw, Poly([(hx + d * u, hy + v) for (u, v) in lock]), MINO_DK, sh=(0.6, 0.6))
        ear = Poly([(hx - d * 5.0, hy - 2.6), (hx - d * 9.4, hy - 3.2), (hx - d * 11.6, hy - 0.8), (hx - d * 8.6, hy + 0.9), (hx - d * 5.2, hy + 0.6)])
        cel(draw, ear, MINO_FUR, sh=(0.4, 0.5),
            regions=[(Poly([(hx - d * 6.0, hy - 1.7), (hx - d * 9.2, hy - 2.1), (hx - d * 10.3, hy - 0.8), (hx - d * 8.4, hy + 0.1), (hx - d * 6.0, hy - 0.2)]), MINO_LT)])
        _mino_horn_side(draw, hx, hy, ry, d)
    else:
        lock = [(-4.4, -ry + 0.4), (-2.2, -ry - 1.8), (0.0, -ry - 0.6), (2.2, -ry - 1.8), (4.4, -ry + 0.4), (3.8, -ry + 3.6), (1.9, -ry + 2.6),
                (0.0, -ry + 5.2), (-1.9, -ry + 2.6), (-3.8, -ry + 3.6)]
        cel(draw, Poly([(hx + u, hy + v) for (u, v) in lock]), MINO_DK, sh=(0.6, 0.6))
    # muzzle, broad and pale, with a gold ring through it
    if d:
        mz = Ell(hx + d * 7.4, hy + 4.2, 6.0, 4.9)
        cel(draw, mz, MINO_LT, sh=(0.0, 0.8))
        Ell(hx + d * 11.2, hy + 3.0, 0.9, 0.8).draw(draw, fill=VOID)
        layer = draw.sub()
        Ell(hx + d * 11.4, hy + 6.0, 1.7, 1.9).draw(layer, fill=None, outline=GOLD, width=0.9)
        draw.merge(layer)
        eye_pair(draw, hx + d * 1.8, hx + d * 6.4, hy - 2.4, d, (220, 50, 40), skin=MINO_FUR, w=4.2, h=4.8)
    elif not r.back:
        mz = Ell(hx, hy + 5.0, 7.4, 5.4)
        cel(draw, mz, MINO_LT, sh=(0.8, 0.8))
        for s in (-1, 1):
            Ell(hx + s * 2.6, hy + 4.4, 1.1, 0.8).draw(draw, fill=VOID)
        layer = draw.sub()
        Ell(hx, hy + 7.4, 2.0, 2.0).draw(layer, fill=None, outline=GOLD, width=0.9)
        draw.merge(layer)
        eye_pair(draw, hx - 5.0, hx + 5.0, hy - 2.6, 0, (220, 50, 40), skin=MINO_FUR, w=4.2, h=4.8)
    if d:
        cel(draw, Ell(grip[0], grip[1], 2.5, 2.5), MINO_FUR, sh=(0.6, 0.6))
    else:
        for side in (-1, 1):
            rig_hand(r, draw, side, MINO_FUR, out=out if side == ax_side else 0.0, hand_r=2.4)


# ===================================================================
# MEDUSA (88) -- snakes for hair, a serpent's tail, eyes that petrify
# ===================================================================

MED_SKIN = (216, 234, 204)
MED_SNAKE = (60, 150, 88)       # a deep emerald: (96,174,92) was the grass's own colour, and the Serpent's
MED_CAP = (40, 104, 70)         # the scalp the snakes grow from, darker, so they show against it
MED_BELLY = (236, 222, 136)
MED_ROBE = (132, 70, 160)
MED_EYE = (255, 210, 40)


def _med_spline(pts, n=4):
    """A smooth curve through the given points (Catmull-Rom), n steps a span."""
    ext = [pts[0]] + list(pts) + [pts[-1]]
    out = []
    for i in range(1, len(ext) - 2):
        p0, p1, p2, p3 = ext[i - 1], ext[i], ext[i + 1], ext[i + 2]
        for k in range(n):
            t = k / float(n)
            out.append(tuple(0.5 * (2 * p1[j] + (p2[j] - p0[j]) * t + (2 * p0[j] - 5 * p1[j] + 4 * p2[j] - p3[j]) * t * t +
                                    (3 * p1[j] - p0[j] - 3 * p2[j] + p3[j]) * t * t * t) for j in (0, 1)))
    out.append(tuple(pts[-1]))
    return out


def _med_tube(pts, widths, n=4):
    """The curve through `pts` and a half-width for each of its points."""
    w = []
    for i in range(len(pts) - 1):
        w += [widths[i] + (widths[i + 1] - widths[i]) * k / float(n) for k in range(n)]
    return _med_spline(pts, n), w + [widths[-1]]


class _MedBelly(object):
    """The pale belly of a snake's body, as a region for `blob`: the band of `shapes` along the
    side that `off` points to (her front and the ground, in profile)."""

    def __init__(self, shapes, off):
        self.shapes, self.off = shapes, off

    def draw(self, layer, fill=None, outline=None, width=1):
        for shp in self.shapes:
            shp.draw(layer, fill=fill)
        for shp in self.shapes:
            shp.shift(-self.off[0], -self.off[1]).draw(layer, fill=(0, 0, 0, 0))


def _med_snake(draw, pts, w=2.4):
    """One snake of her hair: a thick body through `pts` ending in a head wider than its neck,
    with a red eye. One mass, so there is no seam where the head begins. Nine straight stubs
    with a bead on the end were a pincushion at 77px; a snake needs its curve and its head."""
    c = _med_spline(pts)
    n = len(c) - 1
    (x1, y1), (x2, y2) = c[-2], c[-1]
    ang = math.degrees(math.atan2(y2 - y1, x2 - x1))
    k = w / 1.9
    head = Poly(xform([(-1.8, -1.3), (0.4, -2.3), (2.8, -1.7), (4.4, 0.0), (2.8, 1.7), (0.4, 2.3), (-1.8, 1.3)], x2, y2, ang, k))
    blob(draw, [Limb(c, [w * (1.0 - 0.2 * i / n) for i in range(n + 1)], cap=False), head], MED_SNAKE, sh=(0.5, 0.6))
    e1, e2 = xform([(1.5, -0.9), (1.5, 0.9)], x2, y2, ang, k)
    ex, ey = e1 if e1[1] <= e2[1] else e2
    Ell(ex, ey, 0.85 * k, 0.85 * k).draw(draw, fill=(250, 70, 60))


def draw_medusa(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=0.94, bob=0)
    d = r.d
    sway = [0.0, 1.0, 0.0, -1.0][frame]
    cx, base = r.cx, r.base_y
    top = r.waist_y + 2.4       # under the belt, where woman turns to serpent
    if d:
        # in profile she stands on the bend of her tail and the rest of it trails behind her,
        # a hump travelling down it as she goes
        h1, h2 = [0.5, 1.9, 0.5, 0.0][frame], [0.5, 0.0, 0.5, 1.9][frame]

        def P(u, v):
            return (cx + d * u, base - v)
        tail, tw = _med_tube([P(0.6, 4.4), P(-3.6, 4.0), P(-9.0, 3.4 + h1), P(-14.6, 2.7 + h2), P(-19.2, 2.0), P(-22.0, 5.6 + sway * 0.8)],
                             [4.4, 4.0, 3.4, 2.7, 1.8, 0.5])
        shapes = [Limb([(cx + d * 0.2, top), P(1.0, 7.6), P(0.8, 4.8)], [5.0, 4.8, 4.6]), Ell(cx + d * 0.7, base - 4.6, 4.6, 4.6),
                  Limb(tail, tw)]
        blob(draw, shapes, MED_SNAKE, sh=(1.0, 0.8), regions=[(_MedBelly(shapes, (d * 2.2, 1.9)), MED_BELLY)])
    else:
        # head-on she rises out of one coil, its tip curled up beside her (on her own left)
        k = 1 if not r.back else -1
        tx = cx + k * 10.6
        tip, tw = _med_tube([(tx - k * 3.0, base - 3.2), (tx + k * 3.6, base - 3.8), (tx + k * 5.8, base - 8.6 + sway * 0.5),
                             (tx + k * 3.4 + sway * 0.9, base - 13.4)], [2.9, 2.5, 1.8, 0.5])
        cel(draw, Limb(tip, tw), MED_SNAKE, sh=(0.5, 0.5))
        ccx = cx + sway * 0.7
        regs = []
        if not r.back:
            regs = [(Poly(arc_pts(ccx, base - 3.6, 11.0, 2.7, 15, 165, 12)), MED_BELLY),
                    (Limb([(cx, top), (cx + sway * 0.4, base - 4.6)], [3.3, 3.0], cap=False), MED_BELLY)]
        blob(draw, [Ell(ccx, base - 3.6, 12.4, 3.9), Limb([(cx, top), (cx + sway * 0.4, base - 4.6)], [r.hip_w - 0.5, 5.4], cap=False)],
             MED_SNAKE, sh=(1.0, 0.9), regions=regs)
        if not r.back:
            for v in (7.2, 9.4):
                stroke(draw, [(cx - 2.6, base - v), (cx + 2.6, base - v)], 0.5, shade(MED_BELLY, 1.6))
    # a purple chiton to the waist, a gold belt, gold on her arms
    rig_torso(r, draw, MED_ROBE, bottom=r.waist_y + 3.0)
    rig_belt(r, draw, GOLD, buckle=MED_SNAKE)
    if d:
        rig_arms(r, draw, MED_SKIN, MED_SKIN, layer="far")
    rig_arms(r, draw, MED_SKIN, MED_SKIN, layer="near", reach=0.3)
    for side in _sides(r):
        _, e, w, h = arm_pts(r, side, 0.3)
        cel(draw, Ell(e[0], e[1], 2.4, 1.2), GOLD, sh=None, lw=0.6)
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    m = -1 if d < 0 else 1

    def snake(pts, i):
        g = math.sin(frame * math.pi / 2.0 + i * 1.9)
        q = [(hx + m * u, hy + v) for (u, v) in pts]
        q = [q[0]] + [(x + g * (0.9 if j % 2 == 0 else -0.9), y) for j, (x, y) in enumerate(q[1:-1])] + [(q[-1][0] - g * 0.7, q[-1][1])]
        _med_snake(draw, q)

    # few snakes, each big enough to be one: those behind the head first, the face kept clear
    if d:
        behind = ([(-1.0, -9.5), (1.2, -13.6), (4.6, -15.4), (7.6, -14.2)],
                  [(-9.0, -6.0), (-14.0, -8.4), (-17.2, -6.0), (-16.6, -2.4)],
                  [(-10.0, 1.0), (-14.6, 3.5), (-15.0, 8.0), (-12.4, 11.4)])
        over = ([(-4.0, -3.5), (-7.0, 1.5), (-5.0, 6.6), (-7.6, 9.6)],)
    else:
        behind = ([(0.5, -9.5), (2.2, -12.4), (-0.4, -14.4), (0.6, -16.6)],
                  [(-7.0, -8.0), (-11.8, -10.2), (-14.8, -12.6), (-16.4, -15.6)],
                  [(7.0, -8.0), (11.8, -10.6), (15.4, -12.2), (17.6, -14.6)],
                  [(-11.0, -2.0), (-15.4, 0.5), (-16.2, 5.0), (-14.2, 8.6), (-16.6, 10.8)],
                  [(11.0, -2.0), (15.4, 0.5), (16.2, 5.0), (14.2, 8.6), (16.6, 10.8)])
        over = () if not r.back else ([(-3.5, -4.0), (-6.5, 2.0), (-3.6, 7.6), (-6.4, 11.6)], [(5.0, -1.0), (8.0, 3.6), (6.2, 8.2)])
    for i, pts in enumerate(behind):
        snake(pts, i)
    head_skull(r, draw, MED_SKIN, ears=False)
    if r.back:
        # the whole back of her head is scalp: a hairline here left a row of pale teeth on her nape
        cel(draw, Ell(hx, hy - 0.2, rx + 1.0, ry + 1.0), MED_CAP, sh=(1.1, 1.3))
    else:
        head_face(r, draw, MED_SKIN, iris=MED_EYE, mood="sharp", mouth="smirk", blush=False, brows=True, brow_color=MED_CAP)
        rig_hair(r, draw, MED_CAP, dict(vol=1.2, fringe=((-0.6, 3.6), (-0.2, 4.4), (0.2, 4.4), (0.6, 3.6)), sweep=0.0, side=4.0, back=7.0),
                 skin=MED_SKIN)
        # a gold tiara on the brow
        if d:
            cel(draw, Poly([(hx + d * 11.6, hy - 3.2), (hx + d * 8.8, hy - 9.0), (hx + d * 3.6, hy - 8.4), (hx + d * 3.6, hy - 6.4),
                            (hx + d * 7.8, hy - 6.8), (hx + d * 9.6, hy - 3.0)]), GOLD, sh=(0.4, 0.4), lw=0.8)
        else:
            cel(draw, Poly([(hx - 7.2, hy - ry + 4.6), (hx - 3.4, hy - ry + 1.8), (hx, hy - ry - 2.8), (hx + 3.4, hy - ry + 1.8),
                            (hx + 7.2, hy - ry + 4.6), (hx, hy - ry + 3.0)]), GOLD, sh=(0.4, 0.4), lw=0.8)
            gem(draw, hx, hy - ry + 0.8, 1.6, (226, 70, 110))
    for i, pts in enumerate(over):
        snake(pts, i + 5)


# ===================================================================
# CERBERUS (89) -- three heads, spiked collars, fire in its fur
# ===================================================================

CER = (108, 70, 84)
CER_LT = (196, 142, 124)        # muzzles and paws
CER_LAVA = (255, 118, 44)
CER_EYE = (255, 206, 70)
CER_COLLAR = (176, 182, 200)    # steel: a dark collar on dark fur was no collar
CER_FIRE = (CER_LAVA, (255, 190, 80), (255, 240, 170))


def _cer_head(draw, x, y, d, k=1.0, ang=0.0, back=False, far=False):
    """One of the three heads, centred on (x, y) and turned `ang` degrees (in profile nose
    down, head-on leaning that way). The far one is a tone darker: three heads in one maroon,
    stacked on one spot, were one lump."""
    col = CER if not far else shade(CER, 1.1)
    if d:
        def T(pts):
            return [(x + d * px, y + py) for (px, py) in xform(pts, 0.0, 0.0, ang, k)]
        cel(draw, Poly(T([(-4.0, -3.0), (-3.2, -9.4), (-0.8, -4.0)])), shade(col, 0.7), sh=None)
        blob(draw, [Poly(T(arc_pts(0.0, 0.0, 6.2, 5.6, 0, 360)[:-1])), Poly(T([(-1.6, -3.4), (0.2, -9.8), (2.4, -3.6)]))], col, sh=(0.8, 0.8))
        # its collar, round the neck just behind the jaw, nape to throat: a spike at each end and
        # studs between (three heads' worth of long spikes ran together into one spiny strap)
        for spike in ([(-6.2, -3.0), (-4.8, -3.8), (-6.4, -5.4)], [(-4.6, 5.0), (-3.2, 4.6), (-4.2, 6.6)]):
            Poly(T(spike)).draw(draw, fill=(236, 238, 244))
        cel(draw, Poly(T([(-6.4, -2.9), (-4.4, -3.9), (-2.8, 4.4), (-4.8, 5.2)])), CER_COLLAR if not far else shade(CER_COLLAR, 1.0), sh=None, lw=0.7)
        for (su, sv) in ((-5.0, -1.2), (-4.3, 2.0)):
            Poly(T(arc_pts(su, sv, 0.7, 0.7, 0, 360)[:-1])).draw(draw, fill=(240, 242, 248))
        cel(draw, Poly(T(arc_pts(5.4, 1.7, 4.2, 2.9, 0, 360)[:-1])), CER_LT if not far else shade(CER_LT, 1.0), sh=(0.0, 0.6))
        Poly(T(arc_pts(8.8, 0.5, 1.2, 1.0, 0, 360)[:-1])).draw(draw, fill=VOID)
        Poly(T([(3.2, 3.3), (8.4, 3.0), (7.2, 5.0), (3.6, 4.9)])).draw(draw, fill=(150, 30, 40))
        Poly(T([(6.0, 3.1), (7.2, 3.1), (6.6, 4.9)])).draw(draw, fill=TOOTH)
        Poly(T([(0.4, -2.8), (3.8, -1.4), (3.2, 0.4), (0.2, -0.6)])).draw(draw, fill=CER_EYE)
        Poly(T(arc_pts(2.0, -1.0, 0.6, 0.6, 0, 360)[:-1])).draw(draw, fill=(255, 255, 230))
        return

    def T(pts):
        return xform(pts, x, y, ang, k)
    ears = [Poly(T([(s * 4.4 - 2.2, -3.4), (s * 5.4, -9.6), (s * 4.4 + 2.2, -3.4)])) for s in (-1, 1)]
    blob(draw, [Poly(T(arc_pts(0.0, 0.0, 6.2, 5.6, 0, 360)[:-1]))] + ears, col, sh=(0.8, 0.8))
    if back:
        return
    cel(draw, Poly(T(arc_pts(0.0, 2.4, 3.9, 3.0, 0, 360)[:-1])), CER_LT, sh=(0.4, 0.4))
    Poly(T(arc_pts(0.0, 1.0, 1.4, 1.0, 0, 360)[:-1])).draw(draw, fill=VOID)
    Poly(T([(-2.6, 3.6), (2.6, 3.6), (1.6, 5.3), (-1.6, 5.3)])).draw(draw, fill=(150, 30, 40))
    for s in (-1, 1):
        Poly(T([(s * 1.6 - 0.5, 3.6), (s * 1.6 + 0.5, 3.6), (s * 1.6, 5.1)])).draw(draw, fill=TOOTH)
        Poly(T([(s * 1.2, -0.6), (s * 2.4, -2.4), (s * 4.8, -2.7), (s * 4.3, -0.6)])).draw(draw, fill=CER_EYE)
        Poly(T(arc_pts(s * 2.9, -1.5, 0.5, 0.5, 0, 360)[:-1])).draw(draw, fill=(255, 255, 230))


def _cer_collar(draw, hx, hy, rx_, ry_, k, w, far=False):
    """The collar behind a head seen head-on or from behind, centred on (hx, hy), its neck running
    back to (rx_, ry_): a band of steel across the neck, a spike standing out of each end. Three
    bright bands are what make three necks of one dark mass. Drawn before its head, which covers
    its inner edge (in profile the head draws its own, round the neck behind the jaw)."""
    ln = math.hypot(hx - rx_, hy - ry_) or 1.0
    ux, uy = (hx - rx_) / ln, (hy - ry_) / ln
    px, py = -uy, ux
    mx, my = hx - ux * (5.4 * k + 0.5), hy - uy * (5.4 * k + 0.5)
    for sgn in (-1, 1):
        bx, by = mx + px * sgn * (w + 0.2), my + py * sgn * (w + 0.2)
        Poly([(bx - ux * 0.9, by - uy * 0.9), (bx + ux * 0.9, by + uy * 0.9), (bx + px * sgn * 2.0, by + py * sgn * 2.0)]).draw(
            draw, fill=(236, 238, 244))
    cel(draw, Poly([(mx + ux * a + px * b, my + uy * a + py * b) for (a, b) in
                    ((-1.4, -w - 0.5), (1.4, -w - 0.5), (1.4, w + 0.5), (-1.4, w + 0.5))]),
        CER_COLLAR if not far else shade(CER_COLLAR, 1.0), sh=None, lw=0.7)


def _cer_leg(x0, y0, base, d, swing, hind=False):
    """Joints of one leg in profile, from its top to the paw, trotting."""
    fx = x0 + d * (swing * 2.8 + (0.2 if hind else 0.4))
    fy = base - 1.6 * max(0.0, swing) - 1.6
    if hind:
        return [(x0, y0), (x0 + d * (swing * 1.0 - 1.7), (y0 + fy) / 2.0 + 0.4), (fx, fy)]
    return [(x0, y0), (x0 + d * (swing * 1.6 + 0.2), (y0 + fy) / 2.0), (fx, fy)]


def _cer_paw(draw, x, y, d=0, far=False, claws=True):
    """A paw, its sole on the ground 1.6 under (x, y), the claws lying on the ground in front
    of it: the kit's hang 1.5 units under the ground line."""
    if claws and not far:
        if d:
            for q in (0.0, 1.0):
                Poly([(x + d * (1.8 + q), y + 0.1), (x + d * (2.0 + q), y + 1.5), (x + d * (4.2 - q * 0.4), y + 1.6)]).draw(draw, fill=TOOTH)
        else:
            for q in (-1.4, 0.0, 1.4):
                Poly([(x + q - 0.55, y + 0.9), (x + q + 0.55, y + 0.9), (x + q, y + 2.3)]).draw(draw, fill=TOOTH)
    cel(draw, Ell(x + d * 0.8, y, 2.6, 1.7), CER_LT if not far else shade(CER_LT, 1.4), sh=(0.0, 0.6))


def _cer_crack(draw, pts):
    """Fire showing through the fur: wide enough to see (the hairlines were not), hot in the middle."""
    stroke(draw, pts, 1.5, CER_LAVA)
    stroke(draw, pts, 0.55, (255, 226, 130))


def draw_cerberus(draw, ox, oy, direction, frame):
    d, back, base, cx, ph = creature_setup(ox, oy, direction, frame)
    sway = [0.0, 1.0, 0.0, -1.0][frame]
    lick = frame % 2
    dark = shade(CER, 1.1)
    if d:
        # a fire-tipped tail, and fire along its back
        cel(draw, Limb([(cx - d * 9.0, base - 15.5), (cx - d * 13.6, base - 18.0 + sway), (cx - d * 14.6, base - 22.4)], [2.0, 1.6, 1.0]), CER, sh=None)
        flame(draw, cx - d * 14.6, base - 20.8, 5.2, 8.0, CER_FIRE, sway=sway)
        flame(draw, cx - d * 5.2, base - 18.2, 4.4, 6.0 + lick, CER_FIRE, sway=-d * 1.0 + sway * 0.5)
        flame(draw, cx - d * 0.4, base - 19.0, 5.0, 7.2 - lick, CER_FIRE, sway=-d * 1.0 - sway * 0.5, flip=-1.0)
        far_f = _cer_leg(cx + d * 3.6, base - 11.0, base, d, -ph)
        far_h = _cer_leg(cx - d * 5.2, base - 11.6, base, d, ph, hind=True)
        for j in (far_h, far_f):
            cel(draw, Limb(j, [2.4, 2.0, 1.8]), dark, sh=None)
            _cer_paw(draw, j[-1][0], j[-1][1], d, far=True)
        # three heads fanned out, so the silhouette has three muzzles: the far one raised, the
        # middle one level, the near one low
        root = (cx + d * 5.4, base - 17.0)
        far, mid, near = (((cx + d * 6.6, base - 29.6), -26.0, 0.84), ((cx + d * 12.4, base - 23.4), -4.0, 0.94),
                          ((cx + d * 10.8, base - 15.0), 22.0, 0.94))
        cel(draw, Limb([root, far[0]], [3.2, 2.8], cap=False), dark, sh=None)
        _cer_head(draw, far[0][0], far[0][1], d, far[2], far[1], far=True)
        near_f = _cer_leg(cx + d * 6.0, base - 11.4, base, d, ph)
        near_h = _cer_leg(cx - d * 7.2, base - 12.0, base, d, -ph, hind=True)
        body = Poly(rot_pts(arc_pts(cx - d * 1.0, base - 14.0, 11.0, 6.8, 0, 360)[:-1], cx, base - 14.0, -d * 8))
        blob(draw, [body, Ell(cx + d * 5.6, base - 14.6, 5.4, 6.6), Limb(near_f, [2.7, 2.2, 1.9]), Limb(near_h, [3.0, 2.3, 1.9]),
                    Limb([root, mid[0]], [3.4, 3.0], cap=False), Limb([root, near[0]], [3.4, 3.0], cap=False)], CER, sh=(1.2, 1.2))
        for j in (near_h, near_f):
            _cer_paw(draw, j[-1][0], j[-1][1], d)
        _cer_crack(draw, [(cx - d * 6.8, base - 16.6), (cx - d * 4.8, base - 14.2), (cx - d * 5.8, base - 12.4), (cx - d * 3.8, base - 10.4)])
        _cer_crack(draw, [(cx + d * 0.2, base - 17.0), (cx + d * 1.8, base - 14.6), (cx + d * 0.6, base - 12.8)])
        for (hp, ang, k) in (mid, near):
            _cer_head(draw, hp[0], hp[1], d, k, ang)
        return
    if back:
        # leaving: the three heads beyond the rump, the tail and its flame nearest
        for (u, k) in ((-8.8, 0.84), (8.8, 0.84), (0.0, 1.0)):
            hx_, hy_ = cx + u, base - 28.6 + (3.0 if u else 0.0)
            rx_, ry_ = cx + u * 0.45, base - 16.0
            cel(draw, Limb([(rx_, ry_), (hx_, hy_)], [3.2, 3.0], cap=False), CER, sh=None)
            _cer_collar(draw, hx_, hy_, hx_ - u * 0.12, hy_ + 10.0, k, 3.2)
            _cer_head(draw, hx_, hy_, 0, k, u * 1.0, back=True)
        shapes = [Ell(cx, base - 14.0, 10.0, 7.4)]
        feet = []
        for s in (-1, 1):
            lift = 1.0 if ph * s < 0 else 0.0
            feet.append((cx + s * 5.6, base - 1.6 - lift))
            shapes.append(Limb([(cx + s * 5.4, base - 10.0), feet[-1]], [2.6, 2.3]))
        blob(draw, shapes, CER, sh=(1.2, 1.2))
        for (fx, fy) in feet:
            _cer_paw(draw, fx, fy, claws=False)
        _cer_crack(draw, [(cx - 5.4, base - 17.4), (cx - 3.6, base - 15.0), (cx - 4.8, base - 12.6)])
        _cer_crack(draw, [(cx + 5.6, base - 13.4), (cx + 4.0, base - 11.2), (cx + 5.0, base - 9.4)])
        cel(draw, Limb([(cx, base - 13.0), (cx + 3.6 + sway, base - 18.4), (cx + 5.2 + sway * 1.6, base - 23.4)], [2.0, 1.6, 1.0]), CER, sh=None)
        flame(draw, cx + 5.2 + sway * 1.6, base - 21.8, 5.2, 8.0, CER_FIRE, sway=sway)
        return
    # head-on: three heads over a burly chest, fire licking up between them
    cel(draw, Limb([(cx + 6.0, base - 11.0), (cx + 15.6, base - 12.6 + sway), (cx + 18.4, base - 18.6)], [2.0, 1.6, 1.0]), dark, sh=None)
    flame(draw, cx + 18.4, base - 17.0, 5.2, 8.0 - lick, CER_FIRE, sway=sway)
    for s in (-1, 1):
        cel(draw, Limb([(cx + s * 7.6, base - 8.0), (cx + s * 7.8, base - 2.6)], [2.0, 1.8]), dark, sh=None)
        _cer_paw(draw, cx + s * 7.8, base - 2.2, far=True)
    heads = ((-10.6, -22.4, 0.84), (10.6, -22.4, 0.84), (0.0, -27.6, 1.06))
    shapes = [Ell(cx, base - 12.0, 9.6, 6.8)]
    feet = []
    for s in (-1, 1):
        lift = 1.0 if ph * s < 0 else 0.0
        feet.append((cx + s * 4.0, base - 1.6 - lift))
        shapes.append(Limb([(cx + s * 4.0, base - 11.0), feet[-1]], [2.7, 2.3]))
    for (u, v, k) in heads:
        shapes.append(Limb([(cx + u * 0.35, base - 15.0), (cx + u, base + v)], [3.4, 3.0], cap=False))
    blob(draw, shapes, CER, sh=(1.2, 1.0))
    for (fx, fy) in feet:
        _cer_paw(draw, fx, fy)
    _cer_crack(draw, [(cx - 4.4, base - 16.4), (cx - 2.4, base - 14.0), (cx - 3.6, base - 12.0), (cx - 1.8, base - 9.8)])
    _cer_crack(draw, [(cx + 4.6, base - 13.6), (cx + 3.0, base - 11.6), (cx + 4.0, base - 10.0)])
    for (u, v, k) in heads:
        _cer_collar(draw, cx + u, base + v, cx + u * 0.88, base + v + 10.0, k, 3.2)
        _cer_head(draw, cx + u, base + v, 0, k, u * 1.0)


# ===================================================================
# CENTAUR (90) -- a chestnut horse body, a ranger's torso, a bow
# ===================================================================

from sprite_base import torso_shape as _cen_torso  # noqa: E402  (the strap is clipped to it)

HORSE = (160, 92, 56)           # a liver chestnut: darker and redder than the boardwalk she stands on
HORSE_DK = (98, 58, 42)         # the tail, and her own hair
HORSE_SOCK = (246, 240, 228)
HORSE_HOOF = (70, 54, 56)
CEN_TUNIC = (88, 150, 94)
CEN_BOW = (240, 208, 132)       # pale yew: a brown bow was lost against a brown horse
CEN_LAUREL = (126, 192, 88)
CEN_FLETCH = (236, 84, 72)
CEN_HAIR = dict(vol=1.9, fringe=((-0.72, 4.0), (-0.28, 5.2), (0.18, 5.0), (0.62, 4.2)), sweep=0.7, side=8.0, back=8.6, lock=3.4)


def _cen_leg(x0, y0, base, d, swing, hind=False):
    """Joints of one leg in profile, from its top at (x0, y0) to the fetlock: the hoof swung
    `swing` (-1 back .. +1 forward) and lifted as it comes forward, over a knee that folds
    forward or a hock that sits back."""
    lift = 2.2 * max(0.0, swing)
    fx = x0 + d * (swing * 3.6 + (-0.4 if hind else 0.3))
    fy = base - lift - 1.8
    if hind:
        kx, ky = x0 + d * (swing * 1.2 - 1.9), (y0 + fy) / 2.0 + 0.6
    else:
        kx, ky = x0 + d * (swing * 2.6 + 0.4 + 0.9 * max(0.0, swing)), (y0 + fy) / 2.0 - 0.6
    return [(x0, y0), (kx, ky), (fx, fy)]


def _cen_foot(draw, joints, d=0, far=False, sock=True):
    """A white sock and a dark hoof on the end of a leg, the sole 2 units under its last joint."""
    (kx, ky), (fx, fy) = joints[-2], joints[-1]
    t = min(1.0, 3.4 / (math.hypot(fx - kx, fy - ky) or 1.0))
    top = (fx + (kx - fx) * t, fy + (ky - fy) * t)
    if sock:
        cel(draw, Limb([top, (fx, fy)], [1.9, 1.8], cap=False), HORSE_SOCK if not far else shade(HORSE_SOCK, 1.2), sh=(0.6, 0.0), lw=0.7)
    cel(draw, Poly([(fx - 1.8 - d * 0.1, fy - 1.1), (fx + 1.8 + d * 0.2, fy - 1.1), (fx + 2.3 + d * 0.8, fy + 2.0), (fx - 2.3 + d * 0.2, fy + 2.0)]),
        HORSE_HOOF if not far else shade(HORSE_HOOF, 0.6), sh=None, lw=0.7)


def _cen_bow(draw, gx, gy, k, height=24.0, tilt=0.0):
    """A recurve bow gripped at (gx, gy), its belly toward k and its string on the archer's
    side: a thick limb in pale yew, so it is the bow that reads and not only its string."""
    h2 = height / 2.0
    half = [(0.0, 0.0), (-0.5, -h2 * 0.34), (-2.4, -h2 * 0.7), (-4.0, -h2 * 0.93), (-3.0, -h2)]
    pts = half[::-1] + [(x, -y) for (x, y) in half[1:]]
    ang, flip = (tilt, 1.0) if k > 0 else (180.0 - tilt, -1.0)
    stroke(draw, xform([(-3.2, -h2 + 0.3), (-3.2, h2 - 0.3)], gx, gy, ang, 1.0, flip), 0.55, (242, 242, 234))
    cel(draw, Limb(xform(pts, gx, gy, ang, 1.0, flip), [0.6, 0.85, 1.15, 1.4, 1.5, 1.4, 1.15, 0.85, 0.6], cap=True, cap0=True),
        CEN_BOW, sh=None, lw=0.7)
    cel(draw, Limb(xform([(0.0, -1.9), (0.0, 1.9)], gx, gy, ang, 1.0, flip), [1.75, 1.75]), LEATHER, sh=None, lw=0.6)


def _cen_leaf(x, y, ang, length=5.6, width=3.4):
    w = width * 0.5
    local = bez((0.0, 0.0), (length * 0.3, -w * 1.7), (length, 0.0), 5) + bez((length, 0.0), (length * 0.3, w * 1.7), (0.0, 0.0), 5)[1:-1]
    return Poly(xform(local, x, y, ang))


def _cen_wreath(r, draw):
    """The laurel wreath: two sprays of leaves round the head, their tips toward the brow (from
    behind, toward where it is tied), drawn as one mass so it is a band and not five specks."""
    hx, hy, ry, d = r.hx, r.head_cy, r.head_ry, r.d
    leaves = []
    if d:
        for i, u in enumerate((-8.8, -5.0, -1.2, 2.6)):
            leaves.append(_cen_leaf(hx + d * u, hy - ry + 4.6 - i * 0.5, -12.0 if d > 0 else 192.0))
    else:
        y0 = hy - ry + (3.0 if not r.back else 5.0)
        for s in (-1, 1):
            for u in (10.6, 6.8, 3.0):
                leaves.append(_cen_leaf(hx + s * u, y0 + (1.0 - (u / 10.6) ** 2) * 1.2, 200.0 if s > 0 else -20.0))
    blob(draw, leaves, CEN_LAUREL, sh=(0.5, 0.7), lw=0.6, line_color=shade(CEN_LAUREL, 2.6))


def _cen_quiver(draw, x, y, ang):
    """A leather quiver, its mouth at (x, y) and its foot along `ang`, three fletched arrows in it."""
    for off, ln in ((-1.3, 4.2), (0.0, 5.4), (1.3, 3.8)):
        stroke(draw, xform([(0.5, off * 0.8), (-ln, off)], x, y, ang), 0.6, WOOD)
        cel(draw, Poly(xform([(-ln - 0.8, off - 1.0), (-ln + 1.8, off - 1.0), (-ln + 2.6, off), (-ln + 1.8, off + 1.0), (-ln - 0.8, off + 1.0)],
                             x, y, ang)), CEN_FLETCH, sh=None, lw=0.5)
    cel(draw, Poly(xform([(0.0, -2.4), (9.0, -1.8), (10.0, 0.0), (9.0, 1.8), (0.0, 2.4)], x, y, ang)), LEATHER, sh=(0.6, 0.6))
    cel(draw, Poly(xform([(-0.5, -2.6), (1.3, -2.5), (1.3, 2.5), (-0.5, 2.6)], x, y, ang)), GOLD, sh=None, lw=0.6)


def _cen_arm(r, draw, s, reach=0.0, lift=0.0, out=0.0):
    sh_, el, wr, hd = arm_pts(r, s, reach, lift, out)
    aw = 2.2 * min(r.build, 1.3)
    cel(draw, Limb([sh_, el, wr], [aw, aw * 0.9, aw * 0.82]), SKIN_TAN, sh=(0.8, 0.4))
    return hd


def draw_centaur(draw, ox, oy, direction, frame):
    d, back, base, cx, ph = creature_setup(ox, oy, direction, frame)
    sway = [0.0, 1.0, 0.0, -1.0][frame]
    # the rider's torso rises from the horse's withers: a rig raised to meet it
    r = rig(ox + (d * 7.0 if d else 0.0), oy - 7.0, direction, frame, build=0.96, head=0.92, step=0)
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    side = d if d else (1 if not back else -1)      # the bow hand
    far_coat = shade(HORSE, 1.1)
    t_bot = r.waist_y + 2.8                         # the tunic ends in the belt, where woman turns to horse

    def tunic():
        rig_torso(r, draw, CEN_TUNIC, bottom=t_bot)
        if d:
            strap = [(r.cx - d * 4.6, r.sh_y - 1.6), (r.cx + d * 5.0, r.waist_y + 0.4)]
        else:
            k = -1 if not back else 1               # over her right shoulder, whichever way she faces
            strap = [(r.cx + k * (r.sh_w - 1.4), r.sh_y - 2.2), (r.cx - k * (r.hip_w - 0.4), r.waist_y + 0.8)]
        clipped(draw, _cen_torso(r, bottom=t_bot), lambda l: stroke(l, strap, 1.5, LEATHER))
        rig_belt(r, draw, LEATHER, buckle=GOLD)

    def arms():
        for s in _sides(r):
            if s == side:
                hd = _cen_arm(r, draw, s, 0.4, 0.0, 4.6 if d else 4.0)
                _cen_bow(draw, hd[0], hd[1], d if d else s, 23.0, tilt=0.0 if d else 6.0)
                rig_hand(r, draw, s, SKIN_TAN, reach=0.4, out=4.6 if d else 4.0)
            else:
                _cen_arm(r, draw, s, 0.0, 2.2)
                rig_hand(r, draw, s, SKIN_TAN, lift=2.2)

    if d:
        tail = Limb(bez((cx - d * 12.6, base - 19.4), (cx - d * 21.0, base - 21.0 + sway), (cx - d * (17.6 + sway * 0.8), base - 5.6), 7),
                    [1.6, 2.2, 2.8, 3.2, 3.2, 2.6, 1.8, 0.6])
        cel(draw, tail, HORSE_DK, sh=(0.6, 0.6))
        # a trot: the diagonal pairs swing together
        far_f = _cen_leg(cx + d * 3.6, base - 12.0, base, d, -ph)
        far_h = _cen_leg(cx - d * 5.4, base - 12.6, base, d, ph, hind=True)
        for j in (far_h, far_f):
            cel(draw, Limb(j, [2.4, 1.9, 1.6]), far_coat, sh=None)
            _cen_foot(draw, j, d, far=True)
        # her ponytail, a second horse's tail
        cel(draw, Limb(bez((hx - d * (rx - 1.0), hy - ry * 0.3), (hx - d * (rx + 8.5), hy - 2.0 + sway * 0.6),
                           (hx - d * (rx + 5.0), hy + 11.0 - sway), 6), [2.2, 2.8, 3.2, 3.2, 2.6, 1.8, 0.6]), HORSE_DK, sh=(0.6, 0.6))
        # chest, barrel, rump and the near legs: one mass
        near_f = _cen_leg(cx + d * 7.2, base - 12.6, base, d, ph)
        near_h = _cen_leg(cx - d * 9.4, base - 13.0, base, d, -ph, hind=True)
        blob(draw, [Ell(cx - d * 1.5, base - 15.2, 10.2, 6.0), Ell(cx + d * 6.6, base - 16.4, 5.0, 6.8), Ell(cx - d * 8.6, base - 16.2, 5.4, 6.4),
                    Limb(near_f, [2.7, 2.0, 1.7]), Limb(near_h, [3.1, 2.2, 1.7])], HORSE, sh=(1.2, 1.2))
        for j in (near_h, near_f):
            _cen_foot(draw, j, d)
        tunic()
        arms()
    elif back:
        # leaving: the forelegs far off between the hind ones, the rider beyond the rump
        for s in (-1, 1):
            j = [(cx + s * 3.0, base - 12.0), (cx + s * 3.2, base - 5.6 - (1.4 if ph * s > 0 else 0.0))]
            cel(draw, Limb(j, [2.0, 1.6]), far_coat, sh=None)
            _cen_foot(draw, j, far=True, sock=False)
        tunic()
        shapes = [Ell(cx, base - 13.8, 11.0, 7.4), Poly([(cx - 5.6, base - 22.6), (cx + 5.6, base - 22.6), (cx + 9.8, base - 16.0), (cx - 9.8, base - 16.0)])]
        feet = []
        for s in (-1, 1):
            lift = 2.2 if ph * s < 0 else 0.0
            feet.append([(cx + s * 6.8, base - 11.0), (cx + s * 7.6, base - 6.4 - lift * 0.5), (cx + s * 7.0, base - 1.8 - lift)])
            shapes.append(Limb(feet[-1], [2.9, 2.1, 1.8]))
        blob(draw, shapes, HORSE, sh=(1.2, 1.2))
        for j in feet:
            _cen_foot(draw, j)
        cel(draw, Limb(bez((cx, base - 19.5), (cx + sway * 2.6, base - 13.0), (cx + sway * 3.4, base - 4.4), 6), [1.8, 2.6, 3.2, 3.2, 2.8, 2.0, 0.7]),
            HORSE_DK, sh=(0.6, 0.6))
        arms()
    else:
        # coming on: the tail out to one side, the barrel and the hind legs behind, darker and
        # shorter; the chest and the forelegs in front
        cel(draw, Limb(bez((cx - 7.5, base - 17.0), (cx - 16.5, base - 17.0 + sway), (cx - 13.4 - sway * 0.6, base - 4.6), 6),
                       [1.6, 2.2, 2.8, 3.0, 2.6, 1.8, 0.6]), HORSE_DK, sh=(0.6, 0.6))
        # the hind legs show between the forelegs, as a horse's do from the front: legs side by
        # side under her hips were a pair of trousers
        for s in (-1, 1):
            j = [(cx + s * 2.3, base - 12.0), (cx + s * 2.4, base - 5.6 - (1.4 if ph * s < 0 else 0.0))]
            cel(draw, Limb(j, [1.9, 1.5]), far_coat, sh=None)
            _cen_foot(draw, j, far=True, sock=False)
        cel(draw, Ell(cx, base - 17.0, 12.6, 6.0), far_coat, sh=(1.0, 1.0))
        shapes = [Ell(cx, base - 16.2, 10.0, 6.4)]
        feet = []
        for s in (-1, 1):
            lift = 2.6 if ph * s > 0 else 0.0
            feet.append([(cx + s * 6.6, base - 13.0), (cx + s * 6.5, base - 8.8), (cx + s * 6.5, base - 6.8 - lift * 0.4), (cx + s * 6.6, base - 1.8 - lift)])
            shapes.append(Limb(feet[-1], [2.5, 1.9, 2.15, 1.7]))
        blob(draw, shapes, HORSE, sh=(1.2, 1.0))
        stroke(draw, [(cx, base - 14.6), (cx, base - 10.8)], 0.6, shade(HORSE, 1.6))
        for j in feet:
            _cen_foot(draw, j)
        tunic()
        arms()
    # the head: her hair tied back in a tail of its own, a laurel wreath on it
    rig_head(r, draw, SKIN_TAN, hair=HORSE_DK, style=CEN_HAIR, eye_color=(76, 140, 70), expression="set", mood="sharp")
    if back:
        # on her back, so nearer than her hair: the quiver over her right shoulder, then the tail of hair
        _cen_quiver(draw, r.cx + 7.0, r.sh_y - 4.4, 122.0)
        cel(draw, Limb(bez((hx, hy - ry * 0.3), (hx + sway * 1.0, hy + 6.0), (hx + sway * 2.2, hy + 15.0), 5), [2.4, 3.0, 3.2, 2.8, 1.8, 0.6]),
            HORSE_DK, sh=(0.7, 0.7))
    _cen_wreath(r, draw)


# ===================================================================
# KRAKEN (91) -- a bulbous mantle, big fierce eyes, a mass of tentacles
# ===================================================================

KRAK = (64, 86, 176)
KRAK_LT = (120, 150, 226)
KRAK_UNDER = (236, 184, 212)    # the undersides of its arms, where the suckers are: with the eyes, the one thing on it that is not blue
KRAK_ARM_W = (3.2, 3.0, 2.6, 2.0, 1.5, 1.0, 0.5)

# Its arms, as (out from the middle, up from the ground) for the arm on one side; the other side's is
# the mirror. They leave from under the mantle and lie ON the ground, tips curled up off it: the
# sprite stood on its tentacles' tips before, 3.5 units over its own shadow.
KRAK_WIDE = ((5.5, -13.5), (10.4, -9.0), (13.6, -3.8), (17.0, -2.0), (19.4, -3.6), (19.2, -6.4), (17.4, -6.6))
KRAK_NEAR = ((2.2, -12.5), (4.6, -6.0), (6.4, -2.0), (9.6, -0.6), (12.0, -1.8), (12.2, -4.2), (10.8, -4.8))
KRAK_UP = ((8.0, -16.0), (13.6, -14.2), (17.0, -18.0), (17.2, -23.8), (19.0, -28.2), (21.8, -28.2), (21.8, -25.6))


def _krak_spline(pts, n=4, closed=False):
    """Points along a smooth curve through `pts` (Catmull-Rom, n steps a span)."""
    if closed:
        ext, spans = [pts[-1]] + list(pts) + [pts[0], pts[1]], len(pts)
    else:
        ext, spans = [pts[0]] + list(pts) + [pts[-1]], len(pts) - 1
    out = []
    for i in range(1, spans + 1):
        p0, p1, p2, p3 = ext[i - 1], ext[i], ext[i + 1], ext[i + 2]
        for k in range(n):
            t = k / float(n)
            out.append(tuple(0.5 * (2 * p1[j] + (p2[j] - p0[j]) * t + (2 * p0[j] - 5 * p1[j] + 4 * p2[j] - p3[j]) * t * t +
                                    (3 * p1[j] - p0[j] - 3 * p2[j] + p3[j]) * t * t * t) for j in (0, 1)))
    return out if closed else out + [tuple(pts[-1])]


def _krak_arm(draw, pts, s, col=KRAK, under=4, n=4):
    """One arm: a thick tapering curve through `pts`, round at the tip. Its underside, pale, shows
    along the `s` side of its outer part (the last `under` spans): the side that lies on the
    ground, turned out where the tip curls up."""
    c = _krak_spline(pts, n)
    w = []
    for i in range(len(pts) - 1):
        w += [KRAK_ARM_W[i] + (KRAK_ARM_W[i + 1] - KRAK_ARM_W[i]) * k / float(n) for k in range(n)]
    w.append(KRAK_ARM_W[len(pts) - 1])
    band, bw = [], []
    for i in range(max(0, len(c) - 1 - under * n), len(c)):
        a, b = c[max(i - 1, 0)], c[min(i + 1, len(c) - 1)]
        ln = math.hypot(b[0] - a[0], b[1] - a[1]) or 1.0
        band.append((c[i][0] - s * (b[1] - a[1]) / ln * w[i] * 0.62, c[i][1] + s * (b[0] - a[0]) / ln * w[i] * 0.62))
        bw.append(w[i] * 0.62)
    cel(draw, Limb(c, w), col, sh=(0.5, 0.5), regions=[(Limb(band, bw, cap0=True), KRAK_UNDER)] if under else ())


def draw_kraken(draw, ox, oy, direction, frame):
    # planted: the arms stay on the ground and it is the mantle that rises and sinks, a slow pulse
    # over the four frames, while the arms reach and gather under it
    d, back, base, cx, ph = creature_setup(ox, oy, direction, frame, bob=[0, 0, 0, 0])
    up = [0.0, 1.0, 1.0, 0.0][frame]
    wave = frame * math.pi / 2.0
    my = base - 21.5                            # the mantle sits down on its arms: they are a skirt under it, not four legs

    def arm(pts, k, reach=0.0):
        """An arm from the tables on the ground toward k (-1 the camera's left, +1 its right): its
        outer part pushed `reach` further out, the tip lifted as it reaches."""
        lift = max(0.0, reach) * 0.6
        _krak_arm(draw, [(cx + k * (u + (reach if i >= 2 else 0.0)), base + v - (lift if i >= 4 else 0.0)) for i, (u, v) in enumerate(pts)],
                  k, under=2)

    if d:
        # the raised arms: one ahead, one behind and a little lower
        for k in (-d, d):
            a = math.sin(wave + (k == d) * 2.0)
            _krak_arm(draw, [(cx + k * (u + (0.6 if k != d else 0.0) + (a * 1.0 if i >= 3 else 0.0)),
                              base + v + (1.8 if k != d and i >= 2 else 0.0) - (a * 0.6 if i >= 3 else 0.0)) for i, (u, v) in enumerate(KRAK_UP)], k)
        # ahead and behind on the ground, stretching out and gathering in, and the two nearest the camera
        arm(KRAK_WIDE, d, ph * 2.2)
        arm(KRAK_WIDE, -d, ph * 2.2 + 1.4)
        arm(KRAK_NEAR, d, -ph * 1.6)
        arm(KRAK_NEAR, -d, -ph * 1.6)
        # the mantle leans back: a sac swelling up and behind, the face low on its front
        sac = [(8.0, 8.0), (11.6, 3.0), (9.8, -4.8 - up * 0.5), (3.6, -12.6 - up), (-4.0, -15.6 - up), (-10.8, -12.2 - up), (-14.4, -4.6 - up * 0.5),
               (-12.6, 3.0), (-6.6, 7.6), (1.0, 9.0)]
        mantle = Poly(_krak_spline([(cx + d * u, my + v) for (u, v) in sac], 5, closed=True))
        spots = ((-8.6, -8.2, 2.3), (-2.4, -12.0, 1.6), (-11.0, -1.4, 1.5), (2.6, -9.6, 1.2))
    else:
        for s in (-1, 1):
            a = math.sin(wave + (s > 0) * 2.0)
            _krak_arm(draw, [(cx + s * (u + (a * 1.0 if i >= 3 else 0.0)), base + v - (a * 0.6 if i >= 3 else 0.0)) for i, (u, v) in enumerate(KRAK_UP)], s)
        for s in (-1, 1):
            arm(KRAK_WIDE, s, s * ph * 2.2)
        for s in (-1, 1):
            arm(KRAK_NEAR, s, -s * ph * 1.6)
        mantle = Poly(arc_pts(cx, my - 4.4 - up, 12.0, 11.4, 172, 368, 40) + arc_pts(cx, my + 1.8, 10.4, 7.2, 12, 168, 24))
        if back:
            spots = ((-2.0, -10.6, 2.6), (5.6, -6.4, 2.1), (-6.6, -4.4, 1.8), (0.6, -1.0, 1.5), (6.4, 2.6, 1.2), (-4.6, 3.6, 1.3))    # a scatter: a pair of them side by side was a pair of eyes
        else:
            spots = ((-5.2, -10.6, 2.2), (4.4, -12.2, 1.5), (7.8, -7.4, 1.2))
    cel(draw, mantle, KRAK, sh=(1.6, 1.4), hi=(1.0, 1.0))
    for (u, v, k) in spots:
        Ell(cx + (d if d else 1) * u, my + v - up * 0.7, k, k * 0.85).draw(draw, fill=KRAK_LT)
    if back:
        return
    fy = my + 1.0 - up * 0.4
    if d:
        eye_pair(draw, cx + d * 3.2, cx + d * 8.6, fy, d, (250, 210, 50), mood="sharp", skin=KRAK, w=5.2, h=6.0, lash=(20, 24, 60))
        stroke(draw, [(cx + d * 5.0, fy + 5.9), (cx + d * 7.2, fy + 5.3), (cx + d * 9.4, fy + 5.9)], 0.9, shade(KRAK, 1.8))
    else:
        eye_pair(draw, cx - 5.0, cx + 5.0, fy, 0, (250, 210, 50), mood="sharp", skin=KRAK, w=5.2, h=6.0, lash=(20, 24, 60))
        stroke(draw, [(cx - 2.4, fy + 5.9), (cx, fy + 5.3), (cx + 2.4, fy + 5.9)], 0.9, shade(KRAK, 1.8))


# ===================================================================
# SPHINX (92) -- a golden lion with a pharaoh's head and nemes
# ===================================================================

SPX = (230, 182, 100)           # the lion's coat: a sandy gold, yellower and paler than the Griffin's tawny
SPX_DK = (132, 82, 50)          # the tuft on its tail
SPX_BELLY = (246, 222, 164)     # its chest
NEMES_BLUE = (56, 90, 180)
SPX_SKIN = (226, 164, 118)      # bronze, a clear step under the gold round it: skin, gold and coat in one tan were one mass
SPX_IRIS = (44, 112, 204)
SPX_KOHL = (40, 26, 34)


def _spx_curve(pts, widths, n=4):
    """A smooth curve through `pts` (Catmull-Rom, n steps a span) and a half-width for each of its points."""
    ext = [pts[0]] + list(pts) + [pts[-1]]
    out, w = [], []
    for i in range(1, len(ext) - 2):
        p0, p1, p2, p3 = ext[i - 1], ext[i], ext[i + 1], ext[i + 2]
        for k in range(n):
            t = k / float(n)
            out.append(tuple(0.5 * (2 * p1[j] + (p2[j] - p0[j]) * t + (2 * p0[j] - 5 * p1[j] + 4 * p2[j] - p3[j]) * t * t +
                                    (3 * p1[j] - p0[j] - 3 * p2[j] + p3[j]) * t * t * t) for j in (0, 1)))
            w.append(widths[i - 1] + (widths[i] - widths[i - 1]) * t)
    return out + [tuple(pts[-1])], w + [widths[-1]]


def _spx_cloth(draw, cloth, hx, y0, y1):
    """One piece of the nemes: gold, striped in blue from y0 down to y1. Few stripes and broad: at
    1.2 units on a 2.6 pitch they were a grey-blue moire at 77px, the blue and the gold averaged to mud."""
    cel(draw, cloth, GOLD, sh=(1.2, 1.0), line=False)

    def stripes(l):
        y = y0
        while y < y1:
            Poly([(hx - 30, y), (hx + 30, y), (hx + 30, y + 2.0), (hx - 30, y + 2.0)]).draw(l, fill=NEMES_BLUE)
            y += 4.4
    clipped(draw, cloth, stripes)
    cloth.draw(draw, fill=None, outline=ink(GOLD), width=1.0)


def _spx_nemes(draw, hx, hy, rx, ry, d, back=False):
    """The striped headcloth. Its own shape, not a dome: the cloth stands out from the temples in
    two wings, their points level with the jaw, and falls from under them in two lappets to the
    chest; behind, it is gathered into a queue. It sits on the brow, clear of the eyes."""
    y0, y1 = hy - 10.2, hy + 11.0
    if d:
        dome = arc_pts(-0.6, hy - 0.6, rx + 1.4, ry + 1.4, 180, 332, 22)
        rest = [(rx - 1.8, hy - 4.5), (-1.4, hy - 4.2), (-2.2, hy + 2.6), (-1.2, hy + 6.6), (0.8, hy + 11.8), (-3.8, hy + 12.2),
                (-5.6, hy + 7.6), (-10.4, hy + 8.8), (-rx - 2.4, hy + 3.4)]
        _spx_cloth(draw, Poly([(hx + d * u, y) for (u, y) in dome + rest]), hx, y0, y1)
        cel(draw, Poly([(hx + d * (rx - 1.2), hy - 6.5), (hx + d * (rx - 1.6), hy - 4.3), (hx - d * 1.6, hy - 4.0), (hx - d * 1.8, hy - 6.3)]),
            GOLD, sh=None, lw=0.7)
        return
    dome = arc_pts(hx, hy - 0.6, rx + 1.4, ry + 1.4, 180, 360, 26)
    if back:
        side = [(rx + 2.8, 2.6), (rx + 5.0, 6.6), (rx + 0.6, 8.6), (4.0, 11.0)]
        _spx_cloth(draw, Poly(dome + [(hx + u, hy + v) for (u, v) in side] + [(hx - u, hy + v) for (u, v) in reversed(side)]), hx, y0, y1)
        return
    side = [(rx + 2.8, 2.6), (rx + 5.0, 6.6), (rx + 0.6, 7.4), (rx + 0.2, 11.8), (rx - 4.6, 11.8), (rx - 4.6, 7.0), (rx - 1.4, 3.0),
            (rx - 1.0, -1.6), (rx - 2.4, -4.9)]
    _spx_cloth(draw, Poly(dome + [(hx + u, hy + v) for (u, v) in side] + [(hx, hy - 5.5)] +
                          [(hx - u, hy + v) for (u, v) in reversed(side)]), hx, y0, y1)
    # the gold band on the brow
    cel(draw, Poly([(hx - rx + 2.2, hy - 4.5), (hx - rx + 2.9, hy - 7.0), (hx, hy - 7.9), (hx + rx - 2.9, hy - 7.0), (hx + rx - 2.2, hy - 4.5),
                    (hx, hy - 5.3)]), GOLD, sh=None, lw=0.7)


def _spx_tail(draw, pts, ang, col=SPX):
    """A lion's tail through `pts`, the dark tuft on its end pointing along `ang`."""
    c, w = _spx_curve(pts, [1.7, 1.5, 1.3, 1.1])
    cel(draw, Limb(c, w), col, sh=(0.4, 0.4))
    x, y = pts[-1]
    tuft = [(-1.2, -1.1), (1.2, -2.4), (3.6, -1.9), (6.0, 0.0), (3.6, 1.9), (1.2, 2.4), (-1.2, 1.1)]
    cel(draw, Poly(xform(tuft, x, y, ang)), SPX_DK, sh=(0.4, 0.4))


def _spx_leg(x0, y0, base, d, swing, hind=False):
    """Joints of one leg in profile, from its top to the paw, trotting: the paw swung `swing`
    (-1 back .. +1 forward) and lifted as it comes forward; a hind leg's hock sits back."""
    fx = x0 + d * (swing * 2.8 + (0.0 if hind else 0.4))
    fy = base - 1.6 * max(0.0, swing) - 1.8
    if hind:
        return [(x0, y0), (x0 + d * (swing * 1.0 - 1.8), (y0 + fy) / 2.0 + 0.6), (fx, fy)]
    return [(x0, y0), (x0 + d * (swing * 1.6 + 0.3), (y0 + fy) / 2.0), (fx, fy)]


def _spx_paw(draw, x, y, d=0, col=SPX, toes=True, r=3.2):
    """A big round paw, its sole on the ground 1.9 under (x, y). Toes, not claws: the kit's claws
    hang two units under the ground line."""
    cel(draw, Ell(x + d * 1.1, y, r, 1.9), col, sh=(0.0, 0.6))
    if toes:
        for k in ((-1.1, 1.1) if not d else (d * 1.4, d * 2.8)):
            stroke(draw, [(x + k, y + 0.2), (x + k, y + 1.5)], 0.5, ink(col))


def draw_sphinx(draw, ox, oy, direction, frame):
    d, back, base, cx, ph = creature_setup(ox, oy, direction, frame)
    sway = [0.0, 1.0, 0.0, -1.0][frame]
    r = rig(ox, oy, direction, frame, head=0.9)     # the pharaoh's head: the rig gives its size and its face
    rx, ry = r.head_rx, r.head_ry
    far = shade(SPX, 0.7)
    if d:
        def X(u):
            return cx + d * u
        _spx_tail(draw, [(X(-13.4), base - 15.6), (X(-18.6), base - 13.4 + sway * 0.5), (X(-21.2), base - 18.4 + sway),
                         (X(-19.4), base - 23.6 + sway)], -90 + d * 18)
        # a trot: the diagonal pairs swing together
        far_f = _spx_leg(X(4.6), base - 11.0, base, d, -ph)
        far_h = _spx_leg(X(-7.6), base - 11.6, base, d, ph, hind=True)
        for j in (far_h, far_f):
            cel(draw, Limb(j, [2.7, 2.2, 2.0]), far, sh=None)
            _spx_paw(draw, j[-1][0], j[-1][1], d, far, toes=False, r=2.9)
        # a lion's body, as long as the head is wide: a deep chest, the barrel, a heavy haunch, the near legs
        near_f = _spx_leg(X(7.0), base - 11.4, base, d, ph)
        near_h = _spx_leg(X(-9.8), base - 12.0, base, d, -ph, hind=True)
        blob(draw, [Ell(X(5.4), base - 14.6, 5.8, 6.6), Ell(X(-1.8), base - 14.2, 9.6, 5.6), Ell(X(-9.0), base - 14.4, 5.8, 6.4),
                    Limb(near_f, [3.0, 2.4, 2.1]), Limb(near_h, [3.4, 2.5, 2.1])], SPX, sh=(1.2, 1.2),
             regions=[(Ell(X(8.6), base - 12.4, 4.2, 5.6), SPX_BELLY)])
        for j in (near_h, near_f):
            _spx_paw(draw, j[-1][0], j[-1][1], d)
        hx, hy = X(8.0), base - 25.4
        # the queue of the headcloth lies back along the spine
        _spx_cloth(draw, Limb([(hx - d * 10.6, hy + 5.4), (hx - d * 13.6, hy + 6.2), (hx - d * 15.8, hy + 8.0)], [2.1, 1.9, 1.4]),
                   hx, hy - 10.2, hy + 12.0)
    elif back:
        # leaving: the headcloth beyond the shoulders, then the rump and the hind legs, the tail nearest
        hx, hy = cx, base - 26.0
        _spx_nemes(draw, hx, hy, rx, ry, 0, back=True)
        shapes = [Ell(cx, base - 13.4, 10.4, 7.4), Ell(cx, base - 18.6, 8.0, 4.8)]
        feet = []
        for s in (-1, 1):
            lift = 1.6 if ph * s < 0 else 0.0
            feet.append((cx + s * 6.0, base - 1.9 - lift))
            shapes.append(Limb([(cx + s * 5.6, base - 10.6), (cx + s * 6.0, base - 2.9 - lift)], [3.1, 2.8]))
        blob(draw, shapes, SPX, sh=(1.2, 1.2))
        for (fx, fy) in feet:
            _spx_paw(draw, fx, fy, toes=False)
        _spx_cloth(draw, Limb([(hx, hy + 4.6), (hx, hy + 9.6)], [2.2, 1.7]), hx, hy - 10.2, hy + 11.0)
        _spx_tail(draw, [(cx + 0.6, base - 11.4), (cx - 5.6, base - 7.4), (cx - 12.6, base - 7.6 + sway * 0.5),
                         (cx - 16.4 - sway * 0.6, base - 10.6)], -90 - sway * 8)
        return
    else:
        # coming on: the tail out to one side, the haunches and hind paws either side behind, darker;
        # the chest and two heavy forelegs in front
        _spx_tail(draw, [(cx + 8.6, base - 7.6), (cx + 15.0, base - 4.6), (cx + 19.4, base - 7.2 + sway * 0.5),
                         (cx + 18.8 + sway * 0.8, base - 11.8)], -92 + sway * 8, far)
        for s in (-1, 1):
            lift = 1.0 if ph * s > 0 else 0.0
            cel(draw, Ell(cx + s * 8.8, base - 8.8, 4.8, 6.4), far, sh=(0.6, 0.6))
            _spx_paw(draw, cx + s * 10.0, base - 1.9 - lift, 0, far, toes=False, r=2.9)
        shapes = [Ell(cx, base - 12.4, 8.0, 6.4)]
        feet = []
        for s in (-1, 1):
            lift = 1.8 if ph * s < 0 else 0.0
            feet.append((cx + s * 4.3, base - 1.9 - lift))
            shapes.append(Limb([(cx + s * 4.3, base - 11.0), (cx + s * 4.3, base - 2.9 - lift)], [3.0, 2.8]))
        blob(draw, shapes, SPX, sh=(1.2, 1.0), regions=[(Ell(cx, base - 13.6, 4.6, 5.6), SPX_BELLY)])
        for (fx, fy) in feet:
            _spx_paw(draw, fx, fy)
        hx, hy = cx, base - 26.0
    # the pharaoh's head: bronze skin, eyes lined in kohl
    r.hx, r.head_cy = hx, hy
    head_skull(r, draw, SPX_SKIN, ears=False)
    head_face(r, draw, SPX_SKIN, iris=SPX_IRIS, mood="sharp", mouth="smirk", blush=False, brows=True, brow_color=(86, 50, 40),
              lash=SPX_KOHL)
    _spx_nemes(draw, hx, hy, rx, ry, d)
    # the gold cobra reared on the brow, and the braided beard
    if d:
        ux, uy = hx + d * (rx - 0.8), hy - 5.2
        cel(draw, Poly([(ux - d * 0.9, uy + 0.6), (ux + d * 1.1, uy - 0.2), (ux + d * 2.5, uy - 2.2), (ux + d * 2.3, uy - 4.2), (ux + d * 1.1, uy - 5.0),
                        (ux - d * 0.3, uy - 3.4)]), GOLD, sh=(0.3, 0.3), lw=0.7)
    else:
        cel(draw, Poly([(hx - 0.9, hy - 5.6), (hx - 2.1, hy - 9.6), (hx - 1.3, hy - 12.2), (hx, hy - 13.6), (hx + 1.3, hy - 12.2),
                        (hx + 2.1, hy - 9.6), (hx + 0.9, hy - 5.6)]), GOLD, sh=(0.5, 0.4), lw=0.7)
        Ell(hx, hy - 9.8, 0.8, 1.5).draw(draw, fill=NEMES_BLUE)
    bx = hx + d * 5.6
    cel(draw, RRect(bx - 1.2, hy + ry - 1.6, bx + 1.2, hy + ry + 3.6, 0.8), NEMES_BLUE, sh=None, lw=0.6)
    for k in (0.6, 2.4):
        stroke(draw, [(bx - 1.1, hy + ry - 1.2 + k), (bx + 1.1, hy + ry - 1.2 + k)], 0.5, GOLD)


# ===================================================================
# CYCLOPS (93) -- one enormous eye under a single bushy brow, a stubby
# horn, an underbite with two tusks, a spotted pelt and a big club
# resting on its shoulder
# ===================================================================

CYC_SKIN = (238, 180, 132)
CYC_PELT = (236, 168, 76)
CYC_SPOT = (138, 84, 44)
CYC_IRIS = (40, 150, 206)
CYC_BROW = (92, 56, 40)
CYC_HORN = (242, 232, 208)
CYC_CLUB = (172, 112, 64)


def _spots(draw, shape, pts, k=1.0):
    def paint(l):
        for (x, y) in pts:
            Ell(x, y, 1.1 * k, 0.9 * k).draw(l, fill=CYC_SPOT)
    clipped(draw, shape, paint)


def _cyclops_eye(draw, x, y, rx, ry, look):
    """The one great eye: a white nearly filled by a blue iris, two
    catchlights, and a heavy lid sagging over the top of it -- no lash flick,
    which on an eye with no partner reads as a wink."""
    Ell(x, y, rx, ry).draw(draw, fill=EYE_WHITE)
    ix = x + look * rx * 0.22
    cel(draw, Ell(ix, y + ry * 0.12, rx * 0.66, ry * 0.7), CYC_IRIS, sh=None, line=False)
    Ell(ix, y - ry * 0.06, rx * 0.46, ry * 0.42).draw(draw, fill=shade(CYC_IRIS, 2.8))
    Ell(ix + rx * 0.06, y + ry * 0.46, rx * 0.36, ry * 0.18).draw(draw, fill=lit(CYC_IRIS, 1.4))
    Ell(ix - rx * 0.26, y - ry * 0.22, rx * 0.2, ry * 0.2).draw(draw, fill=(255, 255, 255))
    Ell(ix + rx * 0.28, y + ry * 0.3, rx * 0.09, ry * 0.09).draw(draw, fill=(255, 255, 255))
    lid_y = y - ry * 0.34
    lid = Poly(arc_pts(x, y, rx + 0.4, ry + 0.5, 180, 360, 16) + [(x + rx + 0.4, lid_y + 0.6), (x, lid_y + 1.0), (x - rx - 0.4, lid_y + 0.6)])
    cel(draw, lid, CYC_SKIN, sh=None, line=False)
    stroke(draw, [(x - rx - 0.3, lid_y + 0.9), (x, lid_y + 1.2), (x + rx + 0.3, lid_y + 0.9)], 1.0, (90, 48, 44))
    Ell(x, y, rx, ry).draw(draw, fill=None, outline=(90, 48, 44), width=0.8)


def _club(draw, x0, y0, x1, y1, near=True):
    """A caveman's club: a grip widening to a knobbly head, a knot or two."""
    col = CYC_CLUB if near else shade(CYC_CLUB, 0.5)
    ang = math.atan2(y1 - y0, x1 - x0)
    ux, uy = math.cos(ang), math.sin(ang)
    px, py = -uy, ux
    cel(draw, Limb([(x0, y0), (x0 + (x1 - x0) * 0.6, y0 + (y1 - y0) * 0.6), (x1, y1)], [1.3, 2.4, 3.6]), col, sh=(0.6, 0.0))
    head = [Ell(x1, y1, 4.4, 4.4), Ell(x1 + px * 3.2 - ux * 1.6, y1 + py * 3.2 - uy * 1.6, 2.0, 2.0),
            Ell(x1 - px * 3.4 - ux * 0.4, y1 - py * 3.4 - uy * 0.4, 1.9, 1.9), Ell(x1 + ux * 3.0, y1 + uy * 3.0, 2.4, 2.4)]
    blob(draw, head, col, sh=(0.8, 0.8))
    if near:
        for (u, v) in ((-1.0, 1.0), (1.6, -1.4)):
            cel(draw, Ell(x1 + ux * u + px * v, y1 + uy * u + py * v, 0.9, 0.7), shade(col, 1.2), sh=None, line=False)
        stroke(draw, [(x1 - ux * 2.6 - px * 1.6, y1 - uy * 2.6 - py * 1.6), (x1 - ux * 0.6 - px * 2.8, y1 - uy * 0.6 - py * 2.8)], 0.5,
               lit(col, 1.2))


def draw_cyclops(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=1.32, head=1.04)
    d = r.d
    cx = r.cx
    cl_side = _hold_side(r)
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    # the club rests on the shoulder: the grip in a hand at the chest, the
    # knobbly end up behind the shoulder. The arm that carries it does not swing
    # with the walk: the club rode the swinging hand, and in profile its head
    # slid out from behind his skull on one stride and was gone on the other
    lift, reach = 5.4, 0.2
    ph, r.ph = r.ph, 0.0
    s_, e_, w_, h = arm_pts(r, cl_side, reach, lift)
    r.ph = ph
    if d:
        # back over the shoulder far enough that all of its head is clear of
        # his: three units of it peeping round the skull were a second ear
        top = (h[0] - d * 17.6, h[1] - 13.7)
    else:
        top = (h[0] + cl_side * 5.2, h[1] - 20.6)
    if r.front:
        _club(draw, h[0] - cl_side * 0.4, h[1] + 2.4, top[0], top[1])
    if d:
        rig_arms(r, draw, CYC_SKIN, CYC_SKIN, layer="far", cuff=shade(LEATHER, 0.4))
    # legs, bare feet
    rig_legs(r, draw, CYC_SKIN, CYC_SKIN, bare=True, width=1.12)
    rig_torso(r, draw, CYC_SKIN)
    if not r.back:
        # a round belly
        cel(draw, Ell(cx + d * 2.2, r.waist_y - 0.6, 5.6 if not d else 4.0, 4.6), lit(CYC_SKIN, 0.5), sh=None, line=False)
        stroke(draw, [(cx + d * 2.8 - 0.6, r.waist_y + 0.6), (cx + d * 2.8 + 0.6, r.waist_y + 0.6)], 0.5, shade(CYC_SKIN, 1.6))
    # the pelt: over one shoulder and round the waist, spotted like a leopard
    if d:
        sash = Poly([(cx - d * 3.6, r.sh_y - 2.6), (cx + d * 1.6, r.sh_y - 2.6), (cx + d * 5.4, r.waist_y - 0.6), (cx + d * 4.6, r.waist_y + 1.4),
                     (cx - d * 4.6, r.waist_y + 1.0)])
    else:
        s = -cl_side                             # his right shoulder, from in front and from behind
        sash = Poly([(cx + s * (r.sh_w - 0.6), r.sh_y - 2.6), (cx + s * (r.sh_w - 4.6), r.sh_y - 2.8), (cx - s * 4.6, r.waist_y - 0.6),
                     (cx - s * (r.hip_w + 0.6), r.waist_y + 0.4), (cx - s * (r.hip_w + 0.2), r.waist_y + 2.0), (cx + s * (r.hip_w - 0.6), r.waist_y + 0.6),
                     (cx + s * (r.sh_w + 0.6), r.sh_y + 1.6)])
    cel(draw, sash, CYC_PELT, sh=(0.8, 0.6))
    _spots(draw, sash, [(cx + u, r.sh_y + v) for (u, v) in ((-5.0, 0.6), (-2.0, 3.2), (1.4, 5.6), (4.6, 2.4), (-3.6, 6.4), (2.6, 0.0))])
    hem = r.hip_y + 3.4
    if d:
        kilt = Poly([(cx - 5.4, r.waist_y + 0.8), (cx + 5.6, r.waist_y + 0.8), (cx + 6.4, hem - 0.6), (cx + 3.4, hem + 0.6), (cx + 0.6, hem - 0.8),
                     (cx - 2.4, hem + 0.6), (cx - 6.2, hem - 0.4)])
    else:
        kilt = Poly([(cx - r.hip_w - 0.6, r.waist_y + 0.8), (cx + r.hip_w + 0.6, r.waist_y + 0.8), (cx + r.hip_w + 1.6, hem - 0.6),
                     (cx + 5.0, hem + 0.8), (cx + 2.0, hem - 0.6), (cx - 0.8, hem + 1.0), (cx - 3.8, hem - 0.4), (cx - 6.6, hem + 0.8),
                     (cx - r.hip_w - 1.6, hem - 0.4)])
    cel(draw, kilt, CYC_PELT, sh=(1.0, 0.6))
    _spots(draw, kilt, [(cx + u, r.waist_y + v) for (u, v) in ((-6.0, 3.0), (-2.4, 5.6), (1.2, 3.0), (4.8, 5.4), (7.4, 2.4))])
    stroke(draw, [(cx - (r.hip_w + 0.6 if not d else 5.4), r.waist_y + 0.9), (cx + (r.hip_w + 0.6 if not d else 5.6), r.waist_y + 0.9)],
           1.1, (120, 84, 56))
    rig_arms(r, draw, CYC_SKIN, CYC_SKIN, layer="near", hands=False, cuff=shade(LEATHER, 0.4),
             sides=tuple(sd for sd in _sides(r) if sd != cl_side))
    # the arm that carries the club, raised to the chest
    aw = 2.2 * 1.3
    cel(draw, Limb([s_, e_, w_], [aw, aw * 0.9, aw * 0.82]), CYC_SKIN, sh=(0.8, 0.4))
    cel(draw, Ell(w_[0], w_[1] + 0.2, aw * 0.95, 1.0), shade(LEATHER, 0.4), sh=None)
    # big ears, a stubby horn
    if not d:
        for s in (-1, 1):
            cel(draw, Ell(hx + s * (rx + 0.4), hy + 1.6, 3.0, 3.8), CYC_SKIN, sh=(0.5, 0.5))
            if not r.back:
                Ell(hx + s * (rx + 0.6), hy + 1.8, 1.4, 2.2).draw(draw, fill=shade(CYC_SKIN, 1.2))
    if d and not r.back:
        _club(draw, h[0] + d * 0.2, h[1] + 2.4, top[0], top[1])
    head_skull(r, draw, CYC_SKIN, ears=False)
    if d:
        # the ear well back from the eye: a round one beside it reads as a
        # second eye, which a cyclops cannot have
        ex_ = hx - d * 7.2
        cel(draw, Ell(ex_, hy + 2.2, 2.6, 3.4), CYC_SKIN, sh=(0.4, 0.4))
        clipped(draw, Ell(ex_, hy + 2.2, 2.6, 3.4), lambda l: Ell(ex_ - d * 1.0, hy + 2.4, 1.6, 2.4).draw(l, fill=shade(CYC_SKIN, 1.3)))
    hb = (hx - d * 1.4, hy - ry + 1.6)
    horn = Limb([(hb[0], hb[1] + 1.6), (hb[0] - d * 0.6, hb[1] - 2.4), (hb[0] - d * 2.2 + 0.4, hb[1] - 5.4)], [2.6, 1.8, 0.4])
    cel(draw, horn, CYC_HORN, sh=(0.4, 0.4))
    stroke(draw, [(hb[0] - 1.6, hb[1] - 0.4), (hb[0] + 1.6, hb[1] - 0.8)], 0.45, shade(CYC_HORN, 1.4))
    if not r.back:
        # the one great eye, under a single bushy brow
        ex = hx + d * 4.2
        ey = hy + 0.4
        w = 10.6 if not d else 7.0
        _cyclops_eye(draw, ex, ey, w / 2.0, 5.4, float(d))
        # one bushy brow, knitted down in the middle
        bw = w * 0.6
        cel(draw, Limb([(ex - bw - 1.2, ey - 7.6), (ex - bw * 0.4, ey - 7.4), (ex, ey - 6.2), (ex + bw * 0.4, ey - 7.4),
                        (ex + bw + 1.2, ey - 7.6)], [1.1, 1.5, 1.4, 1.5, 1.1]), CYC_BROW, sh=None, lw=0.6)
        for s in ((-1, 1) if not d else (-d,)):
            Ell(hx + s * 8.2 + d * 1.6, hy + 5.6, 2.0, 1.0).draw(draw, fill=mix(CYC_SKIN, (255, 110, 110), 0.4))
        # an underbite, two tusks up out of it
        mx, my = hx + d * 5.2, hy + 7.8
        mw = 5.0 if not d else 3.2
        Poly([(mx - mw, my - 0.4)] + bez((mx - mw * 0.4, my + 1.0), (mx, my + 1.2), (mx + mw, my - 0.4), 3)[1:] +
             [(mx + mw * 0.8, my + 1.4), (mx, my + 2.2), (mx - mw * 0.8, my + 1.4)]).draw(draw, fill=(130, 54, 52))
        for s in ((-1, 1) if not d else (d,)):
            tx = mx + s * mw * 0.66 if not d else mx + d * mw * 0.5
            cel(draw, Poly([(tx - 1.1, my + 1.4), (tx + 1.1, my + 1.4), (tx + s * 0.4, my - 2.2)]), TOOTH, sh=None, lw=0.55)
    if r.back:
        # he is bald, and the back of his head was a blank ball: the nape, a step darker where
        # the skull overhangs the neck. A tone, not lines: two creases there were a mouth
        nape = [(hx - 8.4, hy + ry + 1), (hx - 6.4, hy + 8.0), (hx - 3.4, hy + 6.4), (hx + 3.4, hy + 6.4), (hx + 6.4, hy + 8.0), (hx + 8.4, hy + ry + 1)]
        clipped(draw, Ell(hx, hy, rx - 0.7, ry - 0.7), lambda l: Poly(nape).draw(l, fill=shade(CYC_SKIN, 1.0)))
        _club(draw, h[0] - cl_side * 0.4, h[1] + 2.4, top[0], top[1])
    for side in _sides(r):
        if side == cl_side:
            cel(draw, Ell(h[0], h[1], 2.6, 2.6), CYC_SKIN, sh=(0.6, 0.6))
        else:
            rig_hand(r, draw, side, CYC_SKIN, hand_r=2.6)


# ===================================================================
# HARPY (94) -- wild feathered hair and a crest, wings for arms, a tail
# of feathers, a bird's legs; she never quite lands
# ===================================================================

HARPY = (156, 84, 178)
HARPY_DK = (108, 56, 136)      # the bodice, a step darker: the wings are not its sleeves
HARPY_TIP = (238, 180, 240)
HARPY_SKIN = (250, 222, 214)
HARPY_TALON = (250, 196, 70)
HARPY_CLAW = (70, 48, 80)


def _hpy_wing(draw, place, raise_deg, far=False):
    """A wing where an arm would be, authored in its own frame (a out from the shoulder, b up),
    turned up about the shoulder by `raise_deg` and put on the canvas by `place(a, b)`. A dark
    arm along the leading edge, flight feathers, pale tips, a scalloped trailing edge."""
    ang = math.radians(raise_deg)
    ca, sa = math.cos(ang), math.sin(ang)
    lead = [(0.0, 1.6), (5.6, 3.6), (11.6, 3.2), (17.0, 0.4)]
    tips = [(16.4, -5.2), (13.8, -9.2), (10.2, -11.6), (6.4, -11.0), (3.0, -8.4)]
    core = (7.0, -1.0)

    def toward(p, k):
        ln = math.hypot(core[0] - p[0], core[1] - p[1]) or 1.0
        return (p[0] + (core[0] - p[0]) / ln * k, p[1] + (core[1] - p[1]) / ln * k)

    def placed(pts):
        return Poly([place(a * ca - b * sa, a * sa + b * ca) for (a, b) in pts])

    def tone(c):
        return shade(c, 0.7) if far else c

    edge, notches, prev = [], [], lead[-1]
    for t in tips:
        n = toward(((prev[0] + t[0]) / 2.0, (prev[1] + t[1]) / 2.0), 1.5)
        notches.append(n)
        edge += [n, t]
        prev = t
    wing = placed(lead + edge + [(0.0, -4.6)])
    arm = placed(lead + [(15.0, -1.6), (10.6, 0.6), (5.0, 0.8), (0.0, -1.2)])
    tip_edge = [lead[-1]] + edge
    pale = placed(tip_edge + [toward(p, 3.9) for p in reversed(tip_edge)])
    cel(draw, wing, tone(HARPY), sh=(0.8, 0.8), regions=[(pale, tone(HARPY_TIP)), (arm, tone(shade(HARPY, 1.1)))])
    for n in notches:
        a0, a1 = n, toward(n, 3.8)
        stroke(draw, [place(a0[0] * ca - a0[1] * sa, a0[0] * sa + a0[1] * ca), place(a1[0] * ca - a1[1] * sa, a1[0] * sa + a1[1] * ca)],
               0.5, ink(HARPY))


def _hpy_leg(draw, x, hip, foot_y, d, near, lift=0.0):
    """A bird's leg: a bare gold shank, three toes with dark claws (in profile, two forward and
    one back)."""
    col = HARPY_TALON if near else shade(HARPY_TALON, 0.6)
    fy = foot_y - 2.0 - lift
    cel(draw, Limb([(x, hip), (x + (d * 1.2 if d else 0.0), (hip + fy) / 2.0 + 0.6), (x, fy)], [1.5, 1.2, 1.2], cap=False), col, sh=(0.4, 0.0), lw=0.7)
    toes = ((-2.2, 1.0), (0.0, 1.4), (2.2, 1.0)) if not d else ((d * 3.2, 0.9), (d * 1.8, 1.4), (-d * 1.8, 1.0))
    for (tx, ty) in toes:
        cel(draw, Limb([(x, fy), (x + tx, fy + ty)], [1.0, 0.8]), col, sh=None, lw=0.6)
        k = (1 if tx > 0 else -1) if tx else 0
        Poly([(x + tx - 0.6, fy + ty - 0.2), (x + tx + 0.6, fy + ty - 0.2), (x + tx + k * 0.7, fy + ty + 1.0)]).draw(draw, fill=HARPY_CLAW)


def _hpy_tail(draw, x, y, ang, spread, sway=0.0):
    """A fan of three tail feathers from (x, y) along `ang`, pale at the tips."""
    feathers, tips = [], []
    for k in (-1, 0, 1):
        a = math.radians(ang + k * spread + sway * 4.0)
        ln = 9.6 if k == 0 else 8.2
        mid = (x + math.cos(a) * ln * 0.55, y + math.sin(a) * ln * 0.55)
        end = (x + math.cos(a) * ln, y + math.sin(a) * ln)
        feathers.append(Limb([(x, y), mid, end], [1.2, 2.2, 0.7]))
        tips.append(Ell(end[0] - math.cos(a) * 1.4, end[1] - math.sin(a) * 1.4, 2.6, 2.6))
    blob(draw, feathers, HARPY, sh=(0.6, 0.6), regions=[(t, HARPY_TIP) for t in tips])


def draw_harpy(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=0.9, bob=[-1, -3, -2, -3][frame], step=0)
    d = r.d
    flap = [0.0, 1.0, 0.35, 0.8][frame]
    sway = [0.0, 1.0, 0.0, -1.0][frame]
    cx = r.cx
    hx, hy, ry = r.hx, r.head_cy, r.head_ry
    sy = r.sh_y + 0.2
    if d:
        # in profile both wings are swept up behind her, the far one a little ahead of the near
        def side_place(fwd, up):
            return lambda a, b: (cx - d * 1.6 + d * fwd - d * a * 0.9, sy - up - b)
        _hpy_wing(draw, side_place(2.6, 1.0), 30.0 + flap * 30.0, far=True)
        _hpy_tail(draw, cx - d * 3.0, r.hip_y - 0.6, 90 + d * 52, 16, sway)
    else:
        for s in (-1, 1):
            _hpy_wing(draw, lambda a, b, s=s: (cx + s * (4.2 + a), sy - b), 2.0 + flap * 30.0)
    # a bird's legs, dangling: she tucks one, then the other, as she beats
    for i, s in enumerate((-1, 1) if not d else (-d, d)):
        near = not d or s == d
        lx = cx + s * 3.1 if not d else cx + s * 1.5
        lift = 1.3 if (frame == 1 and i == 0) or (frame == 3 and i == 1) else 0.0
        _hpy_leg(draw, lx, r.hip_y + 1.0, r.base_y, d, near, lift)
    rig_torso(r, draw, HARPY_DK)
    # a skirt of feathers, pale at the tips
    skirt = [(cx - r.hip_w - 0.4, r.waist_y + 0.6), (cx + r.hip_w + 0.4, r.waist_y + 0.6)]
    n = 5
    for i in range(n + 1):
        t = i / float(n)
        skirt.append((cx + r.hip_w + 2.0 - (2 * r.hip_w + 4.0) * t, r.hip_y + (4.4 if i % 2 == 0 else 1.6)))
    cel(draw, Poly(skirt), HARPY, sh=(0.8, 0.6), regions=[(Poly([(cx - 20, r.hip_y + 1.0), (cx + 20, r.hip_y + 1.0), (cx + 20, r.hip_y + 6),
                                                                  (cx - 20, r.hip_y + 6)]), HARPY_TIP)])
    if r.back:
        _hpy_tail(draw, cx, r.waist_y + 1.0, 90, 24, sway)
    if d:
        def near_place(a, b):
            return (cx - d * 1.6 - d * a * 0.9, sy - b)
        _hpy_wing(draw, near_place, 22.0 + flap * 32.0)
    head_skull(r, draw, HARPY_SKIN, ears=False)
    if r.back:
        # the nape under the hair's ragged hem: bare skin between its points read as teeth
        clipped(draw, Ell(hx, hy, r.head_rx, ry), lambda l: Ell(hx, hy + 7.0, 11.0, 5.4).draw(l, fill=shade(HARPY, 0.9)))
    if not r.back:
        head_face(r, draw, HARPY_SKIN, iris=(230, 60, 120), mood="sharp", mouth="open", blush=False, brows=True, brow_color=shade(HARPY, 1.4))
    rig_hair(r, draw, HARPY, "wild", skin=HARPY_SKIN)
    # a crest of three long feathers, pale-tipped, taller than the hair now there is room for it
    bx, by = hx - d * 2.0, hy - ry + 1.0
    crest = (-116, -90, -64) if not d else ((-128, -106, -84) if d > 0 else (-52, -74, -96))
    for i, a in enumerate(crest):
        ang = math.radians(a + sway * 3.0)
        ln = 10.2 if i == 1 else 8.8
        mid = (bx + math.cos(ang) * ln * 0.5, by + math.sin(ang) * ln * 0.5)
        end = (bx + math.cos(ang) * ln, by + math.sin(ang) * ln)
        shape = Limb([(bx, by), mid, end], [1.2, 1.9, 0.4])
        cel(draw, shape, HARPY, sh=None, regions=[(Ell(end[0], end[1], 4.4, 4.4), HARPY_TIP)])


# ===================================================================
# GRIFFIN (95) -- an eagle's head, breast, talons and great raised
# wings on a lion's body, hind legs and tufted tail
# ===================================================================

GRF_BODY = (208, 140, 72)      # the lion half: tawny, a clear step under the head's gold
GRF_HEAD = (246, 196, 84)      # the eagle half: gold feathers
GRF_CHEST = (252, 232, 184)    # cream breast feathers, between the two
GRF_WING = (132, 80, 52)
GRF_COVERT = (192, 130, 70)
GRF_BEAK = (74, 72, 86)
GRF_TIP = (70, 176, 190)
GRF_TALON = (250, 206, 90)
GRF_CLAW = (62, 54, 70)
GRF_TUFT = (104, 60, 40)
GRF_EYE = (236, 120, 30)


def _grf_wing(draw, place, flap, far=False):
    """One raised wing, authored in its own frame (a out from the shoulder, b up) and put on
    the canvas by `place(a, b)`, so the same wing serves head-on, from behind and in profile.
    A leading edge up through the wrist to the top tip, then the flight feathers' tips down
    the trailing edge with a notch between each pair. Three colour areas, so it is a wing at
    77px and not a plank: light coverts along the arm, dark flight feathers, teal tips."""
    f = flap
    lead = [(0.0, 0.0), (4.6, 9.5 + f), (9.0, 19.5 + 2.4 * f), (14.6, 25.0 + 3.4 * f)]
    tips = [(17.6, 20.6 + 2.8 * f), (18.6, 14.6 + 2.2 * f), (17.6, 8.8 + 1.6 * f), (15.0, 4.4 + 1.0 * f),
            (11.4, 1.2 + 0.5 * f), (7.2, -1.2)]
    core = (8.0, 9.0 + f)

    def toward(p, k):
        ln = math.hypot(core[0] - p[0], core[1] - p[1]) or 1.0
        return (p[0] + (core[0] - p[0]) / ln * k, p[1] + (core[1] - p[1]) / ln * k)

    def placed(pts):
        return Poly([place(a, b) for (a, b) in pts])

    def tone(c):
        return shade(c, 0.6) if far else c

    edge, notches, prev = [], [], lead[-1]
    for t in tips:
        n = toward(((prev[0] + t[0]) / 2.0, (prev[1] + t[1]) / 2.0), 1.7)
        notches.append(n)
        edge += [n, t]
        prev = t
    wing = placed(lead + edge + [(2.8, -3.0)])
    coverts = placed(lead + [(12.0, 19.6 + 2.6 * f), (8.8, 11.6 + 1.4 * f), (6.0, 3.0), (2.8, -3.0)])
    tip_edge = [lead[-1]] + edge[:8]
    teal = placed(tip_edge + [toward(p, 4.2) for p in reversed(tip_edge)])
    cel(draw, wing, tone(GRF_WING), sh=(0.9, 0.9), regions=[(teal, tone(GRF_TIP)), (coverts, tone(GRF_COVERT))])
    # the gaps between the flight feathers, from each notch in toward the wrist
    for n in notches[:5]:
        stroke(draw, [place(*n), place(*toward(n, 4.6))], 0.5, ink(GRF_WING))


def _grf_tuft(draw, x, y, ang, color=GRF_TUFT):
    """The dark tuft on the end of the lion's tail, pointing along `ang`."""
    pts = [(-1.0, -1.0), (1.2, -2.2), (3.4, -1.7), (5.6, 0.0), (3.4, 1.7), (1.2, 2.2), (-1.0, 1.0)]
    cel(draw, Poly(xform(pts, x, y, ang)), color, sh=(0.4, 0.4))


def _grf_thigh(x, top, lean=0.0):
    """The feathered top of an eagle's leg: a round drumstick of breast feathers ending in two
    points over the bare shank. Shapes for a blob, so it can be one mass with the breast."""
    return [Ell(x, top + 2.8, 3.0, 3.3),
            Poly([(x - 2.7, top + 3.4), (x + 2.7, top + 3.4), (x + 1.4 + lean, top + 7.6), (x + lean * 0.6, top + 5.9),
                  (x - 1.2 + lean, top + 7.6)])]


def _grf_talon_front(draw, x, base, lift):
    """An eagle's foreleg coming at the camera: a bare yellow shank, three toes spread on the
    ground, a dark claw on each."""
    fy = base - 2.2 - lift
    cel(draw, Limb([(x, base - 8.0), (x, fy)], [1.6, 1.4], cap=False), GRF_TALON, sh=(0.5, 0.0), lw=0.7)
    for k in (-1, 0, 1):
        tx, ty = x + k * 2.3, fy + 1.3 - abs(k) * 0.3
        cel(draw, Limb([(x, fy), (tx, ty)], [1.1, 0.9]), GRF_TALON, sh=None, lw=0.6)
        Poly([(tx - 0.7, ty - 0.1), (tx + 0.7, ty - 0.1), (tx + k * 0.5, ty + 1.1)]).draw(draw, fill=GRF_CLAW)


def _grf_talon_side(draw, x0, x1, base, d, lift, near):
    """The same leg in profile: toes forward, one back."""
    col = GRF_TALON if near else shade(GRF_TALON, 0.7)
    fy = base - 2.0 - lift
    cel(draw, Limb([(x0, base - 8.0), ((x0 + x1) / 2.0 + d * 0.4, (base - 8.0 + fy) / 2.0), (x1, fy)], [1.5, 1.3, 1.2], cap=False),
        col, sh=(0.4, 0.0), lw=0.7)
    cel(draw, Limb([(x1, fy), (x1 - d * 1.9, fy + 1.1)], [1.0, 0.7]), col, sh=None, lw=0.6)
    for (tx, ty) in ((3.5, 0.8), (2.0, 1.2)):
        cel(draw, Limb([(x1, fy), (x1 + d * tx, fy + ty)], [1.1, 0.8]), col, sh=None, lw=0.6)
        Poly([(x1 + d * (tx - 0.2), fy + ty - 0.8), (x1 + d * (tx + 1.6), fy + ty + 0.4),
              (x1 + d * (tx - 0.4), fy + ty + 0.7)]).draw(draw, fill=GRF_CLAW)
    blob(draw, _grf_thigh(x0 + (x1 - x0) * 0.2, base - 14.0, (x1 - x0) * 0.4), GRF_CHEST if near else shade(GRF_CHEST, 0.7),
         sh=(0.6, 0.4))


def _grf_lion_side(draw, x0, x1, base, d, lift, near):
    """A lion's hind leg in profile: a heavy thigh, the hock bent back, a round paw."""
    col = GRF_BODY if near else shade(GRF_BODY, 0.6)
    fy = base - 1.9 - lift
    hock = ((x0 + x1) / 2.0 - d * 1.7, base - 6.0 - lift * 0.5)
    cel(draw, Limb([(x0, base - 12.0), hock, (x1, fy)], [2.9, 1.8, 1.6]), col, sh=(0.6, 0.0))
    cel(draw, Ell(x1 + d * 1.0, fy + 0.5, 2.7, 1.6), col, sh=(0.0, 0.5))


def _grf_head_front(draw, hx, hy, back=False):
    """The eagle's head coming at the camera (or going away): pointed ear tufts, cheek
    feathers, a heavy brow, and a hooked beak that hangs past the chin."""
    parts = [Ell(hx, hy, 9.6, 8.6)]
    for s in (-1, 1):
        parts.append(Poly([(hx + s * 7.8, hy - 4.0), (hx + s * 10.6, hy - 12.4), (hx + s * 3.4, hy - 7.6)]))
        parts.append(Poly([(hx + s * 8.8, hy + 0.2), (hx + s * 12.4, hy + 4.0), (hx + s * 7.6, hy + 5.4)]))
    crest = Poly([(hx - 3.4, hy - 9.4), (hx + 3.4, hy - 9.4), (hx + 2.2, hy + 0.6), (hx, hy + 4.6), (hx - 2.2, hy + 0.6)])
    blob(draw, parts, GRF_HEAD, sh=(1.0, 0.9), regions=[(crest, shade(GRF_HEAD, 1.1))] if back else ())
    if back:
        return
    for s in (-1, 1):
        Poly([(hx + s * 7.4, hy - 5.4), (hx + s * 9.6, hy - 10.6), (hx + s * 5.0, hy - 7.4)]).draw(draw, fill=shade(GRF_HEAD, 1.2))
        stroke(draw, [(hx + s * 8.2, hy - 5.6), (hx + s * 4.6, hy - 4.9), (hx + s * 1.5, hy - 2.6)], 1.3, ink(GRF_HEAD))
    eye_pair(draw, hx - 4.9, hx + 4.9, hy - 0.4, 0, GRF_EYE, mood="sharp", skin=GRF_HEAD, w=4.2, h=4.8)
    beak = Poly([(hx - 2.9, hy + 1.2), (hx - 1.6, hy + 0.2), (hx + 1.6, hy + 0.2), (hx + 2.9, hy + 1.2), (hx + 2.5, hy + 4.8),
                 (hx + 0.9, hy + 8.0), (hx, hy + 9.6), (hx - 0.9, hy + 8.0), (hx - 2.5, hy + 4.8)])
    cel(draw, beak, GRF_BEAK, sh=(0.7, 0.5),
        regions=[(Poly([(hx - 4.0, hy - 1.0), (hx + 4.0, hy - 1.0), (hx + 4.0, hy + 2.4), (hx - 4.0, hy + 2.4)]), GRF_TALON)])
    stroke(draw, [(hx - 1.3, hy + 3.4), (hx - 0.6, hy + 6.6)], 0.7, lit(GRF_BEAK, 2.6))
    for s in (-1, 1):
        Ell(hx + s * 1.1, hy + 1.5, 0.45, 0.4).draw(draw, fill=ink(GRF_TALON))


def _grf_head_side(draw, hx, hy, d):
    """The eagle's head in profile: a crest swept back, one fierce eye, the great hooked beak."""
    def X(u):
        return hx + d * u
    parts = [Ell(hx, hy, 7.8, 7.4),
             Poly([(X(-2.0), hy - 6.6), (X(-9.6), hy - 10.8), (X(-6.8), hy - 3.0)]),
             Poly([(X(-5.6), hy - 2.0), (X(-10.8), hy - 1.2), (X(-6.2), hy + 3.2)])]
    blob(draw, parts, GRF_HEAD, sh=(0.9, 0.9))
    lower = Poly([(X(5.2), hy + 1.8), (X(10.0), hy + 2.4), (X(8.8), hy + 4.5), (X(5.4), hy + 4.3)])
    cel(draw, lower, shade(GRF_BEAK, 0.8), sh=None, lw=0.7)
    upper = Poly([(X(4.8), hy - 3.0), (X(9.4), hy - 2.6), (X(12.0), hy - 0.2), (X(12.6), hy + 2.8), (X(11.8), hy + 5.4),
                  (X(10.4), hy + 2.6), (X(5.2), hy + 2.2)])
    cel(draw, upper, GRF_BEAK, sh=(0.0, 0.7),
        regions=[(Poly([(X(3.6), hy - 4.0), (X(7.4), hy - 4.0), (X(7.0), hy + 3.0), (X(3.6), hy + 3.0)]), GRF_TALON)])
    stroke(draw, [(X(8.0), hy - 1.9), (X(10.4), hy - 0.8), (X(11.5), hy + 1.4)], 0.7, lit(GRF_BEAK, 2.6))
    Ell(X(6.1), hy - 0.9, 0.5, 0.4).draw(draw, fill=ink(GRF_TALON))
    stroke(draw, [(X(-1.2), hy - 4.6), (X(2.4), hy - 4.4), (X(5.0), hy - 2.8)], 1.3, ink(GRF_HEAD))
    ms_eye(draw, X(2.2), hy - 0.6, 4.0, 4.6, GRF_EYE, (float(d), 0.0), "sharp", skin=GRF_HEAD, side=-d, lash=(40, 30, 40))


def _grf_side(draw, cx, base, d, ph, flap, sway):
    def X(u):
        return cx + d * u
    sx, sy = X(1.0), base - 19.5          # where the wings leave the back

    def place(a, b, fwd=0.0, up=0.0):
        return (sx + d * fwd - d * (a * 0.92 + b * 0.13), sy - up - (b - a * 0.1))
    _grf_wing(draw, lambda a, b: place(a, b, 3.4, 1.0), flap * 0.7, far=True)
    # the lion's tail, a dark tuft on the end
    tail = [(X(-11.0), base - 15.0), (X(-16.0), base - 12.6 + sway * 0.5), (X(-19.2), base - 16.0 + sway),
            (X(-18.2), base - 21.0 + sway)]
    cel(draw, Limb(tail, [1.6, 1.4, 1.2, 1.0]), GRF_BODY, sh=(0.4, 0.4))
    _grf_tuft(draw, tail[-1][0], tail[-1][1] + 0.6, -90 + d * 14)

    def leg(kind, u, phase, near):
        swing = ph * phase * 2.6
        lift = 1.2 if ph * phase < 0 else 0.0
        (_grf_talon_side if kind == "talon" else _grf_lion_side)(draw, X(u), X(u + swing), base, d, lift, near)
    leg("talon", 5.8, 1, False)
    leg("lion", -7.8, -1, False)
    # one body: a deep eagle's chest, a lion's barrel and haunch, the breast feathers in front
    blob(draw, [Ell(X(4.4), base - 15.4, 6.0, 6.4), Ell(X(-1.2), base - 14.4, 9.6, 5.4), Ell(X(-7.6), base - 14.4, 5.8, 6.2)],
         GRF_BODY, sh=(1.2, 1.2), regions=[(Ell(X(8.0), base - 13.0, 4.8, 5.8), GRF_CHEST)])
    leg("lion", -7.8, 1, True)
    leg("talon", 5.8, -1, True)
    _grf_wing(draw, place, flap)
    # the ruff: gold neck feathers hanging in points over the shoulder
    ruff = [(0.6, -27.0), (-0.6, -21.0), (0.8, -17.0), (2.4, -19.4), (3.8, -15.2), (5.4, -18.0), (7.2, -14.0), (8.8, -17.0),
            (10.6, -14.8), (12.2, -19.0), (13.0, -24.0)]
    cel(draw, Poly([(X(u), base + v) for (u, v) in ruff]), GRF_HEAD, sh=(0.8, 0.8))
    _grf_head_side(draw, X(8.5), base - 26.0, d)


def _grf_front(draw, cx, base, ph, flap, sway):
    for s in (-1, 1):
        _grf_wing(draw, lambda a, b, s=s: (cx + s * (4.6 + a), base - 18.0 - b), flap)
    # the lion half shows either side of the breast: the tail, the haunches, the hind paws
    far = shade(GRF_BODY, 0.5)
    tail = [(cx + 8.0, base - 7.0), (cx + 14.0, base - 5.4), (cx + 17.6 + sway * 0.5, base - 8.6), (cx + 17.0 + sway, base - 12.4)]
    cel(draw, Limb(tail, [1.5, 1.4, 1.2, 1.0]), far, sh=None)
    _grf_tuft(draw, tail[-1][0], tail[-1][1] + 0.6, -96 + sway * 8)
    for s in (-1, 1):
        cel(draw, Ell(cx + s * 8.4, base - 9.2, 4.6, 6.6), far, sh=(0.6, 0.6))
        cel(draw, Ell(cx + s * 9.6, base - 1.5, 2.9, 1.6), far, sh=(0.0, 0.5))
    for s in (-1, 1):
        _grf_talon_front(draw, cx + s * 4.2, base, 1.6 if ph * s < 0 else 0.0)
    # the breast and the feathered tops of the forelegs, one mass
    blob(draw, [Ell(cx, base - 12.6, 7.8, 6.4)] + _grf_thigh(cx - 4.2, base - 13.4) + _grf_thigh(cx + 4.2, base - 13.4),
         GRF_CHEST, sh=(1.0, 0.8))
    # the ruff: gold neck feathers hanging in points over the breast
    ruff = [(-8.6, -21.0), (8.6, -21.0), (9.2, -16.4), (6.4, -17.6), (4.6, -13.4), (2.2, -16.4), (0.0, -12.4), (-2.2, -16.4),
            (-4.6, -13.4), (-6.4, -17.6), (-9.2, -16.4)]
    cel(draw, Poly([(cx + u, base + v) for (u, v) in ruff]), GRF_HEAD, sh=(0.8, 0.8))
    _grf_head_front(draw, cx, base - 26.5)


def _grf_back(draw, cx, base, ph, flap, sway):
    # nearest the camera last: the head, the wings in front of it, then the lion's rump and tail
    _grf_head_front(draw, cx, base - 26.5, back=True)
    for s in (-1, 1):
        _grf_wing(draw, lambda a, b, s=s: (cx + s * (4.6 + a), base - 18.0 - b), flap)
    for s in (-1, 1):
        lift = 1.4 if ph * s < 0 else 0.0
        cel(draw, Limb([(cx + s * 5.4, base - 10.0), (cx + s * 5.8, base - 2.0 - lift)], [2.8, 2.3]), GRF_BODY, sh=(0.6, 0.0))
        cel(draw, Ell(cx + s * 5.8, base - 1.3 - lift, 2.9, 1.7), GRF_BODY, sh=(0.0, 0.5))
    cel(draw, Ell(cx, base - 13.0, 10.0, 7.6), GRF_BODY, sh=(1.2, 1.2))
    # the mantle: where the feathers end and the fur begins
    mantle = [(-8.4, -22.0), (8.4, -22.0), (8.8, -17.4), (6.0, -18.4), (4.2, -14.6), (2.0, -17.4), (0.0, -13.6), (-2.0, -17.4),
              (-4.2, -14.6), (-6.0, -18.4), (-8.8, -17.4)]
    cel(draw, Poly([(cx + u, base + v) for (u, v) in mantle]), GRF_HEAD, sh=(0.8, 0.8), line=False)
    stroke(draw, [(cx + u, base + v) for (u, v) in mantle[1:]], 1.0, ink(GRF_HEAD))
    tail = [(cx - 1.0, base - 10.4), (cx - 7.0, base - 6.0), (cx - 13.6, base - 6.2 + sway * 0.5), (cx - 15.4 - sway * 0.6, base - 10.6)]
    cel(draw, Limb(tail, [1.6, 1.5, 1.3, 1.0]), GRF_BODY, sh=(0.4, 0.4))
    _grf_tuft(draw, tail[-1][0], tail[-1][1] + 0.6, -90 - sway * 8)


def draw_griffin(draw, ox, oy, direction, frame):
    d, back, base, cx, ph = creature_setup(ox, oy, direction, frame)
    flap = [0.0, 1.0, 0.0, 1.0][frame]
    sway = [0.0, 1.0, 0.0, -1.0][frame]
    if d:
        _grf_side(draw, cx, base, d, ph, flap, sway)
    elif back:
        _grf_back(draw, cx, base, ph, flap, sway)
    else:
        _grf_front(draw, cx, base, ph, flap, sway)


# ===================================================================
# ANUBIS (96) -- a black jackal's head with tall ears, out of a lapis
# and gold wig; a gold collar, a white kilt, a was-sceptre
# ===================================================================

ANU_SKIN = (46, 46, 70)
ANU_GOLD = (244, 198, 80)
ANU_LAPIS = (64, 110, 206)
ANU_KILT = (246, 244, 236)


def _anu_wig(draw, r):
    """The wig the jackal's head rises out of: lapis banded in gold, from the crown to the
    shoulders. Behind the head from the front and in profile, all of the head from behind."""
    hx, hy, rx, ry, d = r.hx, r.head_cy, r.head_rx, r.head_ry, r.d
    bottom = r.sh_y + 0.8
    if d:
        pts = arc_pts(hx - 2.0, hy - 0.4, rx + 0.4, ry + 1.2, 180, 360, 26) + \
            [(hx + 2.0, hy + 4.0), (hx - 1.0, bottom), (hx - rx + 0.6, bottom)]
        pts = [(hx + d * (x - hx), y) for (x, y) in pts]
    else:
        pts = arc_pts(hx, hy - 0.4, rx + 1.5, ry + 1.3, 180, 360, 26) + \
            [(hx + rx + 1.2, hy + 6.0), (hx + rx - 1.4, bottom), (hx - rx + 1.4, bottom), (hx - rx - 1.2, hy + 6.0)]
    shape = Poly(pts)

    def bands(l):
        y = hy - ry + 2.4
        while y < bottom:
            Poly([(hx - 20, y), (hx + 20, y), (hx + 20, y + 1.1), (hx - 20, y + 1.1)]).draw(l, fill=ANU_GOLD)
            y += 3.7
        Poly([(hx - 20, bottom - 2.0), (hx + 20, bottom - 2.0), (hx + 20, bottom + 1), (hx - 20, bottom + 1)]).draw(l, fill=ANU_GOLD)
    cel(draw, shape, ANU_LAPIS, sh=(1.2, 1.0), line=False)
    clipped(draw, shape, bands)
    shape.draw(draw, fill=None, outline=ink(ANU_LAPIS), width=1.0)


def _anu_sceptre(draw, x, foot, top, k):
    """The was-sceptre: a gold staff taller than he is, forked at the foot, the long-snouted
    head of the Set beast at the top looking the way `k` points."""
    cel(draw, Poly([(x - 1.9, foot), (x, foot - 3.4), (x + 1.9, foot), (x + 0.8, foot), (x, foot - 1.4), (x - 0.8, foot)]),
        ANU_GOLD, sh=None, lw=0.5)
    cel(draw, Limb([(x, foot - 2.4), (x, top + 1.0)], [1.1, 1.1], cap=False), ANU_GOLD, sh=(0.5, 0.0), lw=0.5)
    cel(draw, Poly([(x - k * 1.3, top + 2.2), (x - k * 1.5, top - 1.4), (x - k * 0.2, top - 3.8), (x + k * 1.0, top - 1.8),
                    (x + k * 5.6, top + 0.4), (x + k * 5.0, top + 2.2), (x + k * 1.3, top + 0.9), (x + k * 1.3, top + 2.2)]),
        ANU_GOLD, sh=(0.4, 0.5), lw=0.5)


def draw_anubis(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=1.06, head=0.94)
    d = r.d
    cx = r.cx
    st_side = _hold_side(r)
    out = 2.6
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    top = hy - ry - 4.4
    aw = 2.2 * 1.06
    if d:
        # in profile the sceptre is in the far hand, held out ahead of him: it stands behind
        # the head and never crosses the face
        sx = cx + d * 7.0
        grip = (sx, r.hip_y - 3.8)
        _anu_sceptre(draw, sx, r.base_y, top, d)
        s0 = r.shoulder(-d)
        far = shade(ANU_SKIN, 0.6)
        el = ((s0[0] + grip[0]) / 2.0, (s0[1] + grip[1]) / 2.0 + 1.2)
        cel(draw, Limb([s0, el, grip], [aw, aw * 0.9, aw * 0.82]), far, sh=(0.8, 0.4))
        cel(draw, Limb([((el[0] + grip[0]) / 2.0, (el[1] + grip[1]) / 2.0), grip], [2.3, 2.3]), shade(ANU_GOLD, 0.6), sh=(0.4, 0.3))
        cel(draw, Ell(grip[0], grip[1], 2.2, 2.2), far, sh=(0.6, 0.6))
    elif not r.back:
        _anu_wig(draw, r)
    rig_legs(r, draw, ANU_SKIN, ANU_GOLD, bare=True)
    rig_torso(r, draw, ANU_SKIN)
    rig_robe(r, draw, ANU_KILT, top=r.waist_y + 0.6, hem=r.hip_y + 4.2, flare=2.0, split=True)
    if not r.back and not d:
        cel(draw, Poly([(cx - 2.0, r.waist_y + 1.4), (cx + 2.0, r.waist_y + 1.4), (cx + 1.6, r.hip_y + 4.0), (cx - 1.6, r.hip_y + 4.0)]), ANU_GOLD, sh=None)
    rig_belt(r, draw, ANU_GOLD, buckle=ANU_LAPIS)
    if not d:
        # the sceptre stands at his side, its head beside his own
        sx = cx + st_side * (r.sh_w + 2.1 + out)
        _anu_sceptre(draw, sx, r.base_y, top, st_side)
    rig_arms(r, draw, ANU_SKIN, ANU_SKIN, layer="near", hands=False, out=0.0, sides=tuple(s for s in _sides(r) if d or s != st_side))
    if not d:
        rig_arms(r, draw, ANU_SKIN, ANU_SKIN, layer="near", hands=False, out=out, sides=(st_side,))
    for side in _sides(r):
        _, e, w, h = arm_pts(r, side, 0.0, 0.0, out if (not d and side == st_side) else 0.0)
        cel(draw, Limb([((e[0] + w[0]) / 2, (e[1] + w[1]) / 2), w], [2.3, 2.3]), ANU_GOLD, sh=(0.4, 0.3))
    # the usekh: a broad collar in gold and lapis bands
    cy0 = r.sh_y - 2.2
    for i, (col, k) in enumerate(((ANU_GOLD, 1.0), (ANU_LAPIS, 0.78), (ANU_GOLD, 0.56))):
        if d:
            shape = Poly(arc_pts(cx + d * 0.6, cy0, 6.0 * k + 1.0, 5.0 * k + 0.8, 0, 180, 14))
        else:
            shape = Poly(arc_pts(cx, cy0, 9.4 * k + 1.0, 6.4 * k + 1.0, 0, 180, 18))
        cel(draw, shape, col, sh=None, lw=0.7)
    if d:
        _anu_wig(draw, r)
    head_skull(r, draw, ANU_SKIN, ears=False)
    if r.back:
        _anu_wig(draw, r)
    # tall jackal ears, gold inside, up out of the wig
    for s in ((-1, 1) if not d else (-d, d)):
        near = not d or s == -d
        ex = hx + s * 5.8 if not d else hx - d * 2.4 + s * 2.4
        lean = s * 1.8 if not d else -d * 2.0
        cel(draw, Poly([(ex - 3.5, hy - 6.0), (ex + lean, hy - 21.4), (ex + 3.5, hy - 6.0)]),
            ANU_SKIN if near else shade(ANU_SKIN, 0.4), sh=(0.5, 0.5))
        if not r.back and near:
            Poly([(ex - 1.7, hy - 7.2), (ex + lean * 0.86, hy - 17.4), (ex + 1.7, hy - 7.2)]).draw(draw, fill=ANU_GOLD)
    if not r.back:
        # the skull again over the ears' roots, then the long jackal snout
        cel(draw, Ell(hx, hy, rx, ry), ANU_SKIN, sh=(0.9, 0.8), hi=(0.7, 0.6))
    if not r.back and d:
        snout = Poly([(hx + d * 3.0, hy - 1.4), (hx + d * 13.0, hy + 1.8), (hx + d * 13.6, hy + 4.2), (hx + d * 12.6, hy + 5.2), (hx + d * 4.0, hy + 6.4)])
        cel(draw, snout, ANU_SKIN, sh=(0.0, 0.9), hi=(0.0, 0.7))
        cel(draw, Ell(hx + d * 13.1, hy + 2.8, 1.3, 1.1), VOID, sh=None, line=False)
        Ell(hx + d * 12.8, hy + 2.3, 0.5, 0.35).draw(draw, fill=(150, 152, 190))
        stroke(draw, [(hx + d * 5.0, hy + 4.9), (hx + d * 12.0, hy + 4.3)], 0.6, ANU_GOLD)
        e_x = hx + d * 2.6
        Poly([(e_x - d * 2.4, hy - 1.6), (e_x + d * 2.8, hy - 2.8), (e_x + d * 2.4, hy + 0.0), (e_x - d * 1.8, hy + 0.4)]).draw(draw, fill=ANU_GOLD)
        Ell(e_x + d * 0.8, hy - 1.1, 1.0, 0.9).draw(draw, fill=(40, 30, 20))
        stroke(draw, [(e_x - d * 2.2, hy - 0.4), (e_x - d * 4.6, hy + 0.8)], 0.8, ANU_GOLD)
    elif not r.back:
        snout = Poly([(hx - 3.8, hy + 0.6), (hx + 3.8, hy + 0.6), (hx + 2.6, hy + 9.2), (hx, hy + 11.0), (hx - 2.6, hy + 9.2)])
        cel(draw, snout, ANU_SKIN, sh=(0.8, 0.6), hi=(0.7, 0.4))
        cel(draw, Ell(hx, hy + 8.9, 1.7, 1.2), VOID, sh=None, line=False)
        Ell(hx - 0.5, hy + 8.4, 0.6, 0.4).draw(draw, fill=(150, 152, 190))
        for s in (-1, 1):
            ex = hx + s * 5.6
            Poly([(ex - s * 2.9, hy - 0.2), (ex - s * 0.6, hy - 2.6), (ex + s * 3.0, hy - 2.8), (ex + s * 2.3, hy + 0.5), (ex - s * 1.6, hy + 0.8)]).draw(
                draw, fill=ANU_GOLD)
            Ell(ex, hy - 0.9, 1.0, 1.0).draw(draw, fill=(40, 30, 20))
            stroke(draw, [(ex + s * 2.6, hy - 1.0), (ex + s * 5.0, hy + 0.8)], 0.8, ANU_GOLD)
    for side in _sides(r):
        rig_hand(r, draw, side, ANU_SKIN, out=out if (not d and side == st_side) else 0.0)


# ===================================================================
# YOKAI (97) -- a kitsune: fox ears, a fox mask, three tails, foxfire
# ===================================================================

YK_WHITE = (250, 248, 250)
YK_RED = (216, 50, 60)
YK_FOX = (242, 132, 52)         # her fur: the tails, the ears and her hair. White hair on a white kosode under a white
                                # mask was one pale mass with no fox in it, and gone on the snow
YK_TIP = (92, 54, 48)           # the ears' dark tips
YK_INNER = (255, 236, 222)      # inside the ears
YK_EYE = (58, 146, 246)         # the foxfire's blue
YK_FIRE = ((80, 160, 255), (140, 210, 255), (230, 246, 255))
# cut at the jaw, so it ends above the shoulders and the tails are not behind a curtain of it
YK_STYLE = dict(vol=1.9, fringe=((-0.72, 4.0), (-0.28, 5.2), (0.18, 5.0), (0.62, 4.2)), sweep=0.7, side=9.6, back=9.6, lock=3.4)


def _yk_curve(pts, widths, n=5):
    """A smooth curve through `pts` (Catmull-Rom, n steps a span) and a half-width for each of its points."""
    ext = [pts[0]] + list(pts) + [pts[-1]]
    out, w = [], []
    for i in range(1, len(ext) - 2):
        p0, p1, p2, p3 = ext[i - 1], ext[i], ext[i + 1], ext[i + 2]
        for k in range(n):
            t = k / float(n)
            out.append(tuple(0.5 * (2 * p1[j] + (p2[j] - p0[j]) * t + (2 * p0[j] - 5 * p1[j] + 4 * p2[j] - p3[j]) * t * t +
                                    (3 * p1[j] - p0[j] - 3 * p2[j] + p3[j]) * t * t * t) for j in (0, 1)))
            w.append(widths[i - 1] + (widths[i] - widths[i - 1]) * t)
    return out + [tuple(pts[-1])], w + [widths[-1]]


def _yk_tail(draw, pts):
    """One fox tail from its root through `pts`: a fat brush coming to a point, its last third white."""
    c, w = _yk_curve(pts, [1.7, 3.9, 3.8, 0.7])
    tip = pts[-1]
    cel(draw, Limb(c, w), YK_FOX, sh=(0.8, 0.8), regions=[(Ell(tip[0], tip[1], 5.6, 5.6), YK_WHITE)])


def _yk_ear(draw, ex, by, lean, k=1.0, inner=True, far=False):
    """A fox's ear standing on (ex, by): tall, broad at the foot, its tip dark and its inside pale.
    The old ones were 6 units of orange spike on white hair: an oni's horns."""
    col = YK_FOX if not far else shade(YK_FOX, 0.7)
    tip = (ex + lean, by - 11.6)
    pts = bez((ex - 3.8 * k, by + 1.0), (ex - 3.4 * k + lean * 0.3, by - 7.0), tip, 5) + \
        bez(tip, (ex + 3.4 * k + lean * 0.5, by - 7.0), (ex + 3.8 * k, by + 1.0), 5)[1:]
    cel(draw, Poly(pts), col, sh=(0.5, 0.5),
        regions=[(Poly([(ex - 9, by - 7.0), (ex + 9, by - 7.0), (ex + 9, by - 14), (ex - 9, by - 14)]), YK_TIP if not far else shade(YK_TIP, 0.6))])
    if inner:
        Poly([(ex - 2.0 * k, by - 0.4), (ex + lean * 0.5, by - 6.4), (ex + 2.0 * k, by - 0.4)]).draw(draw, fill=YK_INNER)


def _yk_mask(draw, mx, my, ang):
    """The fox mask pushed up on the side of her head: white, its eyes, nose and ear tips in red."""
    def T(pts):
        return xform(pts, mx, my, ang)
    cel(draw, Poly(T([(-3.9, -2.6), (-3.0, -6.2), (-1.3, -3.4), (1.3, -3.4), (3.0, -6.2), (3.9, -2.6), (3.3, 1.6), (0.0, 5.0), (-3.3, 1.6)])),
        YK_WHITE, sh=(0.5, 0.5), lw=0.8)
    for s in (-1, 1):
        Poly(T([(s * 3.0, -6.0), (s * 3.6, -3.6), (s * 2.0, -4.4)])).draw(draw, fill=YK_RED)
        stroke(draw, T([(s * 2.7, -1.2), (s * 0.9, -0.3)]), 1.0, YK_RED)
    Poly(T(arc_pts(0.0, 3.3, 0.9, 0.8, 0, 360)[:-1])).draw(draw, fill=YK_RED)


def _yk_sleeve(r, draw, side):
    """A kosode's sleeve: wide from the shoulder, the hand at its mouth and a pouch of cloth hanging
    behind the wrist, a red cord through the hem."""
    near = r.near(side)
    col = YK_WHITE if near else shade(YK_WHITE, 0.8)
    s, _, w, _ = arm_pts(r, side)
    k = r.d if r.d else side            # which way the mouth of the sleeve faces: forward in profile, outward head-on
    pts = [(s[0] - k * 2.2, s[1] - 1.5), (s[0] + k * 2.0, s[1] - 1.4), (w[0] + k * 2.6, w[1] - 0.2), (w[0] + k * 1.4, w[1] + 1.5),
           (w[0] - k * 3.4, w[1] + 5.0), (w[0] - k * 3.8, w[1] - 1.4)]
    cord = Poly([(w[0] + k * 2.2, w[1] + 0.2), (w[0] - k * 4.4, w[1] + 4.6), (w[0] - k * 4.4, w[1] + 3.2), (w[0] + k * 2.2, w[1] - 1.2)])
    cel(draw, Poly(pts), col, sh=(0.9, 0.6), regions=[(cord, YK_RED if near else shade(YK_RED, 0.8))])
    rig_hand(r, draw, side, SKIN, hand_r=2.0)


def draw_yokai(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=0.94)
    d = r.d
    wag = [0.0, 1.0, 0.0, -1.0][frame]
    cx, hip = r.cx, r.hip_y
    hx, hy = r.hx, r.head_cy

    def tails():
        # three, each big enough to be a tail, fanned so that every one of them shows
        if d:
            def P(u, v):
                return (cx - d * u, hip - v)
            _yk_tail(draw, [P(3.0, -1.0), P(8.0, -4.0), P(14.0, -5.6 - wag * 0.5), P(19.6, -3.6 - wag)])
            _yk_tail(draw, [P(3.0, 1.0), P(8.0, 4.4), P(14.4, 8.4 + wag * 0.6), P(18.4, 14.6 + wag)])
            _yk_tail(draw, [P(3.0, 0.0), P(9.4, 0.4), P(16.4, 1.6 - wag * 0.6), P(21.6, 5.6 - wag)])
        elif r.back:
            # from behind they are the nearest thing to the camera: a fan of three over her back
            for s in (-1, 1):
                _yk_tail(draw, [(cx + s * 1.0, hip + 0.6), (cx + s * 9.0, hip - 2.0), (cx + s * (16.2 + wag * 0.6 * s), hip - 7.4),
                                (cx + s * 19.4 + wag, hip - 14.4)])
            _yk_tail(draw, [(cx, hip + 1.6), (cx + wag * 0.4, hip - 4.0), (cx + wag * 1.0, hip - 9.0), (cx + wag * 1.8, hip - 13.6)])
        else:
            _yk_tail(draw, [(cx - 1.0, hip + 1.0), (cx - 9.0, hip + 3.4), (cx - 16.0, hip + 3.6 + wag * 0.5), (cx - 21.0, hip - 0.4 + wag)])
            _yk_tail(draw, [(cx - 1.0, hip), (cx - 9.6, hip - 2.6), (cx - 16.6, hip - 8.0 - wag * 0.6), (cx - 19.4, hip - 15.0 - wag)])
            _yk_tail(draw, [(cx + 1.0, hip), (cx + 9.6, hip - 1.6), (cx + 16.8, hip - 6.4 + wag * 0.6), (cx + 20.0, hip - 13.0 + wag)])

    if d:
        _yk_sleeve(r, draw, -d)
    if not r.back:
        tails()
    # a shrine maiden's white kosode and red hakama
    rig_legs(r, draw, YK_RED, (60, 50, 60))
    rig_torso(r, draw, YK_WHITE)
    rig_robe(r, draw, YK_RED, top=r.waist_y + 0.4, hem=r.base_y - 2.6, flare=3.0, split=True)
    rig_belt(r, draw, shade(YK_RED, 0.9), buckle=GOLD)
    if not r.back:
        cel(draw, Poly([(cx - 2.8 + d * 1.6, r.sh_y - 2.2), (cx + 2.8 + d * 1.6, r.sh_y - 2.2), (cx + d * 1.6, r.sh_y + 3.0)]), YK_RED, sh=None, lw=0.6)
    for side in _sides(r):
        _yk_sleeve(r, draw, side)
    if r.back:
        tails()
    by = hy - 10.2
    if d:
        _yk_ear(draw, hx + d * 4.2, by, -d * 1.2, 0.8, inner=False, far=True)
    rig_head(r, draw, SKIN, hair=YK_FOX, style=YK_STYLE, eye_color=YK_EYE, expression="smirk", mood="sharp")
    if d:
        _yk_ear(draw, hx - d * 1.6, by, -d * 1.8, 0.82)
    else:
        for s in (-1, 1):
            _yk_ear(draw, hx + s * 6.8, by, s * 2.0, inner=not r.back)
    if r.back:
        # the mask's red cord round the back of her head, tied in a bow: what she has behind her
        clipped(draw, Ell(hx, hy - 0.6, r.head_rx + 1.7, r.head_ry + 1.7),
                lambda l: stroke(l, bez((hx - 15.0, hy - 3.4), (hx, hy + 6.2), (hx + 15.0, hy - 3.4), 8), 1.4, YK_RED))
        by2 = hy + 1.6
        for s in (-1, 1):
            cel(draw, Poly([(hx + s * 0.6, by2 + 0.6), (hx + s * 3.0, by2 + 6.0), (hx + s * 0.8, by2 + 5.0)]), YK_RED, sh=None, lw=0.6)
            cel(draw, Poly([(hx, by2), (hx + s * 4.6, by2 - 2.8), (hx + s * 5.0, by2 + 2.0)]), YK_RED, sh=(0.4, 0.4), lw=0.6)
        cel(draw, Ell(hx, by2, 1.4, 1.4), YK_RED, sh=None, lw=0.6)
    elif d:
        stroke(draw, [(hx - d * 8.6, hy - 3.4), (hx - d * 14.0, hy - 2.6)], 1.2, YK_RED)
        _yk_mask(draw, hx - d * 5.8, hy - 3.6, -d * 8.0)
    else:
        _yk_mask(draw, hx + 9.4, hy - 4.6, 16.0)
    # foxfire: two flames that hover either side of her (ahead and behind, in profile) and bob in
    # turn. They used to circle her, and on every stride both sat on her mouth and chest
    bob = [0.0, 1.4, 0.0, -1.4][frame]
    if d:
        spots = ((cx + d * 14.6, r.sh_y + 3.0 + bob), (cx - d * 19.6, hip - 21.0 - bob))
    else:
        spots = ((cx - 19.2, hy + 5.0 + bob), (cx + 19.2, hy + 5.0 - bob))
    for i, (fx, fy) in enumerate(spots):
        flame(draw, fx, fy, 5.4, 8.0, YK_FIRE, sway=wag * (1 if i else -1))


# ===================================================================
# GOLEM (98) -- stacked stone, glowing runes, boulder fists
# ===================================================================

GOL = (150, 146, 140)
GOL_MID = (126, 122, 120)       # the block under the chest and the forearms: three tones of stone, so the stack reads as a stack
GOL_DK = (100, 96, 98)
GOL_RUNE = (110, 230, 240)
GOL_MOSS = (110, 164, 82)
GOL_GROOVE = (58, 62, 80)       # what the runes are cut into the stone with

# The runes, as strokes about their middle: one on the chest, one on the back, one on each shoulder
# (which is the one a profile shows). Few strokes and wide ones: at 0.7 they were hairlines in game,
# and the chest's ring with a fork in it was a peace sign.
GOL_CHEST = (((-2.7, 4.4), (3.1, -0.7), (0.0, -3.7), (-3.1, -0.7), (2.7, 4.4)),)
GOL_BACK = (((0.0, -4.4), (0.0, 4.5)), ((-3.3, -4.2), (0.0, -0.4), (3.3, -4.2)))
GOL_SHOULDER = (((0.0, -2.1), (1.9, 0.0), (0.0, 2.1), (-1.9, 0.0), (0.0, -2.1)),)


def _stone(draw, x, y, rx, ry, color=GOL, jitter=(1.0, 0.92, 1.06, 0.96, 1.02, 0.9), n=8, moss=False):
    pts = []
    for i in range(n):
        a = math.radians(i * 360.0 / n + 20)
        k = jitter[i % len(jitter)]
        pts.append((x + rx * k * math.cos(a), y + ry * k * math.sin(a)))
    cel(draw, Poly(pts), color, sh=(1.0, 1.0), hi=(0.6, 0.6))
    if moss:
        cel(draw, Poly([(x - rx * 0.8, y - ry * 0.5), (x - rx * 0.2, y - ry * 0.96), (x + rx * 0.5, y - ry * 0.8), (x, y - ry * 0.4)]),
            GOL_MOSS, sh=None, line=False)


def _gol_rune(draw, x, y, strokes, color, w=1.2):
    """A rune about (x, y): each stroke a dark groove in the stone with the light lying in it."""
    for pts in strokes:
        stroke(draw, [(x + u, y + v) for (u, v) in pts], w + 1.2, GOL_GROOVE)
    for pts in strokes:
        stroke(draw, [(x + u, y + v) for (u, v) in pts], w, color)


def draw_golem(draw, ox, oy, direction, frame):
    d, back, base, cx, ph = creature_setup(ox, oy, direction, frame)
    ground = oy + 54
    glow = [0, 1, 0, 1][frame]
    rune = GOL_RUNE if glow else lit(GOL_RUNE, 0.5)
    # It stomps. The foot that stands stays on the ground and the one that steps comes well up;
    # head-on the whole weight leans over the standing leg, and the fists swing against the legs
    bx = cx + (ph * 1.0 if not d else 0.0)
    # stubby block legs
    for s in ((-1, 1) if not d else (-d, d)):
        near = not d or s == d
        fwd = ph * s if not d else (ph if near else -ph)
        if d:
            lx, ly = cx + d * fwd * 2.8 + s * 1.0, ground - (1.8 if fwd > 0 else 0.0)
        else:
            lx, ly = cx + s * 5.6, ground - (2.4 if fwd < 0 else 0.0)
        cel(draw, RRect(lx - 4.0, ly - 7.6, lx + 4.0, ly + 0.2, 1.6), GOL_DK if near else shade(GOL_DK, 0.6), sh=(0.6, 0.6))

    # an arm: a shoulder boulder, a smaller stone, a great dark fist hanging to the knee
    def arm(s, near):
        fwd = -ph * s if not d else (-ph if near else ph)
        tone = (lambda c: c) if near else (lambda c: shade(c, 0.6))
        if d:
            # at his side, a little behind the chest, and swinging its whole length with the walk
            sx, sy = cx - d * 1.6, base - 22.0
            fx, fy = sx + d * (1.0 + fwd * 3.4), base - 9.0 - abs(fwd) * 0.8
        else:
            sx, sy = bx + s * 12.4, base - 22.0
            fx, fy = sx + s * 1.6, base - 9.4 - fwd * 1.6
        _stone(draw, (sx + fx) / 2.0, (sy + fy) / 2.0 - 0.6, 3.8, 3.4, tone(GOL_MID))
        _stone(draw, sx, sy, 5.0, 4.2, tone(GOL))
        if d and near:
            _gol_rune(draw, sx, sy + 0.1, GOL_SHOULDER, rune, 1.1)
        _stone(draw, fx, fy, 5.6, 5.2, tone(GOL_DK))

    if d:
        arm(-d, False)
    # the body: a broad block of a chest standing on a smaller one, chest out in profile
    bw = 11.4 if not d else 9.4
    tx = bx + d * 1.0
    belly = Poly([(bx - bw + 3.0, base - 15.6), (bx + bw - 3.0, base - 15.6), (bx + bw - 2.2, base - 12.0), (bx + bw - 3.2, base - 7.6),
                  (bx - bw + 3.2, base - 7.6), (bx - bw + 2.2, base - 12.0)])
    cel(draw, belly, GOL_MID, sh=(1.4, 1.0), regions=[(Poly([(bx - 20, base - 16.0), (bx + 20, base - 16.0), (bx + 20, base - 12.6), (bx - 20, base - 12.6)]),
                                                         shade(GOL_MID))])
    chest = Poly([(tx - bw + 1.4, base - 26.0), (tx + bw - 1.4, base - 26.0), (tx + bw + 0.6, base - 21.4), (tx + bw - 1.0, base - 14.0),
                  (tx - bw + 1.0, base - 14.0), (tx - bw - 0.6, base - 21.4)])
    cel(draw, chest, GOL, sh=(1.8, 1.4), hi=(1.0, 1.0))
    if back:
        _gol_rune(draw, tx, base - 20.0, GOL_BACK, rune)
        stroke(draw, [(tx + bw - 2.2, base - 19.6), (tx + bw - 4.4, base - 17.6), (tx + bw - 3.4, base - 15.4)], 0.9, shade(GOL, 1.8))
    elif not d:
        _gol_rune(draw, tx, base - 20.2, GOL_CHEST, rune)
        stroke(draw, [(tx - bw + 1.6, base - 19.4), (tx - bw + 3.8, base - 17.4), (tx - bw + 2.8, base - 15.2)], 0.9, shade(GOL, 1.8))
    else:
        stroke(draw, [(tx + d * (bw - 1.6), base - 19.8), (tx + d * (bw - 3.6), base - 17.6), (tx + d * (bw - 2.8), base - 15.4)], 0.9, shade(GOL, 1.8))
    # moss on the shoulders
    blob(draw, [Ell(tx - bw + 2.6, base - 26.0, 3.6, 1.6), Ell(tx + bw - 2.6, base - 26.2, 3.0, 1.4)] if not d else
         [Ell(tx - d * 4.0, base - 26.0, 3.6, 1.6)], GOL_MOSS, sh=(0.0, 0.6))
    # the head: a big squarish stone with a heavy brow and glowing eyes
    hx, hy = bx + d * 2.4, base - 32.4
    head = Poly([(hx - 8.4, hy - 5.6), (hx - 6.0, hy - 8.0), (hx + 6.0, hy - 8.0), (hx + 8.4, hy - 5.6), (hx + 8.8, hy + 3.4),
                 (hx + 6.4, hy + 6.4), (hx - 6.4, hy + 6.4), (hx - 8.8, hy + 3.4)])
    cel(draw, head, GOL, sh=(1.4, 1.2), hi=(0.8, 0.8))
    if back:
        # from behind the moss hangs down the back of its head, and the stone is cracked under it
        cap = [(-8.6, -5.4), (-6.2, -8.6), (-2.0, -9.6), (3.6, -9.2), (6.6, -8.2), (8.6, -5.4), (8.6, -3.6), (-8.6, -3.6)]
        blob(draw, [Poly([(hx + u, hy + v) for (u, v) in cap]), Ell(hx - 5.2, hy - 3.2, 3.5, 2.7), Ell(hx + 0.8, hy - 2.4, 3.1, 3.7),
                    Ell(hx + 5.9, hy - 3.6, 2.8, 2.1)], GOL_MOSS, sh=(0.8, 1.0))
        stroke(draw, [(hx + 4.6, hy + 1.6), (hx + 3.0, hy + 3.4), (hx + 4.4, hy + 5.2)], 0.9, shade(GOL, 1.8))
    else:
        blob(draw, [Ell(hx - 2.0, hy - 7.6, 5.4, 2.2), Ell(hx + 3.6, hy - 7.4, 3.6, 1.8)], GOL_MOSS, sh=(0.0, 0.6))
    # a little sprout growing out of the moss
    sx0 = hx + 1.4 - d * 1.0
    stroke(draw, [(sx0, hy - 8.8), (sx0 + 0.4, hy - 12.0)], 0.6, shade(GOL_MOSS, 1.2))
    leaf(draw, sx0 + 0.4, hy - 11.6, -30, 3.6, 2.2, lit(GOL_MOSS, 0.6), vein=False)
    leaf(draw, sx0 + 0.2, hy - 11.2, -150, 3.0, 2.0, GOL_MOSS, vein=False)
    if not back:
        # the brow ledge casting the eyes into shadow, eyes glowing out of it
        bx0 = hx + d * 2.0
        bw2 = 7.4 if not d else 5.4
        cel(draw, RRect(bx0 - bw2, hy - 3.6, bx0 + bw2, hy - 1.4, 0.8), GOL_DK, sh=None)
        Poly([(bx0 - bw2 + 0.6, hy - 1.4), (bx0 + bw2 - 0.6, hy - 1.4), (bx0 + bw2 - 1.4, hy + 1.8), (bx0 - bw2 + 1.4, hy + 1.8)]).draw(
            draw, fill=shade(GOL, 1.6))
        for s in ((-1, 1) if not d else (d, -d)):
            k = 1.0 if not d or s == d else 0.6
            ex = bx0 + s * 3.4 * (1 if not d else 0.9)
            cel(draw, Ell(ex, hy + 0.2, 1.9 * k, 1.4), rune, sh=None, line=False)
            Ell(ex - 0.4 * k, hy - 0.1, 0.6 * k, 0.5).draw(draw, fill=(240, 255, 255))
        stroke(draw, [(bx0 - 2.4, hy + 4.0), (bx0 + 2.4, hy + 4.0)], 1.0, shade(GOL, 1.8))
    for s in ((-1, 1) if not d else (d,)):
        arm(s, True)


# ===================================================================
# DJINN (99) -- blue skin, a topknot, gold bangles, a smoke tail from a lamp
# ===================================================================

from sprite_base import torso_shape as _dj_torso  # noqa: E402  (the vest is cut to it)

DJ_SKIN = (100, 146, 230)
DJ_VEST = (130, 60, 150)
DJ_SMOKE = (160, 146, 232)      # deeper than it was: (186, 176, 240) is the snow's own value in the engine
DJ_HAIR = (40, 36, 60)
DJ_SASH = (220, 60, 80)


def _dj_lamp(draw, x, g, k):
    """The lamp, standing on the ground at g: its spout comes up to a mouth just the -k side of x,
    where the smoke pours out of it (the smoke is drawn first, the spout's tip over its end); the
    bowl, the lid and the handle lie out toward k. One shape for every facing, mirrored by k."""
    bx = x + k * 5.4
    a0, a1 = (-112, 112) if k > 0 else (292, 68)
    cel(draw, Limb(arc_pts(bx + k * 5.3, g - 2.9, 2.1, 2.1, a0, a1, 10), 0.8, cap=False), GOLD, sh=None, lw=0.6)
    cel(draw, Poly([(bx - 2.2, g - 0.9), (bx + 2.2, g - 0.9), (bx + 2.9, g + 0.5), (bx - 2.9, g + 0.5)]), shade(GOLD, 0.9), sh=None, lw=0.7)
    blob(draw, [Ell(bx, g - 2.6, 5.2, 2.5), Limb([(x + k * 1.6, g - 2.6), (x - k * 1.6, g - 3.2), (x - k * 4.0, g - 4.7)], [1.9, 1.5, 1.0])],
         GOLD, sh=(0.6, 0.9))
    stroke(draw, [(bx - 2.6, g - 3.7), (bx - 0.2, g - 4.3)], 0.7, lit(GOLD, 1.8))
    cel(draw, Ell(bx + k * 0.2, g - 4.9, 2.7, 1.1), shade(GOLD, 0.9), sh=None, lw=0.7)
    cel(draw, Ell(bx + k * 0.2, g - 6.2, 0.9, 0.8), GOLD, sh=None, lw=0.6)


def _dj_tail(draw, x, y0, g, k, wave):
    """Where his legs would be: smoke, as wide as his waist under the sash at (x, y0) and
    narrowing in one curl down into the lamp's mouth. One plain shape: there are nine units
    between his sash and the lamp, and puffs that size are a ragged edge (the two it had beside
    the lamp, drawn as outlined circles, were a pair of chain links)."""
    # a gentle curve, and narrow by the time it turns: a wide stroke round a tight bend folds over itself
    pts = bez((x, y0), (x + k * (1.8 + wave * 1.1), y0 + 5.0), (x - k * 3.4, g - 5.1), 8)
    w = [6.6, 6.3, 5.6, 4.7, 3.8, 2.9, 2.1, 1.5, 1.0]
    core = Limb([(px - 0.9, py - 0.2) for (px, py) in pts[:7]], [3.0, 2.9, 2.5, 2.0, 1.5, 1.0, 0.4])
    cel(draw, Limb(pts, w, cap=True, cap0=True), DJ_SMOKE, sh=(1.2, 1.0), regions=[(core, lit(DJ_SMOKE, 1.0))])


def draw_djinn(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=1.12, bob=[-1, -2, -2, -1][frame], step=0)
    d = r.d
    wave = [0.0, 1.0, 0.0, -1.0][frame]
    cx = r.cx
    g = r.base_y - r.bob                         # the ground: he bobs over it, the lamp does not
    k = -d if d else (-1 if r.back else 1)      # which way the lamp lies: behind him in profile
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    aw = 2.2 * 1.12
    waist = r.waist_y + 2.4
    # smoke out of the lamp, and him out of the smoke
    _dj_tail(draw, cx, waist - 0.6, g, k, wave)
    _dj_lamp(draw, cx, g, k)
    if frame % 2 == 0:
        sparkle(draw, cx + k * 8.4, g - 5.4, 1.9, (255, 250, 214))

    def arm(s, col):
        """An arm akimbo: the elbow out, the fist on the hip, a gold bangle on the wrist."""
        if d:
            near = col == DJ_SKIN
            sh_, el = r.shoulder(s), (cx - d * (4.4 if near else 5.8), r.sh_y + (5.0 if near else 4.4))
            wr = (cx + d * (1.0 if near else 0.0), r.waist_y + 0.4)
        else:
            sh_, el, wr = (cx + s * (r.sh_w - 1.3), r.sh_y + 0.4), (cx + s * (r.sh_w + 5.2), r.sh_y + 4.8), (cx + s * (r.hip_w + 0.6), r.waist_y + 0.2)
        cel(draw, Limb([sh_, el, wr], [aw, aw * 0.92, aw * 0.82]), col, sh=(0.8, 0.4))
        t0, t1 = 0.5, 0.82
        cel(draw, Limb([(el[0] + (wr[0] - el[0]) * t0, el[1] + (wr[1] - el[1]) * t0), (el[0] + (wr[0] - el[0]) * t1, el[1] + (wr[1] - el[1]) * t1)],
                       [aw * 0.98, aw * 0.94], cap=False), GOLD if col == DJ_SKIN else shade(GOLD, 0.6), sh=(0.4, 0.3), lw=0.6)
        cel(draw, Ell(wr[0] - (0.0 if d else s * 0.5), wr[1] + 0.9, 2.3, 2.3), col, sh=(0.6, 0.6))

    if d:
        arm(-d, shade(DJ_SKIN, 0.6))
    # a bare chest in an open vest, gold along its edges
    torso = _dj_torso(r, bottom=waist)
    top = torso.pts[0][1]
    if r.back:
        cel(draw, torso, DJ_VEST, sh=(1.5, 0.9))
        stroke(draw, [(cx - r.hip_w + 0.8, waist - 2.4), (cx + r.hip_w - 0.8, waist - 2.4)], 0.9, GOLD)
    else:
        cel(draw, torso, DJ_SKIN, sh=(1.5, 0.9), regions=[(Ell(hx, top + 0.4, rx * 0.62, 2.3), shade(DJ_SKIN))], line=False)
        if d:
            panels = [Poly([(cx - d * 8.0, top - 1.0), (cx + d * 1.4, top - 1.0), (cx + d * 2.8, waist + 1.0), (cx - d * 8.0, waist + 1.0)])]
            trims = [[(cx + d * 1.4, top), (cx + d * 2.8, waist)]]
        else:
            panels = [Poly([(cx + s * 12.0, top - 1.0), (cx + s * 4.4, top - 1.0), (cx + s * 1.5, waist + 1.0), (cx + s * 12.0, waist + 1.0)]) for s in (-1, 1)]
            trims = [[(cx + s * 4.4, top), (cx + s * 1.5, waist)] for s in (-1, 1)]

        def vest(l):
            for p in panels:
                cel(l, p, DJ_VEST, sh=(1.3, 0.8), line=False)
            for t in trims:
                stroke(l, t, 1.0, GOLD)
        clipped(draw, torso, vest)
        torso.draw(draw, fill=None, outline=ink(DJ_VEST), width=1.0)
    rig_belt(r, draw, DJ_SASH, buckle=GOLD)
    for s in _sides(r):
        arm(s, DJ_SKIN)
    # pointed ears with a gold hoop in each, the head, a little goatee
    if not d:
        for s in (-1, 1):
            cel(draw, Poly([(hx + s * (rx - 2.2), hy - 1.4), (hx + s * (rx + 3.9), hy - 3.2), (hx + s * (rx - 0.8), hy + 4.4)]), DJ_SKIN, sh=(0.4, 0.5),
                regions=[] if r.back else [(Poly([(hx + s * (rx - 0.6), hy - 0.6), (hx + s * (rx + 2.3), hy - 1.9), (hx + s * (rx - 0.2), hy + 2.4)]), shade(DJ_SKIN, 1.2))])
    rig_head(r, draw, DJ_SKIN, hair=None, eye_color=(250, 214, 80), expression="grin", mood="sharp", blush=False, ears=False)
    if d:
        # well back from the eye, and plain: nearer and dark inside it was an arrowhead pointing at his eye
        ear = bez((hx - d * 4.6, hy + 0.2), (hx - d * 3.2, hy + 4.0), (hx - d * 6.2, hy + 4.6), 4) + [(hx - d * 10.2, hy - 1.2)]
        cel(draw, Poly(ear), DJ_SKIN, sh=(0.4, 0.5))
    if not r.back:
        bx = hx + d * 5.6
        by = hy + ry - (2.4 if not d else 3.0)
        cel(draw, Poly([(bx - 1.5, by), (bx + 1.5, by - (0.4 if d else 0.0)), (bx + d * 0.8, by + 4.0)]), DJ_HAIR, sh=None, lw=0.6)
    for s in ((-1, 1) if not d else (-d,)):
        ex = hx + s * (rx + 0.2) if not d else hx - d * 5.4
        layer = draw.sub()
        Ell(ex, hy + (5.8 if not d else 6.8), 1.5, 1.7).draw(layer, fill=None, outline=GOLD, width=0.8)
        draw.merge(layer)
    # the topknot: a gold band on his crown and a plume of black hair out of it, which stands over
    # his brow from the front, arcs back over his head in profile and hangs down his neck from behind
    y0 = hy - ry - 0.2
    if d:
        pts = bez((hx - d * 0.8, y0 - 0.4), (hx - d * 3.0, y0 - 9.4), (hx - d * 10.8, y0 - 2.6), 5) + \
            bez((hx - d * 10.8, y0 - 2.6), (hx - d * 15.8, y0 + 2.4), (hx - d * (13.6 + wave), hy + 4.0), 5)[1:]
        cel(draw, Limb(pts, [1.9, 2.4, 2.8, 3.0, 3.0, 2.9, 2.8, 2.6, 2.2, 1.5, 0.5]), DJ_HAIR, sh=(0.7, 0.8))
        cel(draw, Ell(hx - d * 0.9, y0 + 0.3, 2.7, 1.4), GOLD, sh=(0.4, 0.4), lw=0.7)
    elif r.back:
        # off to one side and curling in to his nape: straight down the middle it was a seam splitting his head in two
        fall = bez((hx - 0.6, y0 - 1.0), (hx - 6.6 - wave, hy - 1.0), (hx - 1.4 - wave * 1.6, hy + 10.6), 6)
        blob(draw, [Ell(hx, y0 - 2.0, 2.8, 2.4), Limb(fall, [2.4, 3.0, 3.3, 3.2, 2.7, 1.9, 0.6])], DJ_HAIR, sh=(0.8, 0.9))
        cel(draw, Ell(hx, y0, 2.9, 1.4), GOLD, sh=(0.4, 0.4), lw=0.7)
    else:
        tip = (hx + 0.6 + wave * 0.9, y0 - 8.8)
        plume = bez((hx - 1.8, y0 - 0.4), (hx - 4.8, y0 - 4.6), tip, 6) + bez(tip, (hx + 4.6, y0 - 4.0), (hx + 1.8, y0 - 0.4), 6)[1:]
        cel(draw, Poly(plume), DJ_HAIR, sh=(0.8, 0.8))
        cel(draw, Ell(hx, y0, 2.9, 1.4), GOLD, sh=(0.4, 0.4), lw=0.7)


# ===================================================================
# FENRIR (100) -- the monstrous wolf: a dark hide under a ragged mane and
# hackles, burning red eyes, a maw of fangs, broken shackles on its forelegs
# ===================================================================

FEN = (70, 66, 84)
FEN_MANE = (104, 92, 118)      # ruff, hackles, tail and the snout's top plane
FEN_EYE = (255, 60, 60)
FEN_SOCKET = (150, 28, 48)
FEN_MAW = (150, 30, 40)
FEN_CHAIN = (176, 182, 200)
FEN_CLAW = (226, 222, 214)


def _fen_spikes(cx, cy, r0, tips, half=0.34):
    """Tufts of fur as fat triangles round (cx, cy): `tips` are (angle in degrees, reach)."""
    out = []
    for (a, r1) in tips:
        t = math.radians(a)
        out.append(Poly([(cx + math.cos(t - half) * r0, cy + math.sin(t - half) * r0),
                         (cx + math.cos(t) * r1, cy + math.sin(t) * r1),
                         (cx + math.cos(t + half) * r0, cy + math.sin(t + half) * r0)]))
    return out


def _fen_shackle(draw, x, y, w, frame, trail=0, links=3, near=True):
    """A broken shackle round a leg: a bright iron band and, with `trail`, the links still
    hanging off it, swinging with the stride."""
    col = FEN_CHAIN if near else shade(FEN_CHAIN, 0.8)
    sw = [0.0, 1.0, 0.0, -1.0][frame]
    if trail:
        for i in range(links):
            lx, ly = x + trail * (w + 0.8 + i * 1.9) + sw * (i + 1) * 0.45, y + 1.5 + i * 1.2
            cel(draw, Ell(lx, ly, 1.5 if i % 2 else 1.1, 1.1 if i % 2 else 1.5), col, sh=None, lw=0.5)
    cel(draw, RRect(x - w - 0.7, y - 1.3, x + w + 0.7, y + 1.3, 0.8), col, sh=(0.0, 0.6), hi=(0.0, 0.5), lw=0.6)


def _fen_paw(draw, x, y, d, color, r=3.1, claws=True):
    """A heavy paw, its claws short and on the ground rather than under it."""
    cel(draw, Ell(x + d * 1.0, y, r, r * 0.62), color, sh=(0.0, 0.6))
    if claws:
        for k in ((-1, 0, 1) if not d else (0, 1)):
            cx_ = x + (k * 1.5 if not d else d * (r + 0.5 - k * 1.3))
            Poly([(cx_ - 0.6, y + 0.5), (cx_ + 0.6, y + 0.5), (cx_ + d * 0.7, y + 1.7)]).draw(draw, fill=FEN_CLAW)


def _fen_ears(draw, hx, hy, d, back=False):
    """Tall ears; the one on its left is torn."""
    for s in ((-1, 1) if not d else (-d, d)):
        near = not d or s == -d
        ex = hx + s * 6.4 if not d else hx - d * 2.6 + s * 2.6
        lean = s * 1.8 if not d else -d * 2.4
        col = FEN if near else shade(FEN, 0.6)
        ear = [(ex - 3.2, hy - 4.4), (ex + lean, hy - 15.0), (ex + 3.2, hy - 4.4)]
        if (s < 0 and not d) or (d and near):
            # a bite out of its outer edge
            k = -1 if not d else d
            ear = [(ex - 3.2, hy - 4.4), (ex + lean, hy - 15.0), (ex + k * -1.0 + lean * 0.5, hy - 10.4),
                   (ex + k * -0.2 + lean * 0.4, hy - 9.0), (ex + k * -2.2 + lean * 0.2, hy - 8.0), (ex + 3.2, hy - 4.4)] if k < 0 else \
                  [(ex - 3.2, hy - 4.4), (ex - 2.2 + lean * 0.2, hy - 8.0), (ex - 0.2 + lean * 0.4, hy - 9.0),
                   (ex - 1.0 + lean * 0.5, hy - 10.4), (ex + lean, hy - 15.0), (ex + 3.2, hy - 4.4)]
        cel(draw, Poly(ear), col, sh=(0.5, 0.5))
        if not back and not d:
            Poly([(ex - 1.5, hy - 5.4), (ex + lean * 0.7, hy - 11.4), (ex + 1.5, hy - 5.4)]).draw(draw, fill=FEN_MANE)


def _fen_head(draw, hx, hy, d, frame, back=False):
    _fen_ears(draw, hx, hy, d, back)
    if d:
        _fen_head_side(draw, hx, hy, d)
        return
    # the ruff: big ragged tufts out from the cheeks, hackles up between the ears
    tufts = _fen_spikes(hx, hy + 1.5, 7.6, [(206, 13.0), (174, 14.6), (142, 13.6), (38, 13.6), (6, 14.6), (-26, 13.0)], 0.36) + \
        _fen_spikes(hx, hy + 1.5, 8.0, [(250, 13.2), (270, 14.4), (290, 13.2)], 0.2)
    blob(draw, tufts + [Ell(hx, hy + 2.0, 10.2, 9.0)], FEN_MANE, sh=(1.2, 1.0))
    cel(draw, Ell(hx, hy, 9.4, 8.4), FEN, sh=(1.0, 1.0))
    if back:
        return
    # the snout's top plane a step lighter; a black nose; the maw open on four fangs
    cel(draw, Ell(hx, hy + 3.8, 5.6, 4.9), FEN_MANE, sh=(0.6, 0.6))
    cel(draw, Poly([(hx - 4.2, hy + 4.2), (hx + 4.2, hy + 4.2), (hx + 3.2, hy + 8.2), (hx - 3.2, hy + 8.2)]), FEN_MAW, sh=None, lw=0.6)
    cel(draw, Ell(hx, hy + 1.7, 2.3, 1.6), VOID, sh=None, line=False)
    Ell(hx - 0.7, hy + 1.2, 0.7, 0.4).draw(draw, fill=(150, 150, 176))
    for s in (-1, 1):
        Poly([(hx + s * 2.9 - 0.9, hy + 4.2), (hx + s * 2.9 + 0.9, hy + 4.2), (hx + s * 2.7, hy + 6.9)]).draw(draw, fill=TOOTH)
        Poly([(hx + s * 1.5 - 0.6, hy + 8.2), (hx + s * 1.5 + 0.6, hy + 8.2), (hx + s * 1.5, hy + 6.3)]).draw(draw, fill=TOOTH)
        # eyes that burn: a dark red socket, the eye, a hot core; a heavy brow knitted over them
        ex = hx + s * 4.6
        Poly([(ex - s * 2.9, hy - 0.0), (ex - s * 0.9, hy - 3.2), (ex + s * 3.2, hy - 3.6), (ex + s * 2.9, hy - 0.2), (ex - s * 1.2, hy + 1.2)]).draw(
            draw, fill=FEN_SOCKET)
        Poly([(ex - s * 2.2, hy - 0.3), (ex - s * 0.6, hy - 2.5), (ex + s * 2.5, hy - 2.8), (ex + s * 2.2, hy - 0.5), (ex - s * 1.0, hy + 0.5)]).draw(
            draw, fill=FEN_EYE)
        Ell(ex + s * 0.3, hy - 1.3, 0.8, 0.7).draw(draw, fill=(255, 236, 200))
        stroke(draw, [(ex + s * 3.8, hy - 5.0), (ex + s * 0.4, hy - 4.2), (ex - s * 3.2, hy - 1.2)], 1.3, ink(FEN))


def _fen_head_side(draw, hx, hy, d):
    def X(u):
        return hx + d * u
    # the ruff: tufts back and down from the head, round to a beard under the jaw
    back_a = (lambda a: a) if d < 0 else (lambda a: 180.0 - a)
    tufts = _fen_spikes(hx - d * 1.0, hy + 1.5, 7.4, [(back_a(a), r) for (a, r) in ((-48, 13.0), (-18, 14.4), (14, 14.0), (46, 13.4), (80, 12.4))], 0.36)
    blob(draw, tufts + [Ell(hx - d * 1.0, hy + 1.6, 9.4, 8.8)], FEN_MANE, sh=(1.2, 1.0))
    # the lower jaw, hanging open
    cel(draw, Poly([(X(1.0), hy + 5.6), (X(3.6), hy + 3.2), (X(11.6), hy + 6.4), (X(11.2), hy + 8.2), (X(4.0), hy + 8.6)]), FEN, sh=(0.0, 0.7))
    cel(draw, Ell(hx, hy, 8.4, 7.8), FEN, sh=(1.0, 1.0))
    # the upper jaw: a long snout, its top plane a step lighter
    snout = Poly([(X(2.0), hy - 3.4), (X(9.4), hy - 2.0), (X(12.8), hy - 1.4), (X(13.6), hy + 0.4), (X(12.8), hy + 2.2), (X(4.0), hy + 3.4),
                  (X(2.0), hy + 3.0)])
    cel(draw, snout, FEN, sh=None,
        regions=[(Poly([(X(2.0), hy - 3.6), (X(13.8), hy - 1.6), (X(13.8), hy - 0.2), (X(2.0), hy - 0.6)]), FEN_MANE)])
    cel(draw, Ell(X(12.9), hy - 0.4, 1.5, 1.2), VOID, sh=None, line=False)
    Ell(X(12.5), hy - 0.9, 0.5, 0.35).draw(draw, fill=(150, 150, 176))
    # the maw between them, a long fang down and one up
    Poly([(X(4.4), hy + 3.3), (X(12.6), hy + 2.2), (X(11.4), hy + 6.3), (X(4.2), hy + 4.4)]).draw(draw, fill=FEN_MAW)
    for (u, v0, v1, w) in ((10.6, 2.4, 5.2, 0.9), (7.6, 2.9, 4.6, 0.7), (9.4, 5.6, 3.2, 0.8)):
        Poly([(X(u - w), hy + v0), (X(u + w), hy + v0 - (0.2 if v1 > v0 else -0.2)), (X(u), hy + v1)]).draw(draw, fill=TOOTH)
    # the eye that burns, under a heavy brow
    Poly([(X(-0.2), hy - 3.0), (X(5.2), hy - 1.6), (X(4.6), hy + 0.8), (X(0.2), hy + 0.4)]).draw(draw, fill=FEN_SOCKET)
    Poly([(X(0.6), hy - 2.3), (X(4.6), hy - 1.2), (X(4.0), hy + 0.3), (X(0.8), hy - 0.1)]).draw(draw, fill=FEN_EYE)
    Ell(X(2.8), hy - 0.9, 0.8, 0.6).draw(draw, fill=(255, 236, 200))
    stroke(draw, [(X(-1.6), hy - 4.6), (X(2.6), hy - 3.8), (X(6.2), hy - 2.2)], 1.3, ink(FEN))


def _fen_leg(draw, x0, x1, top, base, d, lift, near, hind, frame, trail=0):
    """One heavy leg in profile, shackled above the paw."""
    col = FEN if near else shade(FEN, 0.6)
    fy = base - 2.0 - lift
    knee = ((x0 + x1) / 2.0 - (d * 1.6 if hind else -d * 0.4), (top + fy) / 2.0 + (0.8 if hind else 0.0))
    cel(draw, Limb([(x0, top), knee, (x1, fy)], [3.3 if hind else 2.9, 2.5, 2.3]), col, sh=(0.7, 0.0))
    _fen_paw(draw, x1, fy + 0.7, d, col, claws=near)
    if not hind:
        _fen_shackle(draw, x1 + (knee[0] - x1) * 0.3, fy - 2.6, 2.3, frame, trail=trail, near=near)


def draw_fenrir(draw, ox, oy, direction, frame):
    d, back, base, cx, ph = creature_setup(ox, oy, direction, frame, bob=[0, -2, 0, -2])
    sway = [0.0, 1.0, 0.0, -1.0][frame]
    if d:
        def X(u):
            return cx + d * u
        # a great ragged brush of a tail, held high
        tail = [(X(-10.0), base - 16.0), (X(-14.6), base - 18.4), (X(-18.2), base - 21.6 + sway * 0.5), (X(-20.4), base - 25.6 + sway),
                (X(-20.8), base - 29.4 + sway * 1.3)]
        blob(draw, [Limb(tail, [2.4, 3.6, 4.0, 2.8, 0.5]),
                    Poly([(X(-15.0), base - 16.6), (X(-18.6), base - 16.4 + sway * 0.4), (X(-17.2), base - 20.0 + sway * 0.4)]),
                    Poly([(X(-18.6), base - 19.4 + sway * 0.5), (X(-22.6), base - 20.4 + sway * 0.8), (X(-20.6), base - 23.6 + sway * 0.8)])],
             FEN_MANE, sh=(0.9, 0.9))

        def leg(u, phase, near, hind):
            swing = ph * phase * 2.6
            _fen_leg(draw, X(u), X(u + swing), base - 12.0, base, d, 1.2 if ph * phase < 0 else 0.0, near, hind, frame,
                     trail=(-d if near and not hind else 0))
        leg(6.4, 1, False, False)
        leg(-8.0, -1, False, True)
        # the body: a mountain of shoulder falling away to the hips
        blob(draw, [Ell(X(3.4), base - 17.2, 8.4, 8.2), Ell(X(-3.0), base - 15.2, 10.2, 6.6), Ell(X(-8.6), base - 14.4, 5.6, 5.8)],
             FEN, sh=(1.4, 1.2))
        leg(-8.0, 1, True, True)
        leg(6.4, -1, True, False)
        # hackles down the spine
        spine = [(X(5.0), base - 25.0), (X(0.0), base - 25.0), (X(-5.4), base - 22.0), (X(-10.0), base - 19.2)]
        ridge = [Limb(spine, [2.0, 1.8, 1.4, 0.8])]
        for (u, v, h) in ((2.6, -25.4, 5.2), (-1.8, -24.6, 4.6), (-5.6, -22.4, 3.8), (-9.0, -20.2, 3.0)):
            ridge.append(Poly([(X(u + 2.2), base + v + 0.8), (X(u - 2.6), base + v - h), (X(u - 2.4), base + v + 1.4)]))
        blob(draw, ridge, FEN_MANE, sh=(0.8, 0.8))
        _fen_head(draw, X(9.6), base - 24.6, d, frame)
        return
    if back:
        for s in (-1, 1):
            lift = 1.4 if ph * s < 0 else 0.0
            cel(draw, Limb([(cx + s * 5.6, base - 11.0), (cx + s * 6.0, base - 2.0 - lift)], [3.2, 2.7]), FEN, sh=(0.6, 0.0))
            _fen_paw(draw, cx + s * 6.0, base - 1.3 - lift, 0, FEN, claws=False)
        cel(draw, Ell(cx, base - 14.0, 11.2, 8.4), FEN, sh=(1.4, 1.2))
        # the mane lies over the shoulders in ragged points; the tail is swung up to one side
        mantle = [(-9.6, -23.0), (9.6, -23.0), (10.6, -17.0), (7.4, -18.6), (5.6, -13.6), (2.8, -17.2), (0.0, -12.0), (-2.8, -17.2),
                  (-5.6, -13.6), (-7.4, -18.6), (-10.6, -17.0)]
        cel(draw, Poly([(cx + u, base + v) for (u, v) in mantle]), FEN_MANE, sh=(0.9, 0.9))
        _fen_head(draw, cx, base - 27.0, 0, frame, back=True)
        tail = [(cx + 1.0, base - 10.6), (cx - 4.6, base - 9.6), (cx - 9.6, base - 11.0 + sway * 0.4), (cx - 13.4 - sway * 0.5, base - 14.6 + sway * 0.4),
                (cx - 15.4 - sway, base - 19.0 + sway * 0.4)]
        blob(draw, [Limb(tail, [2.4, 3.4, 3.8, 2.8, 0.5]),
                    Poly([(cx - 6.0, base - 7.4), (cx - 10.4, base - 6.4 + sway * 0.4), (cx - 10.0, base - 9.6)])], FEN_MANE, sh=(0.9, 0.9))
        return
    # head-on: the hind legs either side behind a barrel chest, the forelegs in irons
    for s in (-1, 1):
        cel(draw, Limb([(cx + s * 9.0, base - 9.0), (cx + s * 9.4, base - 2.0)], [2.6, 2.3]), shade(FEN, 0.6), sh=None)
        _fen_paw(draw, cx + s * 9.4, base - 1.3, 0, shade(FEN, 0.6), r=2.7, claws=False)
    cel(draw, Ell(cx, base - 13.0, 10.4, 7.6), FEN, sh=(1.4, 1.0))
    for s in (-1, 1):
        lift = 1.6 if ph * s < 0 else 0.0
        cel(draw, Limb([(cx + s * 4.8, base - 13.0), (cx + s * 4.8, base - 2.0 - lift)], [3.2, 2.8]), FEN, sh=(0.7, 0.0))
        _fen_paw(draw, cx + s * 4.8, base - 1.3 - lift, 0, FEN)
        _fen_shackle(draw, cx + s * 4.8, base - 5.6 - lift, 2.7, frame, trail=s, links=2)
    # the chest tuft under the chin
    blob(draw, [Poly([(cx - 5.6, base - 19.0), (cx + 5.6, base - 19.0), (cx + 4.4, base - 12.6), (cx + 2.4, base - 14.8), (cx, base - 10.4),
                      (cx - 2.4, base - 14.8), (cx - 4.4, base - 12.6)])], FEN_MANE, sh=(0.8, 0.8))
    _fen_head(draw, cx, base - 27.0, 0, frame)


# ===================================================================
# CHIMERA (101) -- a lion with a mane of fire, a goat's head rising from
# its back, a snake for a tail: the lion in front, the goat above and
# behind it, the snake last, each clear of the others
# ===================================================================

CHI_LION = (232, 170, 84)
CHI_MANE = ((226, 70, 40), (255, 140, 50), (255, 214, 100))
CHI_GOAT = (238, 234, 226)
CHI_HORN = (150, 128, 112)
CHI_SNAKE = (96, 170, 90)


def _chi_goat(draw, x, y, d, back=False):
    """The goat's head: white, horns swept back, ears out sideways, a beard."""
    if d:
        for (off, near) in ((1.6, False), (0.0, True)):
            hb = (x - d * 0.4 + d * off, y - 3.6)
            cel(draw, Limb([hb, (hb[0] - d * 2.6, hb[1] - 4.2), (hb[0] - d * 5.8, hb[1] - 5.6), (hb[0] - d * 8.6, hb[1] - 4.0)],
                           [1.8, 1.5, 1.1, 0.3]), CHI_HORN if near else shade(CHI_HORN, 0.7), sh=(0.4, 0.5))
        cel(draw, Poly([(x - d * 2.6, y - 2.4), (x - d * 7.4, y - 0.6), (x - d * 3.0, y + 1.0)]), CHI_GOAT, sh=(0.3, 0.4))
        blob(draw, [Ell(x, y, 5.0, 4.6), Ell(x + d * 4.0, y + 1.6, 3.2, 2.5)], CHI_GOAT, sh=(0.7, 0.7))
        cel(draw, Poly([(x + d * 1.6, y + 3.6), (x + d * 4.4, y + 3.8), (x + d * 2.6, y + 7.6)]), CHI_GOAT, sh=None, lw=0.6)
        Ell(x + d * 6.3, y + 1.0, 0.8, 0.9).draw(draw, fill=(214, 150, 150))
        Ell(x + d * 1.8, y - 0.8, 1.0, 1.2).draw(draw, fill=VOID)
        Ell(x + d * 1.5, y - 1.2, 0.4, 0.4).draw(draw, fill=(255, 255, 255))
        return
    for s in (-1, 1):
        _horn(draw, (x + s * 2.4, y - 3.4), s, CHI_HORN, 0.56, 0.92, 1.8)
        cel(draw, Poly([(x + s * 3.8, y - 2.0), (x + s * 8.4, y - 0.2), (x + s * 4.2, y + 1.4)]), CHI_GOAT, sh=(0.3, 0.4))
    cel(draw, Ell(x, y, 5.4, 4.9), CHI_GOAT, sh=(0.7, 0.7))
    if back:
        return
    cel(draw, Poly([(x - 1.5, y + 4.0), (x + 1.5, y + 4.0), (x, y + 8.0)]), CHI_GOAT, sh=None, lw=0.6)
    cel(draw, Ell(x, y + 2.6, 2.7, 2.0), (246, 222, 214), sh=None, lw=0.5)
    for s in (-1, 1):
        Ell(x + s * 0.9, y + 2.3, 0.45, 0.4).draw(draw, fill=(150, 96, 100))
        Ell(x + s * 2.7, y - 0.9, 1.0, 1.25).draw(draw, fill=VOID)
        Ell(x + s * 2.7 - 0.35, y - 1.3, 0.4, 0.4).draw(draw, fill=(255, 255, 255))


def _chi_snake(draw, pts, look, frame):
    """The tail: a green snake, its head on the end looking the way `look` points."""
    cel(draw, Limb(pts, [2.2, 2.0, 1.8, 1.7]), CHI_SNAKE, sh=(0.5, 0.5))
    hx, hy = pts[-1]
    cel(draw, Poly([(hx - look * 1.6, hy - 2.2), (hx + look * 1.6, hy - 2.6), (hx + look * 4.6, hy - 0.4), (hx + look * 1.8, hy + 1.8),
                    (hx - look * 1.6, hy + 1.6)]), CHI_SNAKE, sh=(0.4, 0.4))
    Ell(hx + look * 1.5, hy - 0.9, 0.75, 0.75).draw(draw, fill=(255, 226, 70))
    Ell(hx + look * 1.7, hy - 0.9, 0.3, 0.55).draw(draw, fill=VOID)
    if frame % 2 == 0:
        stroke(draw, [(hx + look * 4.4, hy - 0.2), (hx + look * 6.4, hy + 0.5)], 0.6, (230, 60, 80))


def _chi_paw(draw, x, y, d, color):
    cel(draw, Ell(x + d * 0.9, y, 2.6, 1.7), color, sh=(0.0, 0.6))


def _chi_legs(draw, cx, base, d, ph, far):
    """The lion's legs in profile, trotting: diagonal pairs together. Clawless paws: the kit's
    hang their claws under the ground line."""
    for (lx, phase, near) in ((5.6, 1, False), (-6.6, -1, False), (5.6, -1, True), (-6.6, 1, True)):
        if near == far:
            continue
        swing = ph * phase * 2.6
        lift = 1.2 if ph * phase < 0 else 0.0
        col = CHI_LION if near else shade(CHI_LION, 0.6)
        x0 = cx + d * lx
        x1 = x0 + d * swing
        cel(draw, Limb([(x0, base - 11.0), (x0 + d * swing * 0.4, base - 6.4), (x1, base - 1.9 - lift)], [2.6, 2.2, 2.0]), col, sh=(0.6, 0.0))
        _chi_paw(draw, x1, base - 1.3 - lift, d, col)


def _chi_face(draw, hx, hy, d):
    """The lion's face: round ears, a pale muzzle, a snarl."""
    if d:
        cel(draw, Ell(hx - d * 2.6, hy - 6.0, 2.4, 2.4), CHI_LION, sh=(0.4, 0.4))
        cel(draw, Ell(hx, hy, 7.4, 6.8), CHI_LION, sh=(0.8, 0.8), hi=(0.5, 0.5))
        cel(draw, Ell(hx + d * 5.0, hy + 2.0, 3.8, 3.0), lit(CHI_LION, 0.6), sh=(0.0, 0.5))
        Ell(hx + d * 8.0, hy + 1.0, 1.2, 0.9).draw(draw, fill=VOID)
        Poly([(hx + d * 3.4, hy + 3.4), (hx + d * 8.0, hy + 3.4), (hx + d * 7.0, hy + 5.4), (hx + d * 3.8, hy + 5.4)]).draw(draw, fill=(150, 30, 40))
        Poly([(hx + d * 5.8, hy + 3.4), (hx + d * 7.0, hy + 3.4), (hx + d * 6.4, hy + 5.2)]).draw(draw, fill=TOOTH)
        ms_eye(draw, hx + d * 1.6, hy - 1.4, 3.4, 4.0, (200, 60, 30), (float(d), 0.0), "sharp", skin=CHI_LION, side=-d)
        return
    for s in (-1, 1):
        cel(draw, Ell(hx + s * 6.4, hy - 6.2, 2.6, 2.6), CHI_LION, sh=(0.4, 0.4))
        Ell(hx + s * 6.4, hy - 5.9, 1.2, 1.2).draw(draw, fill=shade(CHI_LION, 1.4))
    cel(draw, Ell(hx, hy, 8.4, 7.6), CHI_LION, sh=(1.0, 1.0), hi=(0.6, 0.6))
    cel(draw, Ell(hx, hy + 3.4, 4.6, 3.4), lit(CHI_LION, 0.6), sh=(0.5, 0.5))
    Ell(hx, hy + 1.8, 1.6, 1.1).draw(draw, fill=VOID)
    Poly([(hx - 2.6, hy + 4.2), (hx + 2.6, hy + 4.2), (hx + 1.6, hy + 6.2), (hx - 1.6, hy + 6.2)]).draw(draw, fill=(150, 30, 40))
    for s in (-1, 1):
        Poly([(hx + s * 1.5 - 0.55, hy + 4.2), (hx + s * 1.5 + 0.55, hy + 4.2), (hx + s * 1.5, hy + 5.9)]).draw(draw, fill=TOOTH)
    eye_pair(draw, hx - 3.8, hx + 3.8, hy - 1.4, 0, (200, 60, 30), mood="sharp", skin=CHI_LION, w=3.6, h=4.4)


def draw_chimera(draw, ox, oy, direction, frame):
    d, back, base, cx, ph = creature_setup(ox, oy, direction, frame)
    sway = [0.0, 1.0, 0.0, -1.0][frame]
    if d:
        def X(u):
            return cx + d * u
        _chi_snake(draw, [(X(-9.0), base - 14.0), (X(-14.6), base - 14.6), (X(-16.6), base - 20.0 + sway), (X(-14.0), base - 25.6 + sway * 0.6)],
                   -d, frame)
        _chi_legs(draw, cx, base, d, ph, far=True)
        body = Poly(rot_pts(arc_pts(cx - d * 1.0, base - 14.0, 11.0, 6.6, 0, 360)[:-1], cx, base - 14.0, -d * 4))
        cel(draw, body, CHI_LION, sh=(1.2, 1.2))
        _chi_legs(draw, cx, base, d, ph, far=False)
        # the lion's head in its mane of fire, which burns back along the neck
        hx, hy = X(8.4), base - 22.0
        flame(draw, hx - d * 2.0, hy + 8.0, 20.0, 20.0, CHI_MANE, sway=sway - d * 3.0)
        # the goat rises from the middle of the back, clear of the mane, looking where the lion looks
        cel(draw, Limb([(X(-5.0), base - 18.6), (X(-5.6), base - 24.0), (X(-5.0), base - 28.6)], [2.9, 2.4, 2.2]), CHI_GOAT, sh=(0.6, 0.0))
        _chi_goat(draw, X(-4.4), base - 32.0, d)
        _chi_face(draw, hx, hy, d)
        return
    if back:
        # furthest first: the mane, burning up behind the shoulders
        # (no pale heart from behind: with one it was a campfire on its back)
        flame(draw, cx + 1.0, base - 13.0, 21.0, 21.0, (CHI_MANE[0], CHI_MANE[1], CHI_MANE[1]), sway=sway)
        for s in (-1, 1):
            lift = 1.4 if ph * s < 0 else 0.0
            cel(draw, Limb([(cx + s * 5.0, base - 10.0), (cx + s * 5.2, base - 2.0 - lift)], [2.6, 2.3]), CHI_LION, sh=(0.6, 0.0))
            _chi_paw(draw, cx + s * 5.2, base - 1.3 - lift, 0, CHI_LION)
        cel(draw, Ell(cx, base - 14.0, 10.0, 7.4), CHI_LION, sh=(1.2, 1.2))
        # the goat's neck and the back of its head, then the snake, nearest, off the rump
        cel(draw, Limb([(cx - 3.6, base - 17.6), (cx - 5.4, base - 22.0), (cx - 6.0, base - 25.6)], [2.9, 2.4, 2.2]), CHI_GOAT, sh=(0.6, 0.0))
        _chi_goat(draw, cx - 6.4, base - 29.4, 0, back=True)
        _chi_snake(draw, [(cx + 1.0, base - 11.0), (cx + 5.6 + sway, base - 15.0), (cx + 8.6 + sway, base - 21.6), (cx + 7.0 + sway * 0.6, base - 27.4)],
                   1, frame)
        return
    # head-on: the snake rears over one shoulder, the goat rises behind the other
    _chi_snake(draw, [(cx + 6.0, base - 12.0), (cx + 12.6, base - 15.6 + sway), (cx + 14.6, base - 23.6), (cx + 12.0, base - 30.6 + sway * 0.6)],
               1, frame)
    cel(draw, Limb([(cx - 4.0, base - 20.0), (cx - 6.6, base - 28.0), (cx - 7.0, base - 33.0)], [2.9, 2.4, 2.2]), CHI_GOAT, sh=(0.6, 0.0))
    for s in (-1, 1):
        cel(draw, Limb([(cx + s * 7.6, base - 9.0), (cx + s * 7.8, base - 2.0)], [2.1, 1.9]), shade(CHI_LION, 0.6), sh=None)
        _chi_paw(draw, cx + s * 7.8, base - 1.4, 0, shade(CHI_LION, 0.6))
    cel(draw, Ell(cx, base - 11.0, 8.6, 6.0), CHI_LION, sh=(1.2, 1.0))
    for s in (-1, 1):
        lift = 1.6 if ph * s < 0 else 0.0
        cel(draw, Limb([(cx + s * 4.0, base - 10.0), (cx + s * 4.0, base - 2.0 - lift)], [2.7, 2.3]), CHI_LION, sh=(0.6, 0.0))
        _chi_paw(draw, cx + s * 4.0, base - 1.3 - lift, 0, CHI_LION)
    hx, hy = cx, base - 22.0
    flame(draw, hx, hy + 10.0, 26.0, 24.0, CHI_MANE, sway=sway)
    _chi_goat(draw, cx - 7.2, base - 37.0, 0)
    _chi_face(draw, hx, hy, 0)


MYTHOLOGICAL_DRAW_FUNCTIONS = {
    'minotaur': draw_minotaur,
    'medusa': draw_medusa,
    'cerberus': draw_cerberus,
    'centaur': draw_centaur,
    'kraken': draw_kraken,
    'sphinx': draw_sphinx,
    'cyclops': draw_cyclops,
    'harpy': draw_harpy,
    'griffin': draw_griffin,
    'anubis': draw_anubis,
    'yokai': draw_yokai,
    'golem': draw_golem,
    'djinn': draw_djinn,
    'fenrir': draw_fenrir,
    'chimera': draw_chimera,
}


def main():
    for name, draw_func in MYTHOLOGICAL_DRAW_FUNCTIONS.items():
        generate_character(name, draw_func=draw_func)
    print(f"\nGenerated {len(MYTHOLOGICAL_DRAW_FUNCTIONS)} mythological character sprites.")


if __name__ == "__main__":
    main()
