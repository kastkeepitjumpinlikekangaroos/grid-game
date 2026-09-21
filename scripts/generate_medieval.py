#!/usr/bin/env python3
"""Medieval/Fantasy character sprite generators (IDs 42-56).

Fifteen classes out of a fantasy guild hall, in MapleStory's style: the
kingdom's knights (paladin, crusader, valkyrie), its wild warriors
(berserker, barbarian, monk), its casters (druid, cleric, enchantress,
warlock) and its rogues' gallery (ranger, bard, rogue, jester, inquisitor).
Every one is told apart by the thing on its head and the thing in its hand.
"""

import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from sprite_base import (
    DOWN, UP, LEFT, RIGHT, GOLD, LEATHER, STEEL, WOOD, SKIN, SKIN_TAN, SKIN_BROWN, HAIR_STYLES,
    Ell, Limb, Poly, RRect, arc_pts, blade, blob, bow, cel, flame, gem, generate_character, hand_at,
    head_face, head_skull, ink, leaf, lit, mix, ms_eye, ms_mouth, rig, rig_arms, rig_belt, rig_cape,
    rig_hair, rig_hand, rig_head, rig_hood, rig_legs, rig_robe, rig_torso, round_shield, shade,
    sparkle, spear, star, stroke, xform, arm_pts, face_anchor,
)


def _sides(r):
    return (-1, 1) if not r.d else (r.d,)


def _hold_side(r):
    return (1 if not r.back else -1) if not r.d else r.d


def _off_side(r):
    return -_hold_side(r) if not r.d else -r.d


def kite_shield(draw, x, y, w, h, face, rim=GOLD, emblem=None):
    pts = [(x - w, y - h * 0.5), (x - w * 0.8, y - h * 0.62), (x, y - h * 0.68), (x + w * 0.8, y - h * 0.62),
           (x + w, y - h * 0.5), (x + w * 0.8, y + h * 0.1), (x, y + h * 0.62), (x - w * 0.8, y + h * 0.1)]
    cel(draw, Poly(pts), rim, sh=(0.8, 0.8))
    inner = Poly(pts).scaled(0.78, x, y - h * 0.02)
    cel(draw, inner, face, sh=(1.0, 1.0), line=False)
    if emblem is not None:
        emblem(draw, x, y - h * 0.06)


def cross(draw, x, y, s, color):
    cel(draw, Poly([(x - s * 0.3, y - s), (x + s * 0.3, y - s), (x + s * 0.3, y - s * 0.3), (x + s * 0.8, y - s * 0.3),
                    (x + s * 0.8, y + s * 0.3), (x + s * 0.3, y + s * 0.3), (x + s * 0.3, y + s * 1.2),
                    (x - s * 0.3, y + s * 1.2), (x - s * 0.3, y + s * 0.3), (x - s * 0.8, y + s * 0.3),
                    (x - s * 0.8, y - s * 0.3), (x - s * 0.3, y - s * 0.3)]), color, sh=None, lw=0.6)


def axe(draw, x, y, ang, length=12.0, head=STEEL, haft=WOOD, flip=1.0, big=1.0, double=False):
    """An axe gripped at (x, y), haft along `ang`, the bit on the flip side."""
    cel(draw, Limb(xform([(-3.0, 0.0), (length, 0.0)], x, y, ang), [1.0, 1.0]), haft, sh=None)
    bit = [(length - 5.4 * big, 0.6), (length - 0.4, 0.6), (length + 0.6 * big, 5.6 * big), (length - 3.0 * big, 7.2 * big),
           (length - 6.4 * big, 4.2 * big)]
    faces = [bit] + ([[(u, -v) for (u, v) in bit]] if double else [])
    for pts in faces:
        p = xform(pts, x, y, ang, 1.0, flip)
        cel(draw, Poly(p), head, sh=None, regions=[(Poly(p[1:4]), shade(head, 0.8))])
        stroke(draw, [p[2], p[3]], 0.5, lit(head, 1.4))


def feather_wing(draw, x, y, side, span, color=(250, 250, 255), flap=0.0, tone=None):
    """A folded angel/valkyrie wing from (x, y) spreading to `side`."""
    tipx = x + side * span
    pts = [(x, y - 1.0), (x + side * span * 0.5, y - 8.0 - flap), (tipx, y - 9.0 - flap * 1.4), (tipx - side * 1.4, y - 5.0),
           (tipx + side * 0.4, y - 2.0), (tipx - side * 2.4, y + 0.8), (tipx - side * 1.2, y + 4.0),
           (x + side * span * 0.45, y + 5.4), (x + side * span * 0.2, y + 9.0), (x, y + 5.0)]
    cel(draw, Poly(pts), color, sh=(1.0, 1.0), tone=tone)
    for (a, b) in ((3, 5), (5, 6)):
        stroke(draw, [(x + side * span * 0.3, y), pts[a]], 0.45, shade(color, 1.3))


# ===================================================================
# PALADIN (42) -- winged gold helm, white cape, hammer and cross shield
# ===================================================================

PAL_GOLD = (248, 204, 84)
PAL_WHITE = (242, 244, 250)
PAL_BLUE = (74, 124, 216)


def draw_paladin(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=1.14, head=0.96)
    d = r.d
    hs, os_ = _hold_side(r), _off_side(r)
    rig_cape(r, draw, PAL_WHITE, layer="under")
    if d:
        kite_shield(draw, r.cx - d * 3.6, r.sh_y + 3.0, 5.4, 12.0, PAL_WHITE, emblem=lambda dr, x, y: cross(dr, x, y, 2.6, PAL_GOLD))
        rig_arms(r, draw, PAL_GOLD, PAL_GOLD, layer="far")
    rig_legs(r, draw, PAL_GOLD, shade(PAL_GOLD, 0.8), width=1.08)
    rig_torso(r, draw, PAL_GOLD)
    cx = r.cx
    if not r.back:
        cel(draw, Poly([(cx - 3.4 + d * 2, r.sh_y + 0.2), (cx + 3.4 + d * 2, r.sh_y + 0.2), (cx + 3.8 + d * 2, r.hip_y + 3.0),
                        (cx - 3.8 + d * 2, r.hip_y + 3.0)]), PAL_WHITE, sh=(0.8, 0.0))
        cross(draw, cx + d * 2, r.sh_y + 3.6, 2.0, PAL_BLUE)
    rig_belt(r, draw, PAL_BLUE, buckle=PAL_GOLD)
    rig_arms(r, draw, PAL_GOLD, PAL_GOLD, layer="near", hands=False)
    for side in _sides(r):
        sx, sy = r.shoulder(side)
        cel(draw, Ell(sx + (side * 1.2 if not d else 0), sy - 1.0, 4.6, 3.2), PAL_GOLD, sh=(0.8, 0.8), hi=(0.5, 0.5))
        Ell(sx + (side * 1.2 if not d else 0), sy - 1.2, 1.2, 1.0).draw(draw, fill=PAL_BLUE)
    head_skull(r, draw, SKIN)
    if not r.back:
        head_face(r, draw, SKIN, iris=(70, 120, 200), mood="bright", mouth="smile", brows=False)
    rig_hair(r, draw, (240, 206, 120), "short", hat=True, skin=SKIN)
    # open winged helm
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    for s in ((-1, 1) if not d else (-d,)):
        wx = hx + s * (rx - 0.4) if not d else hx - d * 3.0
        feather_wing(draw, wx, hy - 5.0, s if not d else -d, 8.4, PAL_WHITE)
    dome = arc_pts(hx - d * 0.4, hy - 0.8, rx + 1.2, ry + 1.0, 182, 358, 26)
    cel(draw, Poly(dome + [(hx + rx + 1.3, hy - 1.4), (hx - rx - 1.3, hy - 1.4)]), PAL_GOLD, sh=(1.4, 1.0), hi=(0.8, 0.8))
    if not r.back:
        gem(draw, hx + d * 3.2, hy - 4.4, 1.6, PAL_BLUE)
        stroke(draw, [(hx - rx + 0.4, hy - 1.6), (hx + rx - 0.4, hy - 1.6)] if not d else
               [(hx - d * 1.0, hy - 1.6), (hx + d * (rx - 0.2), hy - 1.6)], 0.9, shade(PAL_GOLD, 1.2))
    rig_cape(r, draw, PAL_WHITE, layer="over")
    # a big golden warhammer
    h = hand_at(r, hs)
    ang = -90 + hs * 16 if not d else (-90 + d * 30)
    x1, y1 = xform([(13.0, 0.0)], h[0], h[1], ang)[0]
    cel(draw, Limb([xform([(-3.0, 0.0)], h[0], h[1], ang)[0], (x1, y1)], [1.0, 1.0]), WOOD, sh=None)
    hd = xform([(10.6, -4.4), (15.6, -4.4), (15.6, 4.4), (10.6, 4.4)], h[0], h[1], ang)
    cel(draw, Poly(hd), PAL_GOLD, sh=None, hi=None, regions=[(Poly([hd[0], hd[1], ((hd[1][0] + hd[2][0]) / 2, (hd[1][1] + hd[2][1]) / 2),
                                                                     ((hd[0][0] + hd[3][0]) / 2, (hd[0][1] + hd[3][1]) / 2)]), lit(PAL_GOLD, 0.8))])
    for side in _sides(r):
        rig_hand(r, draw, side, PAL_GOLD)
    if not d:
        h2 = hand_at(r, os_)
        kite_shield(draw, h2[0] + os_ * 1.6, h2[1] - 3.4, 5.2, 11.6, PAL_WHITE if not r.back else shade(PAL_WHITE, 0.6),
                    emblem=(lambda dr, x, y: cross(dr, x, y, 2.4, PAL_GOLD)) if not r.back else None)


# ===================================================================
# RANGER (43) -- feathered cap, green cape, a big longbow
# ===================================================================

RNG_GREEN = (86, 154, 82)
RNG_DARK = (58, 112, 66)
RNG_LEATHER = (146, 100, 64)
RNG_FEATHER = (226, 60, 56)


def draw_ranger(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=0.96)
    d = r.d
    hs = _hold_side(r)
    rig_cape(r, draw, RNG_DARK, layer="under", length=r.hip_y + 3.0)
    # quiver on the back
    qx = r.cx + (4.4 if not d else -d * 4.0)
    if r.back or d:
        cel(draw, RRect(qx - 2.2, r.sh_y - 4.0, qx + 2.2, r.waist_y + 1.0, 1.0), RNG_LEATHER, sh=(0.5, 0.0))
        for k in (-1.2, 0.2, 1.6):
            stroke(draw, [(qx + k, r.sh_y - 4.0), (qx + k - 0.4, r.sh_y - 7.4)], 0.5, (220, 220, 214))
            Poly([(qx + k - 1.3, r.sh_y - 7.4), (qx + k + 0.5, r.sh_y - 7.4), (qx + k - 0.4, r.sh_y - 9.6)]).draw(draw, fill=RNG_FEATHER)
    if d:
        rig_arms(r, draw, RNG_LEATHER, SKIN, layer="far")
    rig_legs(r, draw, (98, 86, 70), (104, 72, 50))
    rig_torso(r, draw, RNG_GREEN)
    cx = r.cx
    if not r.back:
        stroke(draw, [(cx - 5.0 + d, r.sh_y - 1.6), (cx + 5.0 + d, r.waist_y + 0.6)], 1.2, RNG_LEATHER)
    rig_belt(r, draw, RNG_LEATHER, buckle=GOLD)
    rig_arms(r, draw, RNG_LEATHER, SKIN, layer="near", hands=False)
    rig_head(r, draw, SKIN, hair=(170, 110, 64), style="short", eye_color=(70, 130, 70), expression="smirk", hat=True)
    # the feathered cap: a peaked hunter's hat
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    if d:
        cap = Poly([(hx + d * (rx + 3.0), hy - 3.4), (hx + d * rx, hy - 7.0), (hx, hy - ry - 1.4), (hx - d * (rx - 2.0), hy - ry + 0.6),
                    (hx - d * (rx + 2.4), hy - 4.0), (hx - d * (rx + 1.0), hy - 2.6)])
    else:
        cap = Poly([(hx - rx - 2.0, hy - 2.4), (hx - rx + 1.0, hy - 7.6), (hx - 2.0, hy - ry - 1.2), (hx + 5.0, hy - ry - 0.2),
                    (hx + rx + 3.4, hy - 4.2), (hx + rx + 1.0, hy - 2.4)])
    cel(draw, cap, RNG_GREEN, sh=(1.4, 1.0))
    stroke(draw, [(hx - rx - 1.0, hy - 3.0), (hx + rx + 0.6, hy - 3.0)] if not d else
           [(hx - d * (rx + 0.6), hy - 3.4), (hx + d * (rx + 1.4), hy - 3.4)], 1.0, RNG_DARK)
    fx = hx - (5.0 if not d else d * 5.0)
    cel(draw, Limb([(fx, hy - 4.4), (fx - (3.0 if not d else d * 3.0), hy - 10.0), (fx - (7.4 if not d else d * 8.0), hy - 13.4)],
                   [1.2, 1.8, 0.3]), RNG_FEATHER, sh=None)
    rig_cape(r, draw, RNG_DARK, layer="over", length=r.hip_y + 3.0)
    for side in _sides(r):
        h = rig_hand(r, draw, side, SKIN, reach=0.2 if side == hs else 0.0)
        if side == hs:
            bow(draw, h[0], h[1] - 4.0, d, 24.0, side=side)
            rig_hand(r, draw, side, SKIN, reach=0.2)


# ===================================================================
# BERSERKER (44) -- a wolf pelt for a hood, warpaint, twin axes
# ===================================================================

BER_WOLF = (152, 150, 162)
BER_HAIR = (212, 72, 42)
BER_PAINT = (70, 110, 222)
BER_FUR = (122, 92, 66)


def draw_berserker(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=1.24, head=0.96)
    d = r.d
    if d:
        rig_arms(r, draw, SKIN_TAN, SKIN_TAN, layer="far")
    rig_legs(r, draw, (96, 76, 66), (96, 70, 50))
    rig_torso(r, draw, SKIN_TAN)
    cx = r.cx
    if not r.back:
        stroke(draw, [(cx - 3.4 + d, r.sh_y + 1.4), (cx - 0.6 + d, r.sh_y + 2.8)], 0.5, shade(SKIN_TAN, 1.3))
        stroke(draw, [(cx + 3.4 + d, r.sh_y + 1.4), (cx + 0.6 + d, r.sh_y + 2.8)], 0.5, shade(SKIN_TAN, 1.3))
        stroke(draw, [(cx - 4.0 + d, r.waist_y - 2.6), (cx + 4.0 + d, r.waist_y - 2.6)], 1.2, BER_PAINT)
    # fur kilt
    cel(draw, Poly([(cx - r.hip_w - 0.6, r.waist_y + 0.6), (cx + r.hip_w + 0.6, r.waist_y + 0.6), (cx + r.hip_w + 1.8, r.hip_y + 3.6),
                    (cx + 3.0, r.hip_y + 2.4), (cx, r.hip_y + 4.0), (cx - 3.0, r.hip_y + 2.4), (cx - r.hip_w - 1.8, r.hip_y + 3.6)])
        if not d else Poly([(cx - 5.0, r.waist_y + 0.6), (cx + 5.2, r.waist_y + 0.6), (cx + 6.0, r.hip_y + 3.4), (cx - 6.0, r.hip_y + 3.6)]),
        BER_FUR, sh=(0.8, 0.6))
    rig_belt(r, draw, (84, 60, 44), buckle=(206, 206, 214))
    rig_arms(r, draw, SKIN_TAN, SKIN_TAN, layer="near", hands=False)
    for side in _sides(r):
        _, e, w, h = arm_pts(r, side)
        stroke(draw, [((e[0] + w[0]) / 2 - 1.8, (e[1] + w[1]) / 2), ((e[0] + w[0]) / 2 + 1.8, (e[1] + w[1]) / 2 + 0.4)], 0.9, BER_PAINT)
    head_skull(r, draw, SKIN_TAN, ears=False)
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    if not r.back:
        head_face(r, draw, SKIN_TAN, iris=(90, 140, 220), mood="sharp", mouth="grin", brows=True, brow_color=shade(BER_HAIR, 1.2))
        e1, e2, ey, _, _ = face_anchor(r)
        for ex in ((e1, e2) if not d else (e1,)):
            out = 1 if ex > hx else -1
            for k in (3.6, 5.2):
                stroke(draw, [(ex - 1.2 + out * 0.8, ey + k), (ex + 1.4 + out * 1.4, ey + k - 0.3)], 0.8, BER_PAINT)
    rig_hair(r, draw, BER_HAIR, "wild", hat=True, skin=SKIN_TAN)
    # the wolf pelt: its head on his, ears up, the hide down his back
    pelt_back = Poly([(hx - rx - 1.6, hy - 2.0), (hx + rx + 1.6, hy - 2.0), (hx + rx + 3.0, r.sh_y + 4.0), (hx - rx - 3.0, r.sh_y + 4.0)]) \
        if not d else Poly([(hx - d * 2.0, hy - 4.0), (hx - d * (rx + 2.0), hy - 3.0), (hx - d * (rx + 4.0), r.sh_y + 6.0), (hx - d * 2.0, r.sh_y + 3.0)])
    if r.back or d:
        cel(draw, pelt_back, BER_WOLF, sh=(1.2, 0.8))
    for s in ((-1, 1) if not d else (-d,)):
        ex = hx + s * 6.4 if not d else hx - d * 2.4
        cel(draw, Poly([(ex - 2.4, hy - ry + 2.0), (ex + (s * 1.6 if not d else -d * 1.6), hy - ry - 5.0), (ex + 2.4, hy - ry + 2.0)]),
            BER_WOLF, sh=(0.4, 0.4))
        Poly([(ex - 1.0, hy - ry + 1.4), (ex + (s * 1.2 if not d else -d * 1.2), hy - ry - 2.8), (ex + 1.0, hy - ry + 1.4)]).draw(
            draw, fill=(236, 170, 170))
    wolf = arc_pts(hx - d * 0.4, hy - 1.6, rx + 1.6, ry + 0.6, 180, 360, 24)
    snout = [(hx + rx + 1.8, hy - 1.6), (hx + 4.6, hy - 0.4), (hx + 2.4, hy + 0.6), (hx, hy - 0.2), (hx - 2.4, hy + 0.6),
             (hx - 4.6, hy - 0.4), (hx - rx - 1.8, hy - 1.6)] if not d else \
        ([(hx + d * (rx + 4.4), hy - 2.4), (hx + d * (rx + 3.0), hy + 0.2), (hx + d * 3.0, hy - 0.8), (hx - d * (rx + 1.6), hy - 1.0)])
    if d and d < 0:
        snout = snout[::-1]
    cel(draw, Poly(wolf + (snout if not d or d > 0 else snout)), BER_WOLF, sh=(1.2, 1.0), hi=(0.6, 0.6))
    if not r.back:
        for s in ((-1, 1) if not d else (d,)):
            ex = hx + s * 5.2 if not d else hx + d * 5.4
            Ell(ex, hy - 5.6 if not d else hy - 4.2, 1.2, 0.9).draw(draw, fill=(250, 214, 80))
            Ell(ex, hy - 5.6 if not d else hy - 4.2, 0.5, 0.7).draw(draw, fill=(40, 30, 30))
        if d:
            Ell(hx + d * (rx + 3.8), hy - 1.8, 1.2, 1.0).draw(draw, fill=(40, 34, 40))
        else:
            # the wolf's muzzle over his brow, nose and fangs
            cel(draw, Poly([(hx - 3.2, hy - 5.8), (hx + 3.2, hy - 5.8), (hx + 2.4, hy - 1.4), (hx - 2.4, hy - 1.4)]),
                lit(BER_WOLF, 0.5), sh=(0.0, 0.6))
            cel(draw, Ell(hx, hy - 4.8, 1.5, 1.0), (40, 34, 40), sh=None, line=False)
            for s in (-1, 1):
                Poly([(hx + s * 1.8 - 0.5, hy - 1.6), (hx + s * 1.8 + 0.5, hy - 1.6), (hx + s * 1.8, hy + 0.2)]).draw(
                    draw, fill=(250, 248, 236))
    # twin hand axes
    for side in _sides(r):
        h = hand_at(r, side)
        ang = -90 + side * 30 if not d else -90 + d * 40
        axe(draw, h[0], h[1], ang, 9.0, flip=-side if not d else -d)
        rig_hand(r, draw, side, SKIN_TAN)
    if d:
        h = hand_at(r, -d)


# ===================================================================
# CRUSADER (45) -- great helm with a cross visor, red cross, kite shield
# ===================================================================

CRU_STEEL = (196, 204, 220)
CRU_RED = (206, 44, 52)
CRU_WHITE = (244, 244, 248)


def draw_crusader(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=1.16)
    d = r.d
    hs, os_ = _hold_side(r), _off_side(r)
    rig_cape(r, draw, CRU_RED, layer="under")
    if d:
        kite_shield(draw, r.cx - d * 3.6, r.sh_y + 3.2, 5.6, 12.6, CRU_WHITE, rim=CRU_STEEL,
                    emblem=lambda dr, x, y: cross(dr, x, y, 2.8, CRU_RED))
        rig_arms(r, draw, CRU_STEEL, CRU_STEEL, layer="far")
    rig_legs(r, draw, CRU_STEEL, shade(CRU_STEEL, 1.0), width=1.08)
    rig_torso(r, draw, CRU_STEEL)
    cx = r.cx
    # white surcoat with the red cross
    if d:
        sur = Poly([(cx - d * 2.6, r.sh_y - 1.6), (cx + d * 3.8, r.sh_y - 1.2), (cx + d * 4.8, r.hip_y + 3.6), (cx - d * 3.6, r.hip_y + 3.6)])
    else:
        sur = Poly([(cx - 4.4, r.sh_y - 1.8), (cx + 4.4, r.sh_y - 1.8), (cx + 5.2, r.hip_y + 3.8), (cx - 5.2, r.hip_y + 3.8)])
    cel(draw, sur, CRU_WHITE, sh=(0.8, 0.6))
    if not r.back or True:
        cross(draw, cx + d * 1.6, r.sh_y + 2.6, 2.4, CRU_RED)
    rig_belt(r, draw, (100, 70, 50), buckle=GOLD)
    rig_arms(r, draw, CRU_STEEL, CRU_STEEL, layer="near", hands=False)
    for side in _sides(r):
        sx, sy = r.shoulder(side)
        cel(draw, Ell(sx + (side * 1.0 if not d else 0), sy - 0.8, 4.2, 2.8), CRU_STEEL, sh=(0.6, 0.6), hi=(0.4, 0.4))
    head_skull(r, draw, CRU_STEEL, ears=False)
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    helm = Poly([(hx - rx - 0.4, hy - ry + 4.4), (hx - rx * 0.6, hy - ry + 0.2), (hx + rx * 0.6, hy - ry + 0.2),
                 (hx + rx + 0.4, hy - ry + 4.4), (hx + rx + 0.2, hy + ry - 1.4), (hx - rx - 0.2, hy + ry - 1.4)])
    cel(draw, helm, CRU_STEEL, sh=(1.8, 1.2), hi=(1.0, 1.0))
    # a red plume
    px = hx - d * 2.0
    cel(draw, Limb([(px, hy - ry + 0.6), (px - d * 3.0 - 1.0, hy - ry - 4.0), (px - d * 7.0 - 2.0, hy - ry - 5.0)], [1.8, 2.2, 0.6]),
        CRU_RED, sh=(0.5, 0.5))
    if not r.back:
        vx = hx + d * 3.2
        w = rx - 2.0 if not d else rx * 0.62
        stroke(draw, [(vx - w, hy + 0.6), (vx + w, hy + 0.6)], 1.5, (40, 38, 54))
        stroke(draw, [(vx, hy - 4.4), (vx, hy + 6.4)], 1.4, (40, 38, 54))
        for s in ((-1, 1) if not d else (d,)):
            ex = vx + s * 3.6 if not d else vx + d * 2.8
            Ell(ex, hy + 0.6, 0.7, 0.5).draw(draw, fill=(255, 255, 255))
        for k in (-1, 1):
            for j in range(3):
                Ell(vx + k * 2.4 + d * 0.4, hy + 3.0 + j * 1.4, 0.35, 0.35).draw(draw, fill=(60, 58, 74))
    rig_cape(r, draw, CRU_RED, layer="over")
    h = hand_at(r, hs)
    ang = 90 + hs * 28 if not d else 90 - d * 40
    blade(draw, h[0], h[1], ang, length=13.0, width=1.8, flip=hs if not d else -d)
    for side in _sides(r):
        rig_hand(r, draw, side, CRU_STEEL)
    if not d:
        h2 = hand_at(r, os_)
        kite_shield(draw, h2[0] + os_ * 1.6, h2[1] - 3.4, 5.6, 12.6, CRU_WHITE if not r.back else shade(CRU_WHITE, 0.6), rim=CRU_STEEL,
                    emblem=(lambda dr, x, y: cross(dr, x, y, 2.8, CRU_RED)) if not r.back else None)


# ===================================================================
# DRUID (46) -- antlers from a leaf circlet, moss cloak, living staff
# ===================================================================

DRU_CLOAK = (92, 138, 72)
DRU_ROBE = (140, 100, 66)
DRU_HAIR = (122, 76, 50)
DRU_ANTLER = (226, 204, 164)
DRU_GEM = (130, 255, 150)


def draw_druid(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, head=0.95)
    d = r.d
    hs = _hold_side(r)
    out = 1.6 if not d else 0.4
    rig_hair(r, draw, DRU_HAIR, "long", layer="back")
    rig_cape(r, draw, DRU_CLOAK, layer="under")
    if d:
        rig_arms(r, draw, DRU_ROBE, SKIN, layer="far")
    rig_legs(r, draw, shade(DRU_ROBE, 1.0), (96, 70, 50))
    rig_robe(r, draw, DRU_ROBE, trim=DRU_CLOAK, flare=3.0)
    rig_belt(r, draw, DRU_CLOAK, buckle=DRU_GEM)
    # leaf mantle over the shoulders
    cx = r.cx
    for i, u in enumerate((-6.0, -3.0, 0.0, 3.0, 6.0) if not d else (-3.0, 0.0, 3.0)):
        leaf(draw, cx + u, r.sh_y - 2.4, 90 + u * 6, 5.0, 3.2, DRU_CLOAK if i % 2 else lit(DRU_CLOAK, 0.4))
    rig_arms(r, draw, DRU_ROBE, SKIN, layer="near", hands=False)
    rig_head(r, draw, SKIN, hair=DRU_HAIR, style="long", eye_color=(80, 150, 80), expression="smile", mood="calm")
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    # antlers
    for s in ((-1, 1) if not d else (-d, d)):
        near = not d or s == -d
        col = DRU_ANTLER if near else shade(DRU_ANTLER, 0.6)
        bx = hx + s * 5.0 if not d else hx + s * 2.6
        by = hy - ry + 2.0
        main = [(bx, by), (bx + s * 3.0, by - 4.4), (bx + s * 4.0, by - 9.4)]
        cel(draw, Limb(main, [1.4, 1.1, 0.5]), col, sh=None, lw=0.8)
        for (t, ln) in ((0.45, 3.6), (0.8, 3.0)):
            p = (bx + s * 3.0 * t * 1.3, by - 9.4 * t)
            cel(draw, Limb([p, (p[0] + s * ln, p[1] - ln * 0.4)], [0.8, 0.4]), col, sh=None, lw=0.7)
            cel(draw, Limb([p, (p[0] - s * ln * 0.4, p[1] - ln)], [0.8, 0.4]), col, sh=None, lw=0.7)
    # leaf circlet
    for i, u in enumerate((-7.0, -3.4, 0.0, 3.4, 7.0) if not d else (-d * 3.0, d * 0.6, d * 4.2)):
        leaf(draw, hx + u, hy - ry + 3.0 - (abs(u) < 1) * 0.6, -90 + u * 8, 3.4, 2.4, DRU_CLOAK, vein=False)
    rig_cape(r, draw, DRU_CLOAK, layer="over")
    if not r.back:
        h = hand_at(r, hs, 0.0, 0.0, out)
        x = h[0] + 0.2
        top = r.head_cy - 2.0
        cel(draw, Limb([(x, r.base_y - 1.2), (x - 0.6, r.sh_y), (x + 0.4, top + 3.0)], [1.2, 1.0, 1.0]), WOOD, sh=(0.5, 0.0))
        cel(draw, Limb(arc_pts(x, top, 3.2, 3.2, 120, 420, 10), [0.8] * 11, cap=False), WOOD, sh=None)
        cel(draw, Ell(x, top, 1.8, 1.8), DRU_GEM, sh=None, lw=0.6)
        Ell(x - 0.5, top - 0.5, 0.6, 0.6).draw(draw, fill=(255, 255, 255))
        for a in (30, 150):
            leaf(draw, x + math.cos(math.radians(a)) * 3.0, top + math.sin(math.radians(a)) * 3.0 + 1.0, a + 40, 3.6, 2.2, DRU_CLOAK)
    for side in _sides(r):
        rig_hand(r, draw, side, SKIN, out=out if side == hs else 0.0)


# ===================================================================
# BARD (47) -- plumed beret, gold-trimmed doublet, an oversized lute
# ===================================================================

BARD_PURPLE = (150, 70, 180)
BARD_RED = (220, 60, 70)
BARD_HAIR = (230, 170, 80)
BARD_LUTE = (196, 130, 70)


def draw_bard(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame)
    d = r.d
    rig_cape(r, draw, BARD_RED, layer="under", length=r.hip_y + 2.0)
    if d:
        rig_arms(r, draw, BARD_PURPLE, SKIN, layer="far", cuff=GOLD)
    rig_legs(r, draw, (90, 70, 110), (110, 70, 50))
    rig_torso(r, draw, BARD_PURPLE)
    cx = r.cx
    if not r.back:
        for k in range(3):
            y = r.sh_y + 0.6 + k * 2.4
            Ell(cx + d * 2.0, y, 0.6, 0.6).draw(draw, fill=GOLD)
        stroke(draw, [(cx - 5.0 + d, r.sh_y - 1.4), (cx + d * 2.0, r.waist_y - 1.0), (cx + 5.0 + d, r.sh_y - 1.4)], 0.8, GOLD)
    rig_belt(r, draw, (90, 60, 60), buckle=GOLD)
    # the lute, slung across the front
    if not r.back:
        lx, ly = (cx + d * 3.0, r.waist_y + 1.0) if d else (cx + 1.0, r.waist_y + 1.0)
        ang = -35 if not d else (-30 if d > 0 else -150)
        body = Ell(lx, ly + 1.0, 4.6, 5.4)
        neck_end = xform([(13.0, 0.0)], lx, ly, ang)[0]
        cel(draw, Limb([(lx, ly), neck_end], [1.1, 0.9]), shade(BARD_LUTE, 1.2), sh=None)
        cel(draw, Poly(xform([(12.4, -1.4), (15.6, -1.8), (15.8, 1.4), (12.4, 1.0)], lx, ly, ang)), shade(BARD_LUTE, 1.4), sh=None)
        cel(draw, body, BARD_LUTE, sh=(1.0, 1.0), hi=(0.6, 0.6))
        cel(draw, Ell(lx, ly + 0.4, 1.5, 1.5), (60, 40, 30), sh=None, line=False)
        for k in (-0.6, 0.6):
            stroke(draw, [(lx + k, ly + 4.0), neck_end], 0.3, (246, 240, 220))
    rig_arms(r, draw, BARD_PURPLE, SKIN, layer="near", cuff=GOLD, reach=0.5)
    rig_head(r, draw, SKIN, hair=BARD_HAIR, style="swept", eye_color=(110, 80, 160), expression="grin" if frame % 2 else "smile",
             hat=True)
    # beret with a long plume
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    beret = Ell(hx - d * 1.4 + (1.6 if not d else 0), hy - ry + 1.6, rx + 1.8, 5.0)
    cel(draw, beret, BARD_RED, sh=(1.2, 1.0))
    cel(draw, RRect(hx - rx + 0.4, hy - ry + 3.6, hx + rx - 0.4, hy - ry + 5.6, 1.0) if not d else
        RRect(hx - rx * 0.8, hy - ry + 3.6, hx + rx * 0.8, hy - ry + 5.6, 1.0), shade(BARD_RED, 1.2), sh=None)
    px = hx + (rx - 2.0 if not d else -d * (rx - 3.0))
    cel(draw, Limb([(px, hy - ry + 1.0), (px + (3.0 if not d else -d * 3.0), hy - ry - 5.0), (px + (8.4 if not d else -d * 8.0), hy - ry - 8.0)],
                   [0.9, 2.0, 0.3]), (255, 236, 120), sh=None)
    rig_cape(r, draw, BARD_RED, layer="over", length=r.hip_y + 2.0)
    # notes floating up
    for i in range(2):
        k = (frame + i * 2) % 4
        nx = cx + (-12.0 if i else 12.0) + (0 if not d else d * 4)
        ny = r.head_cy - 2.0 - k * 1.6
        cel(draw, Ell(nx, ny, 1.3, 1.0), BARD_PURPLE if i else BARD_RED, sh=None, lw=0.5)
        stroke(draw, [(nx + 1.1, ny), (nx + 1.1, ny - 4.0), (nx + 2.6, ny - 3.0)], 0.5, BARD_PURPLE if i else BARD_RED)


# ===================================================================
# MONK (48) -- shaven head, saffron robes, prayer beads, wrapped fists
# ===================================================================

MONK_ROBE = (244, 160, 56)
MONK_RED = (190, 50, 44)
MONK_BEAD = (134, 84, 50)
MONK_WRAP = (240, 232, 214)


def draw_monk(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=1.06)
    d = r.d
    if d:
        rig_arms(r, draw, SKIN_TAN, MONK_WRAP, layer="far", reach=0.3)
    rig_legs(r, draw, MONK_ROBE, (130, 90, 60))
    rig_torso(r, draw, MONK_ROBE)
    cx = r.cx
    # the robe over one shoulder, a red under-layer on the other
    if not r.back and not d:
        cel(draw, Poly([(cx - r.sh_w + 1.6, r.sh_y - 2.2), (cx - 0.6, r.sh_y - 2.2), (cx + r.hip_w - 0.6, r.waist_y + 1.0),
                        (cx + r.hip_w, r.waist_y - 1.6), (cx + r.sh_w, r.sh_y + 0.4), (cx + r.sh_w - 1.8, r.sh_y - 2.2)]),
            MONK_RED, sh=(0.8, 0.4))
    rig_robe(r, draw, MONK_ROBE, top=r.waist_y + 0.6, hem=r.hip_y + 4.4, flare=2.0, split=True)
    rig_belt(r, draw, MONK_RED, buckle=MONK_ROBE)
    rig_arms(r, draw, SKIN_TAN, MONK_WRAP, layer="near", reach=0.3, hand_r=2.5)
    head_skull(r, draw, SKIN_TAN)
    hx, hy = r.hx, r.head_cy
    if not r.back:
        head_face(r, draw, SKIN_TAN, iris=(90, 70, 50), mood="closed" if frame in (0, 2) else "calm", mouth="smile", brows=True,
                  brow_color=(80, 60, 50))
        for i, u in enumerate((-1.4, 0.0, 1.4) if not d else (d * 1.4,)):
            Ell(hx + u + d * 2.0, hy - 6.4 + abs(u) * 0.2, 0.5, 0.5).draw(draw, fill=shade(SKIN_TAN, 1.3))
    # prayer beads, big ones
    n = 9 if not d else 5
    for i in range(n):
        t = i / float(n - 1)
        a = math.radians(200 - t * 220) if not d else math.radians(160 - t * 140)
        bx = cx + (math.cos(a) * 6.4 if not d else d * 1.0 + math.cos(a) * 4.0)
        by = r.sh_y - 1.6 + (math.sin(a) + 0.4) * 3.8
        if not r.back or by < r.sh_y - 1.0:
            cel(draw, Ell(bx, by, 1.2, 1.2), MONK_BEAD, sh=None, lw=0.6)
    if not r.back and not d:
        cel(draw, Ell(cx, r.sh_y + 3.0, 1.6, 1.6), MONK_RED, sh=None, lw=0.6)


# ===================================================================
# CLERIC (49) -- a halo, white and gold robes, a starred staff
# ===================================================================

CLR_ROBE = (246, 246, 252)
CLR_BLUE = (110, 170, 240)
CLR_HAIR = (250, 222, 140)
CLR_HALO = (255, 230, 120)


def draw_cleric(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=0.96)
    d = r.d
    hs = _hold_side(r)
    out = 1.6 if not d else 0.4
    rig_hair(r, draw, CLR_HAIR, "long", layer="back")

    def staff():
        h = hand_at(r, hs, 0.0, 0.0, out)
        x = h[0] + 0.2
        top = r.head_cy - 3.0
        cel(draw, Limb([(x, r.base_y - 1.2), (x, top + 2.4)], [1.0, 0.9]), GOLD, sh=(0.5, 0.0))
        cel(draw, Limb(arc_pts(x, top - 1.0, 3.4, 3.4, 0, 360, 16), [0.8] * 17, cap=False), GOLD, sh=None)
        star(draw, x, top - 1.0, 2.6, (255, 250, 200), line=ink(GOLD))

    if r.back:
        staff()
    if d:
        rig_arms(r, draw, CLR_ROBE, SKIN, layer="far", cuff=GOLD)
    rig_legs(r, draw, shade(CLR_ROBE, 1.0), (200, 170, 110))
    rig_robe(r, draw, CLR_ROBE, trim=GOLD, flare=3.2)
    cx = r.cx
    if not r.back:
        cel(draw, Poly([(cx - 1.8 + d * 1.8, r.sh_y - 1.6), (cx + 1.8 + d * 1.8, r.sh_y - 1.6), (cx + 2.6 + d * 1.8, r.base_y - 3.0),
                        (cx - 2.6 + d * 1.8, r.base_y - 3.0)]), CLR_BLUE, sh=None)
        star(draw, cx + d * 1.8, r.sh_y + 2.0, 1.8, GOLD)
    rig_belt(r, draw, GOLD, buckle=CLR_BLUE)
    rig_arms(r, draw, CLR_ROBE, SKIN, layer="near", cuff=GOLD, hands=False)
    rig_head(r, draw, SKIN, hair=CLR_HAIR, style="long", eye_color=(90, 150, 220), expression="smile")
    # halo
    hx, hy, ry = r.hx, r.head_cy, r.head_ry
    halo_y = hy - ry - 3.6 + [0, -0.6, 0, -0.6][frame]
    outer = Ell(hx - d * 0.6, halo_y, 8.0 if not d else 5.6, 2.4)
    inner = Ell(hx - d * 0.6, halo_y, 5.8 if not d else 3.8, 1.2)
    layer = draw.sub()
    cel(layer, outer, CLR_HALO, sh=None, line_color=shade(CLR_HALO, 1.8))
    inner.draw(layer, fill=(0, 0, 0, 0))
    inner.draw(layer, fill=None, outline=shade(CLR_HALO, 1.8), width=0.6)
    draw.merge(layer)
    if not r.back:
        staff()
    for side in _sides(r):
        rig_hand(r, draw, side, SKIN, out=out if side == hs else 0.0)


# ===================================================================
# ROGUE (50) -- a domino mask, a hood thrown back, twin daggers
# ===================================================================

ROG_CLOAK = (54, 92, 98)
ROG_VEST = (70, 64, 76)
ROG_HAIR = (56, 50, 64)
ROG_MASK = (32, 30, 40)


def draw_rogue(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=0.96)
    d = r.d
    rig_cape(r, draw, ROG_CLOAK, layer="under", length=r.hip_y + 4.0)
    if d:
        rig_arms(r, draw, ROG_VEST, SKIN, layer="far")
    rig_legs(r, draw, (60, 56, 70), (80, 60, 48))
    rig_torso(r, draw, ROG_VEST)
    cx = r.cx
    if not r.back:
        for k in (-1, 1):
            stroke(draw, [(cx + k * 5.2 + d, r.sh_y - 1.4), (cx - k * 4.8 + d, r.waist_y + 0.6)], 0.9, LEATHER)
    rig_belt(r, draw, LEATHER, buckle=(200, 200, 210))
    if not r.back:
        cel(draw, RRect(cx + (3.4 if not d else d * 2.0) - 1.6, r.waist_y + 2.4, cx + (3.4 if not d else d * 2.0) + 1.6, r.waist_y + 5.0, 0.6),
            LEATHER, sh=None)
    rig_arms(r, draw, ROG_VEST, SKIN, layer="near", hands=False)
    head_skull(r, draw, SKIN)
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    if not r.back:
        # the mask band across the eyes, tails fluttering behind
        e1, e2, ey, _, _ = face_anchor(r)
        if d:
            band = Poly([(hx - d * 3.4, ey - 3.6), (hx + d * (rx + 0.2), ey - 3.2), (hx + d * (rx + 0.2), ey + 2.6), (hx - d * 3.4, ey + 2.8)])
        else:
            band = Poly([(hx - rx - 0.2, ey - 3.4), (hx + rx + 0.2, ey - 3.4), (hx + rx + 0.2, ey + 2.4), (hx + 1.6, ey + 3.0),
                         (hx, ey + 1.6), (hx - 1.6, ey + 3.0), (hx - rx - 0.2, ey + 2.4)])
        cel(draw, band, ROG_MASK, sh=None)
        head_face(r, draw, SKIN, iris=(90, 200, 190), mood="sharp", mouth="smirk", blush=False, brows=False)
    wave = [0, 1, 0, -1][frame]
    tx = hx + (rx if not d else -d * rx)
    cel(draw, Limb([(tx, hy + 0.4), (tx + (3.0 if not d else -d * 3.4), hy + 2.0 + wave), (tx + (5.0 if not d else -d * 6.0), hy + 0.6 - wave)],
                   [1.0, 0.9, 0.3]), ROG_MASK, sh=None)
    rig_hair(r, draw, ROG_HAIR, "wild", skin=SKIN)
    # the hood, thrown back onto the shoulders
    if r.back:
        cel(draw, Poly([(hx - 7.6, r.sh_y - 3.0), (hx + 7.6, r.sh_y - 3.0), (hx + 5.0, r.sh_y + 3.4), (hx, r.sh_y + 5.0), (hx - 5.0, r.sh_y + 3.4)]),
            ROG_CLOAK, sh=(0.8, 0.8))
    else:
        cel(draw, RRect(r.cx - 7.6, r.sh_y - 3.6, r.cx + 7.6, r.sh_y - 0.6, 1.4) if not d else
            RRect(r.cx - 5.6, r.sh_y - 3.6, r.cx + 5.6, r.sh_y - 0.6, 1.4), ROG_CLOAK, sh=(0.0, 0.7))
    rig_cape(r, draw, ROG_CLOAK, layer="over", length=r.hip_y + 4.0)
    for side in _sides(r):
        h = hand_at(r, side)
        ang = 90 + side * 150 if not d else (90 + d * 140)
        blade(draw, h[0], h[1], -90 + (side * 30 if not d else d * 36), length=7.4, width=1.3, guard=1.8,
              flip=-side if not d else -d, hilt=(200, 200, 210))
        rig_hand(r, draw, side, SKIN)


# ===================================================================
# BARBARIAN (51) -- horned helmet, braided blond beard, a great axe
# ===================================================================

BAR_IRON = (152, 158, 174)
BAR_HAIR = (242, 202, 110)
BAR_FUR = (150, 108, 70)
BAR_HORN = (240, 230, 206)


def draw_barbarian(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=1.32, head=0.96)
    d = r.d
    hs = _hold_side(r)
    if d:
        rig_arms(r, draw, SKIN_TAN, SKIN_TAN, layer="far")
    rig_legs(r, draw, (120, 90, 70), (100, 72, 52), width=1.1)
    rig_torso(r, draw, (170, 140, 104))
    cx = r.cx
    rig_belt(r, draw, (90, 64, 46), buckle=BAR_IRON)
    rig_arms(r, draw, SKIN_TAN, SKIN_TAN, layer="near", hands=False)
    # fur mantle
    blob(draw, [Ell(cx - 6.4, r.sh_y - 1.4, 5.0, 3.2), Ell(cx, r.sh_y - 0.8, 5.4, 3.0), Ell(cx + 6.4, r.sh_y - 1.4, 5.0, 3.2)]
         if not d else [Ell(cx, r.sh_y - 1.2, 7.0, 3.2)], BAR_FUR, sh=(0.8, 1.0))
    head_skull(r, draw, SKIN_TAN, ears=False)
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    if not r.back:
        head_face(r, draw, SKIN_TAN, iris=(70, 110, 190), mood="sharp", mouth=None, brows=True, brow_color=shade(BAR_HAIR, 1.4))
        if d:
            parts = [Ell(hx + d * 4.4, hy + 9.0, 5.4, 5.4), Ell(hx + d * 1.4, hy + 6.8, 3.8, 3.8)]
            braids = [(hx + d * 4.0, hy + 13.0)]
        else:
            parts = [Ell(hx, hy + 9.0, 7.6, 6.0), Ell(hx - 7.0, hy + 6.4, 3.6, 3.6), Ell(hx + 7.0, hy + 6.4, 3.6, 3.6)]
            braids = [(hx - 2.8, hy + 13.4), (hx + 2.8, hy + 13.4)]
        blob(draw, parts, BAR_HAIR, sh=(1.0, 1.2))
        for (bx, by) in braids:
            cel(draw, Limb([(bx, by - 1.0), (bx, by + 4.0)], [1.5, 1.0]), BAR_HAIR, sh=(0.4, 0.0))
            cel(draw, RRect(bx - 1.4, by + 1.6, bx + 1.4, by + 2.8, 0.4), BAR_IRON, sh=None)
        mcx = hx + d * 5.6
        stroke(draw, [(mcx - 3.6, hy + 6.6), (mcx, hy + 5.8), (mcx + 3.6, hy + 6.6)], 1.3, shade(BAR_HAIR, 0.6))
    rig_hair(r, draw, BAR_HAIR, dict(HAIR_STYLES["long"], side=8.0, back=12.0), hat=True, skin=SKIN_TAN)
    # horned helmet
    for s in ((-1, 1) if not d else (-d, d)):
        near = not d or s == -d
        col = BAR_HORN if near else shade(BAR_HORN, 0.6)
        hb = (hx + s * (rx - 0.6), hy - 4.4) if not d else (hx + s * 2.6, hy - ry + 1.2)
        pts = [hb, (hb[0] + s * 4.4, hb[1] - 2.0), (hb[0] + s * 6.8, hb[1] - 6.4), (hb[0] + s * 6.0, hb[1] - 10.0)]
        cel(draw, Limb(pts, [2.4, 2.0, 1.4, 0.3]), col, sh=(0.5, 0.6))
        for t in (1, 2):
            p = pts[t]
            stroke(draw, [(p[0] - 1.4, p[1] + 0.6 * s), (p[0] + 1.4, p[1] - 0.6 * s)], 0.45, shade(col, 1.4))
    dome = arc_pts(hx - d * 0.4, hy - 1.0, rx + 1.4, ry + 0.6, 182, 358, 26)
    cel(draw, Poly(dome + [(hx + rx + 1.6, hy - 2.0), (hx - rx - 1.6, hy - 2.0)]), BAR_IRON, sh=(1.4, 1.0), hi=(0.8, 0.8))
    stroke(draw, [(hx - rx - 1.2, hy - 2.6), (hx + rx + 1.2, hy - 2.6)], 1.1, shade(BAR_IRON, 1.2))
    if not r.back:
        stroke(draw, [(hx + d * 3.0, hy - 2.4), (hx + d * 3.0, hy + 2.6)], 1.4, BAR_IRON)
    # a great two-handed axe
    h = hand_at(r, hs, 0.3)
    ang = -90 + hs * 18 if not d else -90 + d * 26
    axe(draw, h[0], h[1] + 4.0, ang, 18.0, big=1.3, double=True, flip=1.0)
    for side in _sides(r):
        rig_hand(r, draw, side, SKIN_TAN, reach=0.3 if side == hs else 0.0)


# ===================================================================
# ENCHANTRESS (52) -- violet hair, a jewelled tiara, a heart wand
# ===================================================================

ENC_GOWN = (156, 90, 206)
ENC_PINK = (255, 132, 194)
ENC_HAIR = (120, 70, 170)


def draw_enchantress(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=0.92)
    d = r.d
    hs = _hold_side(r)
    style = dict(HAIR_STYLES["long"], extra="twin")
    rig_hair(r, draw, ENC_HAIR, style, layer="back")
    if d:
        rig_arms(r, draw, ENC_GOWN, SKIN, layer="far", cuff=ENC_PINK)
    rig_legs(r, draw, shade(ENC_GOWN, 1.0), ENC_PINK)
    rig_robe(r, draw, ENC_GOWN, trim=ENC_PINK, flare=4.0)
    cx = r.cx
    # a pink bow at the waist
    if not r.back:
        bx = cx + d * 1.8
        for s in (-1, 1):
            cel(draw, Poly([(bx, r.waist_y + 1.6), (bx + s * 3.4, r.waist_y - 0.6), (bx + s * 3.6, r.waist_y + 3.8)]), ENC_PINK, sh=None, lw=0.7)
        cel(draw, Ell(bx, r.waist_y + 1.6, 1.1, 1.1), lit(ENC_PINK, 0.8), sh=None, lw=0.6)
    rig_arms(r, draw, ENC_GOWN, SKIN, layer="near", cuff=ENC_PINK, hands=False, reach=0.3)
    rig_head(r, draw, SKIN, hair=ENC_HAIR, style=style, eye_color=(200, 80, 170), expression="smile")
    # tiara
    hx, hy, ry = r.hx, r.head_cy, r.head_ry
    if not r.back:
        tx = hx + d * 3.0
        cel(draw, Poly([(tx - 5.6, hy - ry + 2.6), (tx - 3.0, hy - ry + 0.6), (tx, hy - ry - 2.8), (tx + 3.0, hy - ry + 0.6),
                        (tx + 5.6, hy - ry + 2.6), (tx, hy - ry + 1.6)]), GOLD, sh=(0.4, 0.4))
        gem(draw, tx, hy - ry + 0.2, 1.4, ENC_PINK)
    if r.back:
        rig_hair(r, draw, ENC_HAIR, style, layer="back")
    for side in _sides(r):
        h = rig_hand(r, draw, side, SKIN, reach=0.3)
        if side == hs and not r.back:
            wx, wy = h[0] + (0.6 if not d else d * 1.0), h[1] - 1.0
            tip = (wx + (4.0 if not d else d * 4.4), wy - 10.4)
            cel(draw, Limb([(wx, wy + 1.0), tip], [0.8, 0.7]), (250, 250, 250), sh=None, lw=0.6)
            hxh, hyh = tip[0], tip[1] - 1.0
            k = 1.6
            cel(draw, Poly([(hxh, hyh + 2.4 * k), (hxh - 2.6 * k, hyh - 0.2 * k), (hxh - 2.2 * k, hyh - 1.8 * k),
                            (hxh - 1.0 * k, hyh - 2.2 * k), (hxh, hyh - 1.0 * k), (hxh + 1.0 * k, hyh - 2.2 * k),
                            (hxh + 2.2 * k, hyh - 1.8 * k), (hxh + 2.6 * k, hyh - 0.2 * k)]), ENC_PINK, sh=(0.7, 0.7), hi=(0.4, 0.4))
            Ell(hxh - 1.6, hyh - 1.6, 0.7, 0.6).draw(draw, fill=(255, 255, 255))
            if frame % 2 == 0:
                sparkle(draw, hxh + 5.0, hyh - 3.4, 2.0, (255, 220, 240))
    # hearts floating up
    for i in range(2):
        k = (frame + i * 2) % 4
        x = cx + (-13.0 if i else 13.0)
        y = r.sh_y - 2.0 - k * 1.8
        cel(draw, Poly([(x, y + 1.6), (x - 1.8, y - 0.2), (x - 1.4, y - 1.4), (x - 0.6, y - 1.6), (x, y - 0.8), (x + 0.6, y - 1.6),
                        (x + 1.4, y - 1.4), (x + 1.8, y - 0.2)]), ENC_PINK, sh=None, lw=0.5)


# ===================================================================
# JESTER (53) -- a two-pointed belled hat, motley, a fan of cards
# ===================================================================

JES_RED = (224, 54, 64)
JES_GREEN = (70, 170, 90)
JES_GOLD = (252, 212, 70)


def draw_jester(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=0.94)
    d = r.d
    hs = _hold_side(r)
    if d:
        rig_arms(r, draw, JES_GREEN, (250, 250, 250), layer="far")
    for side in ((-1, 1) if not d else (-d, d)):
        rig_legs(r, draw, JES_RED if side < 0 else JES_GREEN, JES_GOLD, sides=(side,))
    rig_torso(r, draw, JES_RED)
    cx = r.cx
    # diamond motley
    if not r.back:
        for (u, v, col) in ((-2.6, 1.6, JES_GREEN), (2.6, 1.6, JES_GOLD), (0.0, 5.0, JES_GREEN), (-2.6, 8.4, JES_GOLD), (2.6, 8.4, JES_GREEN)):
            x = cx + u + d * 1.6
            y = r.sh_y + v
            cel(draw, Poly([(x, y - 1.8), (x + 1.6, y), (x, y + 1.8), (x - 1.6, y)]), col, sh=None, lw=0.5)
    rig_belt(r, draw, JES_GOLD, buckle=JES_RED)
    rig_arms(r, draw, JES_GREEN, (250, 250, 250), layer="near", hands=False)
    # a ruff collar
    n = 7 if not d else 5
    for i in range(n):
        t = i / float(n - 1) - 0.5
        cel(draw, Ell(cx + t * (13.0 if not d else 9.0), r.sh_y - 2.2 + abs(t) * 0.8, 2.0, 1.6), (250, 250, 252), sh=None, lw=0.6)
    head_skull(r, draw, SKIN)
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    if not r.back:
        head_face(r, draw, SKIN, iris=(160, 60, 60), mood="bright", mouth="grin", brows=True)
        e1, e2, ey, _, _ = face_anchor(r)
        cel(draw, Poly([(e2, ey + 3.0), (e2 + 0.8, ey + 4.4), (e2, ey + 5.8), (e2 - 0.8, ey + 4.4)]), (60, 60, 150), sh=None, line=False)
    rig_hair(r, draw, (120, 70, 50), "short", hat=True, skin=SKIN)
    # the hat: two floppy horns with bells
    wob = [0.0, 1.0, 0.0, -1.0][frame]
    cap = arc_pts(hx - d * 0.4, hy - 1.2, rx + 1.4, ry + 0.4, 180, 360, 24)
    for s, col in ((-1, JES_RED), (1, JES_GREEN)):
        if d and s == d:
            continue
        base = (hx + s * 5.0, hy - ry + 1.0) if not d else (hx - d * 2.0, hy - ry)
        k = s if not d else -d
        pts = [base, (base[0] + k * 6.0, base[1] - 5.0 + wob * k), (base[0] + k * 12.0, base[1] - 2.0 + wob * k), (base[0] + k * 13.4, base[1] + 3.4)]
        cel(draw, Limb(pts, [4.4, 3.4, 2.0, 0.6]), col, sh=(0.8, 0.8))
        bx, by = pts[-1]
        cel(draw, Ell(bx, by + 1.2, 1.7, 1.7), JES_GOLD, sh=(0.4, 0.4))
    cel(draw, Poly(cap + [(hx + rx + 1.4, hy - 2.0), (hx - rx - 1.4, hy - 2.0)]), JES_RED if d <= 0 else JES_GREEN, sh=(1.2, 0.8),
        regions=[(Poly([(hx, hy - 20), (hx + 20, hy - 20), (hx + 20, hy), (hx, hy)]), JES_GREEN)] if not d else ())
    stroke(draw, [(hx - rx - 1.0, hy - 2.4), (hx + rx + 1.0, hy - 2.4)], 1.4, JES_GOLD)
    for side in _sides(r):
        h = rig_hand(r, draw, side, (250, 250, 250))
        if side == hs and not r.back:
            for j, a in enumerate((-40, -15, 10)):
                ang = -90 + a + (0 if not d else d * 20)
                c = xform([(4.4, 0.0)], h[0], h[1] - 1.0, ang)[0]
                card = Poly(xform([(-2.2, -1.6), (2.2, -1.6), (2.2, 1.6), (-2.2, 1.6)], c[0], c[1], ang + 90))
                cel(draw, card, (252, 252, 252), sh=None, lw=0.6)
                Ell(c[0], c[1], 0.7, 0.7).draw(draw, fill=JES_RED if j % 2 == 0 else (40, 40, 50))


# ===================================================================
# VALKYRIE (54) -- winged helm, blond braid, feather wings, a spear
# ===================================================================

VAL_STEEL = (210, 218, 232)
VAL_BLUE = (72, 122, 212)
VAL_HAIR = (250, 214, 120)


def draw_valkyrie(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=1.0, head=0.96)
    d = r.d
    hs = _hold_side(r)
    flap = [0.0, 1.0, 0.0, 1.0][frame]
    style = dict(HAIR_STYLES["long"], extra="pony", back=14.0, side=10.0)
    rig_hair(r, draw, VAL_HAIR, style, layer="back")
    # feather wings on her back, spread past her hair
    for s in ((-1, 1) if not d else (-d,)):
        feather_wing(draw, r.cx + s * 3.0 if not d else r.cx - d * 3.0, r.sh_y - 1.0, s, 14.4, flap=flap)
    if d:
        rig_arms(r, draw, VAL_STEEL, VAL_STEEL, layer="far")
    rig_legs(r, draw, VAL_BLUE, VAL_STEEL)
    rig_torso(r, draw, VAL_STEEL)
    cx = r.cx
    rig_robe(r, draw, VAL_BLUE, top=r.waist_y + 0.6, hem=r.hip_y + 4.4, flare=2.4, split=True, trim=VAL_STEEL)
    rig_belt(r, draw, GOLD, buckle=VAL_BLUE)
    rig_arms(r, draw, VAL_STEEL, VAL_STEEL, layer="near", hands=False)
    rig_head(r, draw, SKIN, hair=VAL_HAIR, style=style, eye_color=(70, 130, 210), expression="set", mood="sharp", hat=True)
    # winged helm
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    for s in ((-1, 1) if not d else (-d,)):
        wx = hx + s * (rx - 0.2) if not d else hx - d * 3.4
        feather_wing(draw, wx, hy - 4.4, s if not d else -d, 7.4)
    dome = arc_pts(hx - d * 0.4, hy - 0.8, rx + 1.1, ry + 0.9, 182, 358, 26)
    cel(draw, Poly(dome + [(hx + rx + 1.2, hy - 2.4), (hx - rx - 1.2, hy - 2.4)]), VAL_STEEL, sh=(1.4, 1.0), hi=(0.8, 0.8))
    if not r.back:
        stroke(draw, [(hx + d * 3.0, hy - ry + 0.8), (hx + d * 3.0, hy - 2.6)], 1.2, GOLD)
    if r.back:
        rig_hair(r, draw, VAL_HAIR, style, layer="back")
    h = hand_at(r, hs, 0.0, 0.0, 1.4 if not d else 0.4)
    x = h[0] + 0.2
    spear(draw, x, r.base_y - 1.0, x, r.head_cy - 6.0, head_len=6.4, head_w=2.4)
    for side in _sides(r):
        rig_hand(r, draw, side, VAL_STEEL, out=(1.4 if not d else 0.4) if side == hs else 0.0)


# ===================================================================
# WARLOCK (55) -- pact horns, a high-collared coat, a floating grimoire
# ===================================================================

WLK_COAT = (66, 44, 88)
WLK_TRIM = (232, 188, 84)
WLK_FIRE = ((140, 60, 220), (190, 110, 255), (240, 214, 255))
WLK_HORN = (44, 36, 52)


def draw_warlock(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame)
    d = r.d
    hs = _hold_side(r)
    bob2 = [0.0, -0.6, -1.0, -0.6][frame]
    # the grimoire floating at his off side
    bxs = r.cx + ((-13.0 if hs > 0 else 13.0) if not d else -d * 11.0)
    by = r.sh_y - 2.0 + bob2

    def book():
        cel(draw, Poly([(bxs - 4.6, by - 3.0), (bxs, by - 2.2), (bxs + 4.6, by - 3.0), (bxs + 4.6, by + 2.6), (bxs, by + 3.4),
                        (bxs - 4.6, by + 2.6)]), (120, 50, 60), sh=(0.5, 0.5))
        cel(draw, Poly([(bxs - 3.8, by - 2.2), (bxs, by - 1.4), (bxs + 3.8, by - 2.2), (bxs + 3.8, by + 1.8), (bxs, by + 2.6),
                        (bxs - 3.8, by + 1.8)]), (246, 236, 210), sh=None, lw=0.5)
        stroke(draw, [(bxs, by - 1.4), (bxs, by + 2.6)], 0.4, (180, 160, 130))
        star(draw, bxs + 1.9, by + 0.4, 1.2, WLK_FIRE[1], points=5)

    if d:
        book()
    # high collar behind the head
    hx0, hy0 = r.hx, r.head_cy
    if d:
        cel(draw, Poly([(hx0 - d * 2.0, r.sh_y - 1.0), (hx0 - d * 9.4, hy0 + 1.0), (hx0 - d * 10.6, hy0 - 2.4), (hx0 - d * 10.4, hy0 + 6.0),
                        (hx0 - d * 5.0, r.sh_y)]), shade(WLK_COAT, 0.6), sh=None)
    elif not r.back:
        for s in (-1, 1):
            cel(draw, Poly([(hx0 + s * 4.4, r.sh_y - 0.4), (hx0 + s * 11.6, hy0 + 0.6), (hx0 + s * 12.8, hy0 - 2.6), (hx0 + s * 13.2, hy0 + 4.4),
                            (hx0 + s * 9.4, r.sh_y + 0.6)]), shade(WLK_COAT, 0.6), sh=None)
    if d:
        rig_arms(r, draw, WLK_COAT, (210, 196, 214), layer="far", cuff=WLK_TRIM)
    rig_legs(r, draw, shade(WLK_COAT, 1.0), (40, 34, 50))
    rig_robe(r, draw, WLK_COAT, trim=WLK_TRIM, flare=2.8, split=True)
    rig_belt(r, draw, shade(WLK_COAT, 1.6), buckle=WLK_TRIM)
    rig_arms(r, draw, WLK_COAT, (210, 196, 214), layer="near", cuff=WLK_TRIM, hands=False, reach=0.3)
    rig_head(r, draw, (226, 214, 228), hair=(40, 34, 54), style="swept", eye_color=(190, 90, 255), expression="smirk", mood="sharp",
             blush=False)
    # curling pact horns
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    for s in ((-1, 1) if not d else (-d, d)):
        near = not d or s == -d
        hb = (hx + s * (rx - 3.4), hy - ry + 2.4)
        pts = [hb, (hb[0] + s * 2.6, hb[1] - 4.6), (hb[0] + s * 1.0, hb[1] - 8.2), (hb[0] - s * 1.4, hb[1] - 8.6)]
        cel(draw, Limb(pts, [2.0, 1.6, 1.0, 0.3]), WLK_HORN if near else shade(WLK_HORN, 0.4), sh=(0.3, 0.4))
    if r.back:
        for s in (-1, 1):
            cel(draw, Poly([(hx0 + s * 4.4, r.sh_y - 0.4), (hx0 + s * 11.6, hy0 + 0.6), (hx0 + s * 12.8, hy0 - 2.6), (hx0 + s * 13.2, hy0 + 4.4),
                            (hx0 + s * 9.4, r.sh_y + 0.6)]), WLK_COAT, sh=None)
    if not d:
        book()
    for side in _sides(r):
        h = rig_hand(r, draw, side, (210, 196, 214), reach=0.3)
        if side == hs and not r.back:
            flame(draw, h[0] + (1.0 if not d else d * 1.6), h[1] - 1.4, 5.6, 8.0 + [0, 1, 0, 1][frame], WLK_FIRE,
                  sway=[0, 1, 0, -1][frame])


# ===================================================================
# INQUISITOR (56) -- a tall buckled hat, a long coat, a holy chain
# ===================================================================

INQ_COAT = (84, 78, 92)
INQ_RED = (178, 40, 48)
INQ_COLLAR = (246, 246, 250)
INQ_CHAIN = (206, 210, 222)


def draw_inquisitor(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, head=0.95)
    d = r.d
    hs = _hold_side(r)
    swing = [0.0, 1.2, 0.0, -1.2][frame]
    if d:
        rig_arms(r, draw, INQ_COAT, (60, 54, 64), layer="far")
    rig_legs(r, draw, (60, 56, 66), (50, 44, 50))
    rig_robe(r, draw, INQ_COAT, flare=3.0, split=True, trim=INQ_RED)
    rig_belt(r, draw, INQ_RED, buckle=GOLD)
    # white falling-band collar
    cx = r.cx
    if not r.back:
        cel(draw, Poly([(cx - 4.4 + d * 1.6, r.sh_y - 2.4), (cx + 4.4 + d * 1.6, r.sh_y - 2.4), (cx + 2.6 + d * 1.6, r.sh_y + 2.4),
                        (cx - 2.6 + d * 1.6, r.sh_y + 2.4)]), INQ_COLLAR, sh=(0.6, 0.4))
        stroke(draw, [(cx + d * 1.6, r.sh_y - 2.0), (cx + d * 1.6, r.sh_y + 2.2)], 0.4, shade(INQ_COLLAR, 1.4))
    rig_arms(r, draw, INQ_COAT, (60, 54, 64), layer="near", hands=False)
    head_skull(r, draw, SKIN)
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    if not r.back:
        head_face(r, draw, SKIN, iris=(110, 110, 120), mood="sharp", mouth="frown", brows=True, brow_color=(70, 60, 60))
    rig_hair(r, draw, (90, 80, 76), "short", hat=True, skin=SKIN)
    # capotain: a tall, slightly tapered crown on a wide brim, gold buckle
    cx0 = hx - d * 0.6
    brim = Ell(cx0 + d * 1.0, hy - 6.2, 15.0 if not d else 13.8, 4.0)
    cel(draw, brim, (48, 44, 54), sh=(0.0, 1.2))
    crown = Poly([(cx0 - 7.4, hy - 6.4), (cx0 - 6.2, hy - 17.0), (cx0 + 6.2, hy - 17.0), (cx0 + 7.4, hy - 6.4)])
    cel(draw, crown, (48, 44, 54), sh=(1.4, 0.6), hi=(0.6, 0.6))
    cel(draw, Poly([(cx0 - 7.4, hy - 6.4), (cx0 + 7.4, hy - 6.4), (cx0 + 7.1, hy - 8.8), (cx0 - 7.1, hy - 8.8)]), INQ_RED, sh=None)
    if not r.back:
        bx = cx0 + d * 3.0
        layer = draw.sub()
        cel(layer, RRect(bx - 2.2, hy - 9.6, bx + 2.2, hy - 5.6, 0.5), GOLD, sh=None)
        RRect(bx - 1.1, hy - 8.6, bx + 1.1, hy - 6.6, 0.3).draw(layer, fill=(0, 0, 0, 0))
        draw.merge(layer)
    # a chain swinging from one hand, a gold holy symbol on the end
    h = hand_at(r, hs)
    end = (h[0] + (hs * 2.0 if not d else d * 3.0) + swing, h[1] + 7.6)
    n = 5
    for i in range(n + 1):
        t = i / float(n)
        x = h[0] + (end[0] - h[0]) * t + math.sin(t * math.pi) * swing * 0.4
        y = h[1] + 1.0 + (end[1] - h[1] - 1.0) * t
        cel(draw, Ell(x, y, 0.9, 0.7 if i % 2 else 0.9), INQ_CHAIN, sh=None, lw=0.5)
    cross(draw, end[0], end[1] + 2.4, 1.7, GOLD)
    for side in _sides(r):
        rig_hand(r, draw, side, (60, 54, 64))


MEDIEVAL_DRAW_FUNCTIONS = {
    'paladin': draw_paladin,
    'ranger': draw_ranger,
    'berserker': draw_berserker,
    'crusader': draw_crusader,
    'druid': draw_druid,
    'bard': draw_bard,
    'monk': draw_monk,
    'cleric': draw_cleric,
    'rogue': draw_rogue,
    'barbarian': draw_barbarian,
    'enchantress': draw_enchantress,
    'jester': draw_jester,
    'valkyrie': draw_valkyrie,
    'warlock': draw_warlock,
    'inquisitor': draw_inquisitor,
}


def main():
    for name, draw_func in MEDIEVAL_DRAW_FUNCTIONS.items():
        generate_character(name, draw_func=draw_func)
    print(f"\nGenerated {len(MEDIEVAL_DRAW_FUNCTIONS)} medieval character sprites.")


if __name__ == "__main__":
    main()
