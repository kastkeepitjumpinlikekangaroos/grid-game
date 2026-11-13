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
    sparkle, spear, star, stroke, xform, arm_pts, face_anchor, torso_shape, ms_brow, LASH, clipped,
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


def _pal_hammer(draw, x, y, ang, length=13.0):
    """The warhammer gripped at (x, y), its haft along `ang`: a gold head
    waisted at the haft and flaring to a steel face at either end, so that it
    reads as a hammer and not as one more gold plate beside the pauldron."""
    L = length
    cel(draw, Limb(xform([(-3.0, 0.0), (L + 4.2, 0.0)], x, y, ang), [1.05, 1.05], cap0=True), WOOD, sh=None)
    head = [(L - 2.1, -1.5), (L - 3.4, -5.6), (L + 3.4, -5.6), (L + 2.1, -1.5), (L + 2.1, 1.5), (L + 3.4, 5.6), (L - 3.4, 5.6),
            (L - 2.1, 1.5)]
    marks = [(Poly(xform([(L - 4.0, k * 3.9), (L + 4.0, k * 3.9), (L + 4.0, k * 6.0), (L - 4.0, k * 6.0)], x, y, ang)), STEEL)
             for k in (-1, 1)]
    marks.append((Poly(xform([(L - 2.6, -1.3), (L + 2.6, -1.3), (L + 2.6, 1.3), (L - 2.6, 1.3)], x, y, ang)), shade(PAL_GOLD, 1.2)))
    cel(draw, Poly(xform(head, x, y, ang)), PAL_GOLD, sh=(0.7, 0.7), regions=marks)


def _pal_helm(r, draw):
    """The winged helm. Head-on a gold dome with a wing either side; in
    profile an open-faced helm with a guard over the ear to the nape and the
    wing on its side, sweeping back; from behind it covers the head to the
    neck, a ridge down it and a blue rim."""
    hx, hy, rx, ry, d = r.hx, r.head_cy, r.head_rx, r.head_ry, r.d
    if not d:
        for s in (-1, 1):
            feather_wing(draw, hx + s * (rx - 0.4), hy - 5.0, s, 8.4, PAL_WHITE)
    dome = arc_pts(-0.4 if d else 0.0, hy - 0.8, rx + 1.2, ry + 1.0, 182, 358, 26)
    if d:
        guard = [(rx + 1.3, hy - 1.4), (-1.0, hy - 1.4), (-2.2, hy + 5.6), (-(rx - 1.0), hy + 7.4), (-(rx + 1.6), hy + 5.0)]
        cel(draw, Poly([(hx + d * u, y) for (u, y) in dome + guard]), PAL_GOLD, sh=(1.4, 1.0), hi=(0.8, 0.8))
        stroke(draw, [(hx - d * 1.0, hy - 1.6), (hx + d * (rx - 0.2), hy - 1.6)], 0.9, shade(PAL_GOLD, 1.2))
        gem(draw, hx + d * 3.2, hy - 4.4, 1.6, PAL_BLUE)
        feather_wing(draw, hx - d * 2.0, hy - 3.6, -d, 9.6, PAL_WHITE)
    elif r.back:
        neck = [(rx + 1.3, hy - 1.4), (rx - 0.4, hy + 6.0), (rx + 0.8, hy + 9.6), (4.0, hy + 10.6), (0.0, hy + 11.4)]
        pts = [(hx + u, y) for (u, y) in dome + neck] + [(hx - u, y) for (u, y) in neck[-2::-1]]
        ridge = Poly([(hx - 1.3, hy - ry - 3.0), (hx + 1.3, hy - ry - 3.0), (hx + 1.3, hy + 12.0), (hx - 1.3, hy + 12.0)])
        rim = Poly([(hx - rx - 3.0, hy + 6.2), (hx - 4.0, hy + 7.2), (hx, hy + 7.8), (hx + 4.0, hy + 7.2), (hx + rx + 3.0, hy + 6.2),
                    (hx + rx + 3.0, hy + 13.0), (hx - rx - 3.0, hy + 13.0)])
        cel(draw, Poly(pts), PAL_GOLD, sh=(1.4, 1.0), hi=(0.8, 0.8), regions=[(ridge, lit(PAL_GOLD, 0.9)), (rim, PAL_BLUE)])
        stroke(draw, [(hx - rx - 0.6, hy - 1.6), (hx + rx + 0.6, hy - 1.6)], 0.9, shade(PAL_GOLD, 1.2))
    else:
        cel(draw, Poly([(hx + u, y) for (u, y) in dome] + [(hx + rx + 1.3, hy - 1.4), (hx - rx - 1.3, hy - 1.4)]), PAL_GOLD,
            sh=(1.4, 1.0), hi=(0.8, 0.8))
        gem(draw, hx, hy - 4.4, 1.6, PAL_BLUE)
        stroke(draw, [(hx - rx + 0.4, hy - 1.6), (hx + rx - 0.4, hy - 1.6)], 0.9, shade(PAL_GOLD, 1.2))


def draw_paladin(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=1.14, head=0.96)
    d = r.d
    cx, sy = r.cx, r.sh_y
    hs, os_ = _hold_side(r), _off_side(r)
    rig_cape(r, draw, PAL_WHITE, layer="under")
    rig_legs(r, draw, PAL_GOLD, shade(PAL_GOLD, 0.8), width=1.08)
    rig_torso(r, draw, PAL_GOLD)
    if not r.back:
        cel(draw, Poly([(cx - 3.4 + d * 2, sy + 0.2), (cx + 3.4 + d * 2, sy + 0.2), (cx + 3.8 + d * 2, r.hip_y + 3.0),
                        (cx - 3.8 + d * 2, r.hip_y + 3.0)]), PAL_WHITE, sh=(0.8, 0.0))
        cross(draw, cx + d * 2, sy + 3.6, 2.0, PAL_BLUE)
    rig_belt(r, draw, PAL_BLUE, buckle=PAL_GOLD)
    if d:
        # In profile the shield is carried ahead of him on the far arm (behind
        # his back it was white on the white cape), and the hammer rests on
        # his near shoulder, its head behind him (held upright it sat on his chin).
        kite_shield(draw, cx + d * (5.4 + r.ph * 0.8), sy + 4.4, 4.4, 11.6, PAL_WHITE,
                    emblem=lambda dr, x, y: cross(dr, x + d * 0.6, y, 2.2, PAL_GOLD))
        hand = (cx + d * 3.4, sy + 4.0 + abs(r.ph) * 0.4)
        s0 = r.shoulder(d)
        cel(draw, Limb([s0, (cx - d * 1.4, sy + 5.8), (hand[0] - d * 0.6, hand[1] + 0.4)], [2.5, 2.3, 2.1]), PAL_GOLD, sh=(0.8, 0.4))
    else:
        for side in (-1, 1):
            rig_arms(r, draw, PAL_GOLD, PAL_GOLD, sides=(side,), hands=False, out=1.2 if side == hs else 0.0)
    for side in _sides(r):
        sx, s_y = r.shoulder(side)
        cel(draw, Ell(sx + (side * 1.2 if not d else 0), s_y - 1.0, 4.6, 3.2), PAL_GOLD, sh=(0.8, 0.8), hi=(0.5, 0.5))
        Ell(sx + (side * 1.2 if not d else 0), s_y - 1.2, 1.2, 1.0).draw(draw, fill=PAL_BLUE)
    if d:
        _pal_hammer(draw, hand[0], hand[1], -90 - d * (68 - r.ph * 3), 15.0)
    head_skull(r, draw, SKIN)
    if not r.back:
        head_face(r, draw, SKIN, iris=(70, 120, 200), mood="bright", mouth="smile", brows=False)
    rig_hair(r, draw, (240, 206, 120), "short", hat=True, skin=SKIN)
    _pal_helm(r, draw)
    rig_cape(r, draw, PAL_WHITE, layer="over")
    if r.back:
        # the order's cross on the cape
        cross(draw, cx + r.ph * 0.8, sy + 5.6, 4.0, PAL_BLUE)
    if d:
        cel(draw, Ell(hand[0], hand[1], 2.4, 2.4), PAL_GOLD, sh=(0.6, 0.6))
        return
    # head-on and from behind: the hammer up in one hand, the shield on the other arm
    h = hand_at(r, hs, 0.0, 0.0, 1.2)
    _pal_hammer(draw, h[0], h[1], -90 + hs * 18)
    for side in (-1, 1):
        rig_hand(r, draw, side, PAL_GOLD, out=1.2 if side == hs else 0.0)
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


def _rng_bow(draw, x, y, ang, flip, length=22.0, brace=3.6):
    """A longbow gripped at (x, y): its upper limb along `ang`, its belly
    toward `flip` and its string on the archer's side. (The kit's bow only
    stands upright, and upright in his hand its string crossed his face.)"""
    h = length / 2.0
    stroke(draw, xform([(-h + 0.3, -brace), (h - 0.3, -brace)], x, y, ang, 1.0, flip), 0.5, (236, 236, 226))
    stave = [(-h, -brace), (-h * 0.55, -brace * 0.34), (0.0, 0.0), (h * 0.55, -brace * 0.34), (h, -brace)]
    cel(draw, Limb(xform(stave, x, y, ang, 1.0, flip), [0.55, 1.15, 1.4, 1.15, 0.55], cap0=True), WOOD, sh=None)


def _rng_quiver(draw, bx, by, tx, ty):
    """A leather quiver from its foot (bx, by) to its mouth (tx, ty), three
    red-fletched arrows standing out of it."""
    ang = math.degrees(math.atan2(ty - by, tx - bx))
    ln = math.hypot(tx - bx, ty - by)
    for k in (-1.3, 0.1, 1.5):
        stroke(draw, xform([(ln - 0.5, k), (ln + 2.6, k * 1.15)], bx, by, ang), 0.6, (226, 222, 210))
        cel(draw, Poly(xform([(ln + 1.6, k * 1.15 - 1.0), (ln + 5.0, k * 1.2 - 0.9), (ln + 5.6, k * 1.2), (ln + 5.0, k * 1.2 + 0.9),
                              (ln + 1.6, k * 1.15 + 1.0)], bx, by, ang)), RNG_FEATHER, sh=None, lw=0.5)
    cel(draw, Poly(xform([(0.0, -1.9), (ln, -2.4), (ln, 2.4), (0.0, 1.9)], bx, by, ang)), RNG_LEATHER, sh=(0.5, 0.5))
    cel(draw, Poly(xform([(ln - 2.0, -2.5), (ln, -2.5), (ln, 2.5), (ln - 2.0, 2.5)], bx, by, ang)), shade(RNG_LEATHER, 1.4),
        sh=None, lw=0.6)


def _rng_arm(r, draw, side, hand):
    """The bow arm, from its shoulder to the hand on the grip, drawn as the
    rig draws its own."""
    sx, sy = r.shoulder(side)
    bend = (side if not r.d else -r.d * 0.4) * 0.7
    ex, ey = (sx + hand[0]) / 2.0 + bend, (sy + hand[1]) / 2.0 + 0.8
    cel(draw, Limb([(sx, sy), (ex, ey), (hand[0], hand[1] - 0.6)], [2.1, 1.9, 1.7]), RNG_LEATHER, sh=(0.8, 0.4))


def draw_ranger(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=0.96)
    d = r.d
    cx = r.cx
    hs = _hold_side(r)
    sy = r.sh_y
    # The bow hand: out to his side head-on and from behind; low and ahead of
    # him in profile, the bow canted forward, so that bow and string stand
    # clear of his face in every facing.
    if d:
        hand = (cx + d * (7.6 - r.ph * 0.6), r.hip_y - 2.4 - abs(r.ph) * 0.3)
        bow_ang, bow_flip = -90 + d * 14, d
    else:
        hand = hand_at(r, hs, 0.0, 2.0, 3.6)
        bow_ang, bow_flip = -90 + hs * 6, hs
    rig_cape(r, draw, RNG_DARK, layer="under", length=r.hip_y + 3.0)
    # The quiver over his right shoulder: its fletchings show past the
    # shoulder head-on, the whole of it in profile. From behind it is drawn
    # further down, over the cape.
    if d:
        _rng_quiver(draw, cx - d * 2.6, r.hip_y + 0.5, cx - d * 7.0, sy + 0.6)
        rig_arms(r, draw, RNG_LEATHER, SKIN, layer="far")
    elif not r.back:
        _rng_quiver(draw, cx - 3.4, sy + 4.5, cx - 8.2, sy - 1.0)
    rig_legs(r, draw, (98, 86, 70), (104, 72, 50))
    rig_torso(r, draw, RNG_GREEN)
    if r.front:
        stroke(draw, [(cx - 5.0, sy - 1.6), (cx + 5.0, r.waist_y + 0.6)], 1.2, RNG_LEATHER)
    rig_belt(r, draw, RNG_LEATHER, buckle=GOLD)
    if d:
        # in profile the bow goes under the arm that holds it, or its stave and string cut the sleeve in two
        _rng_bow(draw, hand[0], hand[1], bow_ang, bow_flip)
    else:
        rig_arms(r, draw, RNG_LEATHER, SKIN, sides=(-hs,), hands=False)
    _rng_arm(r, draw, d if d else hs, hand)
    rig_head(r, draw, SKIN, hair=(170, 110, 64), style="short", eye_color=(70, 130, 70), expression="smirk", hat=True)
    # the feathered cap: a peaked hunter's hat, which turns round when he does
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    m = -1 if r.back else 1
    if d:
        cap = Poly([(hx + d * (rx + 3.0), hy - 3.4), (hx + d * rx, hy - 7.0), (hx, hy - ry - 1.4), (hx - d * (rx - 2.0), hy - ry + 0.6),
                    (hx - d * (rx + 2.4), hy - 4.0), (hx - d * (rx + 1.0), hy - 2.6)])
    else:
        cap = Poly([(hx + m * u, y) for (u, y) in ((-rx - 2.0, hy - 2.4), (-rx + 1.0, hy - 7.6), (-2.0, hy - ry - 1.2),
                                                    (5.0, hy - ry - 0.2), (rx + 3.4, hy - 4.2), (rx + 1.0, hy - 2.4))])
    cel(draw, cap, RNG_GREEN, sh=(1.4, 1.0))
    stroke(draw, [(hx - m * (rx + 1.0), hy - 3.0), (hx + m * (rx + 0.6), hy - 3.0)] if not d else
           [(hx - d * (rx + 0.6), hy - 3.4), (hx + d * (rx + 1.4), hy - 3.4)], 1.0, RNG_DARK)
    k = -d if d else -m
    fx = hx + k * 5.0
    cel(draw, Limb([(fx, hy - 4.4), (fx + k * 3.4, hy - 11.0), (fx + k * 8.4, hy - 15.6)], [1.1, 2.2, 0.4]), RNG_FEATHER, sh=None)
    rig_cape(r, draw, RNG_DARK, layer="over", length=r.hip_y + 3.0)
    if r.back:
        # the cape's hood, down on his shoulders; the quiver and its strap over it
        cel(draw, Poly([(cx - 8.0, sy - 4.6), (cx - 4.0, sy - 5.6), (cx + 4.0, sy - 5.6), (cx + 8.0, sy - 4.6), (cx + 6.4, sy + 2.2),
                        (cx, sy + 4.8), (cx - 6.4, sy + 2.2)]), RNG_GREEN, sh=(0.8, 0.8))
        stroke(draw, [(cx + 6.4, sy - 2.6), (cx - 5.6, r.waist_y + 1.6)], 1.2, RNG_LEATHER)
        _rng_quiver(draw, cx + 2.8, r.waist_y + 2.4, cx + 6.6, sy - 2.6)
    if not d:
        _rng_bow(draw, hand[0], hand[1], bow_ang, bow_flip)
        rig_hand(r, draw, -hs, SKIN)
    cel(draw, Ell(hand[0], hand[1], 2.2, 2.2), SKIN, sh=(0.6, 0.6))


# ===================================================================
# BERSERKER (44) -- a wolf pelt for a hood, warpaint, twin axes
# ===================================================================

from sprite_base import bez as _ber_bez  # this block's own: the file's import list is shared

BER_WOLF = (140, 142, 160)
BER_WOLF_DK = (92, 94, 118)
BER_WOLF_LT = (226, 222, 216)
BER_NOSE = (44, 38, 48)
BER_HAIR = (212, 72, 42)
BER_PAINT = (70, 110, 222)
BER_FUR = (170, 126, 82)
BER_STEEL = (214, 222, 236)
# his own hair under the pelt: the wild fringe and side locks, without the
# crown of spikes, which stood up round the wolf's head like a helmet's crest
BER_HAIR_STYLE = dict(HAIR_STYLES["wild"], spikes=None, vol=1.2)
# head-on the wolf's lip lies on his brow, and the fringe stays under it
BER_HAIR_FRONT = dict(BER_HAIR_STYLE, fringe=((-0.6, 2.0), (-0.2, 2.4), (0.2, 2.4), (0.6, 2.0)), sweep=0.0)
BER_CUFF = (214, 180, 132)


def _ber_axe(draw, x, y, ang, flip, length=10.0, far=False):
    """A bearded hand axe gripped at (x, y): the haft along `ang`, the bit on
    the `flip` side, its beard swept back toward the hand."""
    L = length
    steel = BER_STEEL if not far else shade(BER_STEEL, 0.8)
    wood = WOOD if not far else shade(WOOD, 0.6)
    cel(draw, Limb(xform([(-2.8, 0.0), (L + 1.0, 0.0)], x, y, ang), [1.0, 0.9], cap0=True), wood, sh=None)
    poll = [(L - 4.0, -0.7), (L - 0.8, -0.7), (L - 1.2, -2.3), (L - 3.6, -2.3)]
    cel(draw, Poly(xform(poll, x, y, ang, 1.0, flip)), shade(steel, 0.9), sh=None)
    edge = _ber_bez((L + 1.8, 5.6), (L - 2.4, 8.8), (L - 6.8, 5.4), 8)
    bit = [(L - 4.4, 0.7), (L - 0.4, 0.7), (L + 0.5, 2.8)] + edge + [(L - 4.8, 3.0)]
    cheek = [(L - 4.4, 0.7), (L - 0.4, 0.7), (L + 0.5, 2.8), (L + 0.8, 4.0), (L - 2.4, 5.4), (L - 5.4, 4.0), (L - 4.8, 3.0)]
    cel(draw, Poly(xform(bit, x, y, ang, 1.0, flip)), steel, sh=None,
        regions=[(Poly(xform(cheek, x, y, ang, 1.0, flip)), shade(steel, 0.9))])


def _ber_ear(draw, x, y, lean, inner=True, far=False):
    """One of the wolf's ears, standing on (x, y) and leaning `lean`: dark at
    the tip, and dark inside where its opening shows."""
    col = BER_WOLF if not far else shade(BER_WOLF, 0.6)
    tip = (x + lean * 2.4, y - 8.8)
    shape = Poly([(x - 3.1, y + 1.4), (x - 2.2 + lean * 0.9, y - 4.0), tip, (x + 2.4 + lean * 1.4, y - 3.6), (x + 3.1, y + 1.4)])
    regions = [(Poly([(tip[0] - 3.0, tip[1] - 1.0), (tip[0] + 3.0, tip[1] - 1.0), (tip[0] + 2.6, tip[1] + 3.2),
                      (tip[0] - 2.6, tip[1] + 3.2)]), BER_WOLF_DK)]
    if inner:
        regions.append((Poly([(x - 1.5, y + 1.0), (x + lean * 1.6, y - 5.6), (x + 1.7, y + 1.0)]), BER_WOLF_DK))
    cel(draw, shape, col, sh=(0.5, 0.4), regions=regions)


def _ber_hood_front(r, draw):
    """The wolf's head worn as a hood, seen head-on: ears up, its muzzle down
    over his brow with a fang either side, its cheek ruff over his temples."""
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    for s in (-1, 1):
        _ber_ear(draw, hx + s * 7.2, hy - 11.0, s * 1.0)
    right = [(rx + 3.8, hy + 1.6), (rx + 1.2, hy + 0.9), (rx + 1.6, hy + 3.4), (rx - 1.4, hy + 0.4),
             (7.4, hy - 2.9), (4.4, hy - 3.1), (3.4, hy - 1.9), (1.8, hy - 1.0)]
    pts = arc_pts(hx, hy - 1.6, rx + 2.0, ry + 1.6, 180, 360, 28)
    pts += [(hx + u, y) for (u, y) in right] + [(hx, hy - 0.8)] + [(hx - u, y) for (u, y) in right[::-1]]
    marks = [(Poly([(hx - 2.4, hy - 9.6), (hx + 2.4, hy - 9.6), (hx + 4.8, hy - 3.4), (hx + 3.8, hy + 1.0), (hx - 3.8, hy + 1.0),
                    (hx - 4.8, hy - 3.4)]), BER_WOLF_LT)]
    for s in (-1, 1):
        marks.append((Poly([(hx + s * (rx - 2.0), hy - 0.4), (hx + s * (rx + 6.0), hy - 2.0), (hx + s * (rx + 6.0), hy + 6.0),
                            (hx + s * (rx - 2.0), hy + 6.0)]), BER_WOLF_LT))
    marks.append((Poly([(hx - 3.0, hy - ry - 5.0), (hx + 3.0, hy - ry - 5.0), (hx + 2.0, hy - 9.6), (hx - 2.0, hy - 9.6)]), BER_WOLF_DK))
    cel(draw, Poly(pts), BER_WOLF, sh=(1.2, 1.0), regions=marks)
    for s in (-1, 1):
        Poly([(hx + s * 3.6, hy - 6.0), (hx + s * 8.0, hy - 8.2), (hx + s * 8.3, hy - 6.8), (hx + s * 4.2, hy - 5.0)]).draw(draw, fill=BER_NOSE)
        Ell(hx + s * 5.9, hy - 6.5, 0.8, 0.7).draw(draw, fill=(252, 214, 70))
        Poly([(hx + s * 3.9 - 0.8, hy - 2.9), (hx + s * 3.9 + 0.8, hy - 2.9), (hx + s * 3.9, hy - 0.4)]).draw(draw, fill=(252, 250, 240))
    cel(draw, Ell(hx, hy - 2.4, 2.1, 1.5), BER_NOSE, sh=None, line=False)
    Ell(hx - 0.7, hy - 2.9, 0.7, 0.45).draw(draw, fill=(150, 144, 160))


def _ber_hood_side(r, draw):
    """The same in profile: the snout out over his brow like a cap's peak,
    nose at its tip and a fang under it, the ruff down behind his cheek."""
    hx, hy, rx, ry, d = r.hx, r.head_cy, r.head_rx, r.head_ry, r.d

    def loc(pts):
        return [(hx + d * u, y) for (u, y) in pts]

    _ber_ear(draw, hx + d * 1.8, hy - 12.6, -d * 0.4, inner=False, far=True)
    _ber_ear(draw, hx - d * 3.2, hy - 12.2, -d * 0.8)
    top = arc_pts(-0.4, hy - 1.6, rx + 1.8, ry + 1.6, 176, 320, 26)
    snout = [(rx + 0.4, hy - 8.6), (rx + 5.8, hy - 6.8), (rx + 7.0, hy - 5.6), (rx + 7.0, hy - 3.6), (rx + 5.6, hy - 2.2),
             (rx + 2.6, hy - 2.4), (rx - 1.6, hy - 2.9), (6.4, hy - 2.6), (-1.0, hy - 2.2), (-1.8, hy + 0.5)]
    ruff = [(-0.6, hy + 3.4), (-3.0, hy + 2.4), (-2.6, hy + 5.8), (-5.4, hy + 3.6), (-6.6, hy + 6.6), (-8.8, hy + 3.6),
            (-(rx + 1.4), hy + 5.2), (-(rx + 1.2), hy + 1.4)]
    pale = Poly(loc([(3.0, hy - 4.0), (rx + 9.0, hy - 5.0), (rx + 9.0, hy + 1.0), (3.0, hy + 1.0)]))
    crown = Limb(loc([(rx + 1.6, hy - 8.4), (5.0, hy - ry - 2.2), (-4.0, hy - ry - 2.6), (-(rx + 0.2), hy - 6.0),
                      (-(rx + 1.6), hy + 1.0)]), 2.3)
    cel(draw, Poly(loc(top + snout + ruff)), BER_WOLF, sh=(1.2, 1.0), regions=[(pale, BER_WOLF_LT), (crown, BER_WOLF_DK)])
    Poly(loc([(3.6, hy - 7.6), (8.2, hy - 6.6), (7.8, hy - 5.5), (4.0, hy - 6.1)])).draw(draw, fill=BER_NOSE)
    Ell(hx + d * 6.4, hy - 6.5, 0.8, 0.7).draw(draw, fill=(252, 214, 70))
    cel(draw, Ell(hx + d * (rx + 6.5), hy - 5.3, 1.6, 1.5), BER_NOSE, sh=None, line=False)
    fx = hx + d * (rx + 3.6)
    Poly([(fx - 0.8, hy - 2.5), (fx + 0.8, hy - 2.5), (fx + d * 0.2, hy + 0.3)]).draw(draw, fill=(252, 250, 240))


def _ber_pelt_back(r, draw, sway):
    """The whole pelt from behind: the wolf's head capping his, its ruff at
    his nape, the hide down his back with a dark stripe along the spine, and
    the tail swinging under a ragged hem."""
    cx, hx, hy, rx, ry = r.cx, r.hx, r.head_cy, r.head_rx, r.head_ry
    sw, sy, py = r.sh_w, r.sh_y, r.hip_y
    for s in (-1, 1):
        _ber_ear(draw, hx + s * 7.2, hy - 11.0, s * 1.0, inner=False)
    tip = (cx + sway * 3.4, r.base_y - 3.4)
    cel(draw, Limb([(cx, py + 1.6), (cx + 0.4 + sway * 1.8, py + 5.8), tip], [2.2, 3.3, 1.2]), BER_WOLF, sh=(0.8, 0.5),
        regions=[(Ell(tip[0], tip[1] + 0.6, 3.6, 2.6), BER_WOLF_LT)])
    right = [(rx + 3.4, hy + 1.4), (rx - 0.4, hy + 0.8), (8.6, hy + 3.4), (6.4, hy + 2.0), (6.2, hy + 8.6),
             (sw + 3.2, sy + 0.4), (sw + 3.8, sy + 3.2), (sw + 1.8, sy + 3.6), (sw + 1.2, py + 0.2)]
    hem = [(7.6, py + 4.2), (5.4, py + 2.2), (3.2, py + 4.6), (1.2, py + 2.6)]
    pts = arc_pts(hx, hy - 1.6, rx + 2.0, ry + 1.6, 180, 360, 28)
    pts += [(cx + u, y) for (u, y) in right] + [(cx + u + sway, y) for (u, y) in hem]
    pts += [(cx - u + sway, y) for (u, y) in hem[::-1]] + [(cx - u, y) for (u, y) in right[::-1]]
    stripe = Poly([(hx - 3.0, hy - ry - 5.0), (hx + 3.0, hy - ry - 5.0), (cx + 2.0 + sway, py + 5.0), (cx - 2.0 + sway, py + 5.0)])
    cel(draw, Poly(pts), BER_WOLF, sh=(1.4, 1.0), regions=[(stripe, BER_WOLF_DK)])


def draw_berserker(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=1.24, head=0.96)
    d = r.d
    cx = r.cx
    hx, hy, rx = r.hx, r.head_cy, r.head_rx
    sway = r.ph * 0.9
    leg, boot = (96, 76, 66), (88, 62, 46)
    if d:
        # the far hand's axe, carried low and forward
        rig_arms(r, draw, SKIN_TAN, SKIN_TAN, layer="far", hands=False, reach=0.2)
        h = hand_at(r, -d, 0.2)
        _ber_axe(draw, h[0], h[1], 90 - d * 58, d, far=True)
        rig_hand(r, draw, -d, SKIN_TAN, reach=0.2)
    # fur-topped boots
    for side in ((-d, d) if d else (-1, 1)):
        rig_legs(r, draw, leg, boot, sides=(side,))
        fx, fy = r.foot(side)
        cel(draw, Ell(fx + d * 0.3, fy - 2.5, 3.2, 1.5), BER_CUFF if r.near(side) else shade(BER_CUFF, 0.6), sh=(0.0, 0.5))
    rig_torso(r, draw, SKIN_TAN)
    if not r.back:
        stroke(draw, [(cx - 4.2 + d, r.waist_y - 2.4), (cx + 4.2 + d, r.waist_y - 2.4)], 1.5, BER_PAINT)
    # fur kilt, ragged at the hem
    if d:
        kilt = Poly([(cx - 5.0, r.waist_y + 0.6), (cx + 5.2, r.waist_y + 0.6), (cx + 6.0, r.hip_y + 2.6), (cx + 3.6, r.hip_y + 4.2),
                     (cx + 1.6, r.hip_y + 2.8), (cx - 0.6, r.hip_y + 4.4), (cx - 2.8, r.hip_y + 2.8), (cx - 4.8, r.hip_y + 4.2),
                     (cx - 6.0, r.hip_y + 2.6)])
    else:
        w = r.hip_w
        kilt = Poly([(cx - w - 0.6, r.waist_y + 0.6), (cx + w + 0.6, r.waist_y + 0.6), (cx + w + 1.8, r.hip_y + 2.6),
                     (cx + w - 1.4, r.hip_y + 4.2), (cx + 4.2, r.hip_y + 2.6), (cx + 2.2, r.hip_y + 4.4), (cx, r.hip_y + 2.8),
                     (cx - 2.2, r.hip_y + 4.4), (cx - 4.2, r.hip_y + 2.6), (cx - w + 1.4, r.hip_y + 4.2), (cx - w - 1.8, r.hip_y + 2.6)])
    cel(draw, kilt, BER_FUR, sh=(0.8, 0.6))
    rig_belt(r, draw, (84, 60, 44), buckle=(206, 206, 214))
    if d:
        # the hide down his back, the tail swinging at its hem
        def loc(pts):
            return [(hx + d * u, y) for (u, y) in pts]

        tip = loc([(-15.4 - sway * 1.6, r.hip_y + 7.0)])[0]
        cel(draw, Limb(loc([(-8.6, r.hip_y - 0.8), (-12.6 - sway * 0.8, r.hip_y + 2.8)]) + [tip], [1.8, 2.9, 1.0]), BER_WOLF, sh=(0.6, 0.5),
            regions=[(Ell(tip[0], tip[1], 3.4, 3.0), BER_WOLF_DK)])
        hide = Poly(loc([(-3.2, hy + 6.0), (-3.4, r.sh_y + 2.0), (-4.6, r.hip_y - 2.4), (-4.0 - sway * 0.3, r.hip_y + 1.8),
                         (-5.8, r.hip_y - 0.2), (-7.2 - sway * 0.6, r.hip_y + 2.4), (-9.0, r.hip_y + 0.2), (-10.6 - sway, r.hip_y + 1.6),
                         (-11.6, r.waist_y - 0.6), (-12.6, r.sh_y + 1.0), (-(rx + 2.4), hy + 6.2), (-(rx + 1.8), hy + 2.0), (-6.0, hy + 2.0)]))
        stripe = Limb(loc([(-(rx + 2.6), hy + 1.0), (-13.0, r.sh_y + 1.0), (-12.0, r.waist_y - 0.6), (-11.0 - sway, r.hip_y + 2.0)]),
                      [2.4, 2.2, 2.0, 1.8])
        cel(draw, hide, BER_WOLF, sh=(1.0, 0.8), regions=[(stripe, BER_WOLF_DK)])
    reach, out = (0.5, 0.0) if d else (0.0, 1.0)
    rig_arms(r, draw, SKIN_TAN, SKIN_TAN, layer="near", hands=False, reach=reach, out=out)
    for side in _sides(r):
        _, e, w, _ = arm_pts(r, side, reach, 0.0, out)
        mx, my = (e[0] + w[0]) / 2, (e[1] + w[1]) / 2
        stroke(draw, [(mx - 2.0, my - 0.2), (mx + 2.0, my + 0.2)], 1.3, BER_PAINT)
    if r.front:
        # the pelt's forepaws, over his shoulders and onto his chest
        for s in (-1, 1):
            paw = Limb([(cx + s * (r.sh_w - 0.8), r.sh_y - 2.2), (cx + s * 5.0, r.sh_y + 1.2), (cx + s * 3.6, r.sh_y + 4.4)], [2.1, 1.9, 2.2])
            cel(draw, paw, BER_WOLF, sh=(0.6, 0.5), regions=[(Ell(cx + s * 3.5, r.sh_y + 5.4, 3.0, 2.2), BER_WOLF_LT)])
    head_skull(r, draw, SKIN_TAN, ears=False)
    if not r.back:
        head_face(r, draw, SKIN_TAN, iris=(90, 140, 220), mood="sharp", mouth="grin", brows=False)
        e1, e2, ey, _, _ = face_anchor(r)
        for ex, o in (((e1, -1), (e2, 1)) if not d else ((e1, d),)):
            for k in (3.6, 5.2):
                stroke(draw, [(ex - o * 0.6, ey + k), (ex + o * 2.8, ey + k - 0.3)], 1.0, BER_PAINT)
    rig_hair(r, draw, BER_HAIR, BER_HAIR_STYLE if d else BER_HAIR_FRONT, hat=True, skin=SKIN_TAN)
    if r.back:
        _ber_pelt_back(r, draw, sway)
    elif d:
        _ber_hood_side(r, draw)
    else:
        _ber_hood_front(r, draw)
    # twin hand axes: out to his sides head-on, the near one up and ahead in profile
    for side in _sides(r):
        if d:
            h = hand_at(r, side, reach)
            _ber_axe(draw, h[0], h[1], -90 + d * 50, d)
        else:
            h = hand_at(r, side, reach, 0.0, out)
            _ber_axe(draw, h[0], h[1], -90 + side * (34 + r.ph * side * 5), side)
        rig_hand(r, draw, side, SKIN_TAN, reach=reach, out=out)


# ===================================================================
# CRUSADER (45) -- great helm with a cross visor, red cross, kite shield
# ===================================================================

from sprite_base import bez as _cru_bez  # this block's own: the file's import list is shared

# Steel a clear step under the white of his surcoat and shield. It was nearly
# their white, and the snow's: a pale lump with red crosses on it, and a helm
# that only its line held on the Snowglobe.
CRU_STEEL = (160, 172, 198)
CRU_RED = (206, 44, 52)
CRU_WHITE = (244, 244, 248)
CRU_SLIT = (40, 38, 54)
CRU_WOOD = (150, 104, 70)


def _cru_plume(draw, x, y, s, lift=0.0, back=0.0):
    """The red plume from its socket at (x, y) on the lid of the helm: up, and
    leaning over toward `s`. `back` lengthens it (in profile it streams behind
    him); `lift` is its tip riding the stride. One fluffy mass, fullest past
    its middle and round at its end, so that it is a feather and not a horn."""
    top = (x + s * (2.6 + back * 0.45), y - 7.4)
    tip = (x + s * (6.6 + back * 1.3), y - 5.2 + back * 0.45 + lift)
    path = _cru_bez((x, y + 0.8), (x + s * 0.2, y - 6.0), top, 4) + \
        _cru_bez(top, (x + s * (5.6 + back * 1.2), y - 8.8 + lift * 0.5), tip, 5)[1:]
    n = len(path) - 1
    parts = []
    for i, (px, py) in enumerate(path):
        t = i / float(n)
        rad = 1.8 + 1.2 * math.sin(math.pi * 0.5 * min(1.0, t / 0.55)) if t <= 0.55 else 3.0 - 1.5 * ((t - 0.55) / 0.45) ** 1.5
        parts.append(Ell(px, py, rad, rad * 0.92))
    blob(draw, parts, CRU_RED, sh=(0.7, 0.9))


def _cru_helm(r, draw):
    """The great helm: a flat-topped pot, its lid a lighter plane, a darker
    rim round its foot. In front the cross cut in it (eye slit and breathing
    slit), turned with his head in profile; behind, the strap the two halves
    are riveted along, which is not a cross: his back is not his face."""
    hx, hy, rx, ry, d = r.hx, r.head_cy, r.head_rx, r.head_ry, r.d
    top, foot, w = hy - ry + 0.2, hy + ry - 1.4, rx + 0.4
    helm = Poly([(hx - w, top + 4.4), (hx - rx * 0.6, top), (hx + rx * 0.6, top), (hx + w, top + 4.4),
                 (hx + w - 0.2, foot - 1.4), (hx + w - 1.5, foot), (hx - w + 1.5, foot), (hx - w + 0.2, foot - 1.4)])
    lid = Poly([(hx - w - 1, top - 1), (hx + w + 1, top - 1), (hx + w + 1, top + 2.9), (hx - w - 1, top + 2.9)])
    rim = Poly([(hx - w - 1, foot - 2.3), (hx + w + 1, foot - 2.3), (hx + w + 1, foot + 1), (hx - w - 1, foot + 1)])
    regions = [(lid, lit(CRU_STEEL, 0.7)), (rim, shade(CRU_STEEL, 0.9))]
    if r.back:
        regions.append((Poly([(hx - 1.4, top + 2.9), (hx + 1.4, top + 2.9), (hx + 1.4, foot - 2.3), (hx - 1.4, foot - 2.3)]),
                        shade(CRU_STEEL, 0.9)))
    cel(draw, helm, CRU_STEEL, sh=(1.8, 1.2), hi=(1.0, 1.0), regions=regions)
    line = ink(CRU_STEEL)
    clipped(draw, helm, lambda l: [stroke(l, [(hx - w, y), (hx + w, y)], 0.55, line) for y in (top + 2.9, foot - 2.3)])
    if r.back:
        for k in (-1, 1):
            stroke(draw, [(hx + k * 1.4, top + 3.2), (hx + k * 1.4, foot - 2.6)], 0.55, line)
        return
    # The cross. Head-on it is centred; in profile its upright is near the
    # front edge and the eye slit runs back from that edge along his near side.
    vx, ey = hx + d * 7.0, hy + 0.6
    x0, x1 = (vx - (rx - 2.0), vx + (rx - 2.0)) if not d else (hx - d * 2.8, hx + d * (rx - 0.8))
    for u in ((-5.0, -2.6, 2.6, 5.0) if not d else (-d * 5.0, -d * 2.6, d * 2.4)):
        stroke(draw, [(vx + u, hy + 3.6), (vx + u, hy + 6.0)], 0.9, shade(CRU_STEEL, 2.0))
    stroke(draw, [(x0, ey), (x1, ey)], 1.5, CRU_SLIT)
    stroke(draw, [(vx, hy - 4.4), (vx, hy + 6.6)], 1.4, CRU_SLIT)
    for ex in ((vx - 3.6, vx + 3.6) if not d else (vx - d * 3.8, vx + d * 2.4)):
        Ell(ex, ey, 0.7, 0.5).draw(draw, fill=(255, 255, 255))


def draw_crusader(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=1.16)
    d = r.d
    cx, sy = r.cx, r.sh_y
    hs, os_ = _hold_side(r), _off_side(r)
    red_cross = lambda dr, x, y: cross(dr, x, y, 2.8, CRU_RED)
    rig_cape(r, draw, CRU_RED, layer="under")
    if d:
        # In profile the kite shield is on his near arm, its face to the camera
        # from his shoulder to his shin (slung behind him it was a white lump
        # on his back, and the body under the helm a thin column), and the
        # sword is in his far hand, levelled ahead of him past the shield.
        fh = (cx + d * (9.6 + r.ph * 0.6), sy + 6.8 - abs(r.ph) * 0.4)
        blade(draw, fh[0], fh[1], -90 + d * (58 + r.ph * 3), length=13.0, width=1.8, flip=d)
        cel(draw, Limb([r.shoulder(-d), (cx + d * 4.4, sy + 6.4), (fh[0] - d * 0.8, fh[1] + 0.2)], [2.5, 2.3, 2.1]),
            shade(CRU_STEEL, 0.6), sh=(0.8, 0.4))
        cel(draw, Ell(fh[0], fh[1], 2.4, 2.4), shade(CRU_STEEL, 0.5), sh=(0.6, 0.6))
    rig_legs(r, draw, CRU_STEEL, shade(CRU_STEEL, 1.0), width=1.08)
    rig_torso(r, draw, CRU_STEEL)
    # white surcoat with the red cross
    if d:
        sur = Poly([(cx - d * 2.6, sy - 1.6), (cx + d * 3.8, sy - 1.2), (cx + d * 4.8, r.hip_y + 3.6), (cx - d * 3.6, r.hip_y + 3.6)])
    else:
        sur = Poly([(cx - 4.4, sy - 1.8), (cx + 4.4, sy - 1.8), (cx + 5.2, r.hip_y + 3.8), (cx - 5.2, r.hip_y + 3.8)])
    cel(draw, sur, CRU_WHITE, sh=(0.8, 0.6))
    cross(draw, cx + d * 1.6, sy + 2.6, 2.4, CRU_RED)
    rig_belt(r, draw, (100, 70, 50), buckle=GOLD)
    if d:
        rig_arms(r, draw, CRU_STEEL, CRU_STEEL, layer="near", hands=False)
    else:
        # head-on and from behind: the sword up in one hand, held out from the hip
        rig_arms(r, draw, CRU_STEEL, CRU_STEEL, hands=False, sides=(os_,))
        rig_arms(r, draw, CRU_STEEL, CRU_STEEL, hands=False, sides=(hs,), out=1.4)
    for side in _sides(r):
        sx, s_y = r.shoulder(side)
        cel(draw, Ell(sx + (side * 1.0 if not d else 0), s_y - 0.8, 4.2, 2.8), CRU_STEEL, sh=(0.6, 0.6), hi=(0.4, 0.4))
    if d:
        kite_shield(draw, cx + d * (1.6 - r.ph * 0.5), sy + 7.6 + abs(r.ph) * 0.4, 5.6, 13.4, CRU_WHITE, rim=CRU_STEEL, emblem=red_cross)
    head_skull(r, draw, CRU_STEEL, ears=False)
    _cru_helm(r, draw)
    # the plume: it falls to his left head-on (so to the other side of the
    # picture from behind) and streams back in profile
    px, py = r.hx, r.head_cy - r.head_ry + 0.4
    lift = [0.0, 1.0, 0.0, -0.8][frame]
    if d:
        _cru_plume(draw, px - d * 2.0, py, -d, lift=lift, back=4.2)
    else:
        _cru_plume(draw, px, py, 1 if r.back else -1, lift=lift)
    rig_cape(r, draw, CRU_RED, layer="over")
    if d:
        return
    if r.back:
        # the order's cross on his cape, white on red: the Paladin's back is blue on white
        cross(draw, cx + r.ph * 0.8, sy + 5.4, 3.8, CRU_WHITE)
    h = hand_at(r, hs, 0.0, 0.0, 1.4)
    blade(draw, h[0], h[1], -90 + hs * 26, length=13.0, width=1.8)
    rig_hand(r, draw, hs, CRU_STEEL, out=1.4)
    h2 = hand_at(r, os_)
    if r.back:
        # the shield from behind: its boards, and his arm through the straps
        kite_shield(draw, h2[0] + os_ * 1.6, h2[1] - 3.4, 5.6, 12.6, CRU_WOOD, rim=CRU_STEEL)
        rig_arms(r, draw, CRU_STEEL, CRU_STEEL, sides=(os_,))
    else:
        rig_hand(r, draw, os_, CRU_STEEL)
        kite_shield(draw, h2[0] + os_ * 1.6, h2[1] - 3.4, 5.6, 12.6, CRU_WHITE, rim=CRU_STEEL, emblem=red_cross)


# ===================================================================
# DRUID (46) -- antlers from a leaf circlet, moss cloak, living staff
# ===================================================================

from sprite_base import bez as _dru_bez  # this block's own: the file's import list is shared

DRU_CLOAK = (92, 138, 72)
DRU_ROBE = (140, 100, 66)
DRU_HAIR = (122, 76, 50)
DRU_ANTLER = (226, 204, 164)
DRU_GEM = (130, 255, 150)
# Long locks either side of her face head-on. In profile the kit's long hair
# is a slab deeper than her body, so there it is cut at the nape, and from the
# side and from behind it is gathered into one braid down her back.
DRU_HAIR_FRONT = dict(HAIR_STYLES["long"], back=9.5)
DRU_HAIR_SIDE = dict(HAIR_STYLES["long"], back=9.5, side=6.0)


def _dru_leaf(draw, x, y, ang, length, width, color):
    """A pointed leaf from its stem at (x, y), lined thinly enough to stay
    green: the kit's leaf, at this size, is nearly all ink."""
    w = width * 0.5
    upper = _dru_bez((0.0, 0.0), (length * 0.4, -w * 1.7), (length, 0.0), 6)
    lower = _dru_bez((length, 0.0), (length * 0.4, w * 1.7), (0.0, 0.0), 6)
    cel(draw, Poly(xform(upper + lower[1:-1], x, y, ang)), color, sh=None, lw=0.6,
        regions=[(Poly(xform(lower, x, y, ang)), shade(color, 0.8))])


def _dru_antler(draw, bx, by, s, far=False):
    """One antler rooted at (bx, by): a beam curving out to `s` and back in, a
    brow tine inside it and two tines outside. One mass, lined in a mid brown
    so that the bone colour survives (a dark line each side left none)."""
    col = DRU_ANTLER if not far else shade(DRU_ANTLER, 1.4)
    parts = [Limb([(bx, by + 0.6), (bx + s * 3.2, by - 4.2), (bx + s * 5.0, by - 8.6), (bx + s * 4.0, by - 12.6)], [1.5, 1.3, 1.1, 0.5]),
             Limb([(bx + s * 1.2, by - 1.6), (bx - s * 0.6, by - 5.8)], [1.0, 0.5]),
             Limb([(bx + s * 5.0, by - 8.6), (bx + s * 8.2, by - 11.2)], [0.9, 0.5])]
    if not far:
        parts.append(Limb([(bx + s * 3.2, by - 4.2), (bx + s * 7.0, by - 6.4)], [1.0, 0.5]))
    blob(draw, parts, col, sh=(0.5, 0.5), lw=0.6, line_color=shade(col, 2.6))


def _dru_braid(draw, x0, y0, x1, y1):
    """Her hair gathered into one thick braid: a single mass, bound near its
    end with a ring of antler bone."""
    n = 6
    parts = []
    for i in range(n):
        t = i / float(n - 1)
        parts.append(Ell(x0 + (x1 - x0) * t + (0.7 if i % 2 else -0.7), y0 + (y1 - y0) * t, 3.3 - 0.9 * t, 2.3))
    parts.append(Poly([(x1 - 2.0, y1 + 1.0), (x1 + 2.0, y1 + 1.0), (x1 + 0.4, y1 + 5.0)]))
    blob(draw, parts, DRU_HAIR, sh=(0.9, 0.7), lw=0.8)
    cel(draw, RRect(x1 - 1.7, y1 + 0.6, x1 + 2.5, y1 + 2.4, 0.8), DRU_ANTLER, sh=None, lw=0.6)


def _dru_staff(draw, foot, top):
    """The living staff from `foot` to `top`: it curls at its head round a
    seed of green light, two leaves in bud below the curl."""
    dx, dy = top[0] - foot[0], top[1] - foot[1]
    ln = math.hypot(dx, dy)
    ux, uy = dx / ln, dy / ln
    cel(draw, Limb([foot, top], [1.25, 1.0]), WOOD, sh=(0.5, 0.0))
    c = (top[0] + ux * 2.9, top[1] + uy * 2.9)
    a0 = math.degrees(math.atan2(-uy, -ux))
    cel(draw, Limb(arc_pts(c[0], c[1], 3.3, 3.3, a0 - 30, a0 + 290, 12), [1.05] * 11 + [0.8, 0.5]), WOOD, sh=None)
    ang = math.degrees(math.atan2(uy, ux))
    for k in (-1, 1):
        _dru_leaf(draw, top[0] - ux * 2.4, top[1] - uy * 2.4, ang + k * 64, 4.6, 3.0, lit(DRU_CLOAK, 0.5))
    cel(draw, Ell(c[0], c[1], 2.1, 2.1), DRU_GEM, sh=None, lw=0.6)
    Ell(c[0] - 0.6, c[1] - 0.6, 0.75, 0.75).draw(draw, fill=(255, 255, 255))


def _dru_arm(r, draw, side, hand):
    """The staff arm, from its shoulder to a hand placed on the staff (the
    rig's arms end at the hip), drawn as the rig draws its own."""
    sx, sy = r.shoulder(side)
    bend = (side if not r.d else -r.d * 0.4) * 0.7
    ex, ey = (sx + hand[0]) / 2.0 + bend, (sy + hand[1]) / 2.0 + 0.9
    cel(draw, Limb([(sx, sy), (ex, ey), (hand[0], hand[1] - 0.6)], [2.2, 2.0, 1.8]), DRU_ROBE, sh=(0.8, 0.4))


def _dru_cloak_back(r, draw, sway):
    """The moss cloak from behind, its hem cut in leaf points."""
    cx, w, b = r.cx, r.sh_w, r.base_y - 3.4
    pts = [(cx - w + 0.6, r.sh_y - 2.8), (cx + w - 0.6, r.sh_y - 2.8), (cx + w + 2.6 + sway, b - 2.6), (cx + w - 0.4 + sway, b + 0.4),
           (cx + 3.2 + sway, b - 2.0), (cx + sway, b + 1.4), (cx - 3.2 + sway, b - 2.0), (cx - w + 0.4 + sway, b + 0.4),
           (cx - w - 2.6 + sway, b - 2.6)]
    vein = Poly([(cx - 0.8, r.sh_y), (cx + 0.8, r.sh_y), (cx + 1.3 + sway, b + 1.4), (cx - 1.3 + sway, b + 1.4)])
    cel(draw, Poly(pts), DRU_CLOAK, sh=(1.8, 0.0), regions=[(vein, shade(DRU_CLOAK, 0.8))])


def draw_druid(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, head=0.95)
    d = r.d
    cx = r.cx
    hs = _hold_side(r)
    hx, hy, ry = r.hx, r.head_cy, r.head_ry
    sway = r.ph * 0.9
    # The staff: planted out to her side head-on and from behind; in profile
    # ahead of her and leaning forward, where one held upright up her middle
    # stood its head on her face.
    foot_y = r.base_y - 1.0
    if d:
        foot = (cx + d * (6.4 + r.ph * 1.0), foot_y)
        top = (cx + d * 15.6, hy - 9.5)
        hand_y = r.hip_y - 4.6
    else:
        h0 = hand_at(r, hs, 0.0, 1.5, 4.6)
        foot = (h0[0] - hs * 0.8, foot_y)
        top = (h0[0] + hs * 2.4, hy - 7.0)
        hand_y = h0[1]
    t = (foot_y - hand_y) / (foot_y - top[1])
    hand = (foot[0] + (top[0] - foot[0]) * t, hand_y)

    rig_cape(r, draw, DRU_CLOAK, layer="under")
    if d:
        rig_arms(r, draw, DRU_ROBE, SKIN, layer="far")
    rig_legs(r, draw, shade(DRU_ROBE, 1.0), (96, 70, 50))
    rig_robe(r, draw, DRU_ROBE, trim=DRU_CLOAK, flare=3.0)
    rig_belt(r, draw, DRU_CLOAK, buckle=DRU_GEM)
    if d:
        _dru_arm(r, draw, d, hand)
    else:
        rig_arms(r, draw, DRU_ROBE, SKIN, sides=(-hs,), hands=False)
        _dru_arm(r, draw, hs, hand)
    # the cloak's mantle over her shoulders, its edge cut in leaf points
    sy, w = r.sh_y, r.sh_w
    if d:
        cel(draw, Poly([(cx - d * 3.8, sy - 2.4), (cx + d * 3.4, sy - 2.2), (cx + d * 4.8, sy + 1.6), (cx + d * 3.2, sy + 4.8),
                        (cx + d * 1.4, sy + 3.0), (cx - d * 0.2, sy + 5.8), (cx - d * 2.2, sy + 3.4), (cx - d * 4.2, sy + 5.2),
                        (cx - d * 5.2, sy + 1.0)]), DRU_CLOAK, sh=(0.8, 0.6))
    elif not r.back:
        right = [(w + 1.4, sy - 1.8), (w + 3.4, sy + 4.4), (w + 0.8, sy + 3.2), (w - 0.8, sy + 6.0), (3.6, sy + 3.4), (2.2, sy + 5.2)]
        cel(draw, Poly([(cx + u, y) for (u, y) in right] + [(cx, sy + 2.0)] + [(cx - u, y) for (u, y) in right[::-1]]),
            DRU_CLOAK, sh=(0.8, 0.6))
    if d:
        _dru_braid(draw, hx - d * 10.2, hy + 5.0, hx - d * (10.0 + sway), sy + 9.5)
    rig_head(r, draw, SKIN, hair=DRU_HAIR, style=DRU_HAIR_SIDE if d else DRU_HAIR_FRONT, eye_color=(80, 150, 80),
             expression="smile", mood="calm")
    # antlers: a pair, the far one a step ahead of the near one in profile
    by = hy - ry + 1.6
    if d:
        _dru_antler(draw, hx + d * 3.4, by + 0.6, -d, far=True)
        _dru_antler(draw, hx - d * 1.4, by, -d)
    else:
        for s in (-1, 1):
            _dru_antler(draw, hx + s * 4.6, by, s)
    # leaf circlet, over their roots
    for i, u in enumerate((-7.0, -3.4, 0.0, 3.4, 7.0) if not d else (-d * 3.4, d * 0.4, d * 4.2)):
        _dru_leaf(draw, hx + u, hy - ry + 3.8 - (abs(u) < 1) * 0.6, -90 + u * 8, 4.4, 3.0,
                  lit(DRU_CLOAK, 0.5) if i % 2 == 0 else DRU_CLOAK)
    if r.back:
        _dru_cloak_back(r, draw, sway)
        _dru_braid(draw, cx, hy + 6.5, cx + sway * 1.2, sy + 9.5)
    _dru_staff(draw, foot, top)
    cel(draw, Ell(hand[0], hand[1], 2.2, 2.2), SKIN, sh=(0.6, 0.6))
    if not d:
        rig_hand(r, draw, -hs, SKIN)


# ===================================================================
# BARD (47) -- plumed beret, gold-trimmed doublet, an oversized lute
# ===================================================================

from sprite_base import bez as _bard_bez, rot_pts as _bard_rot  # this block's own: the file's import list is shared

BARD_PURPLE = (150, 70, 180)
BARD_RED = (220, 60, 70)
BARD_HAIR = (230, 170, 80)
BARD_LUTE = (196, 130, 70)
# The lute's soundboard is pale spruce inside a rim of the bowl's wood, so that
# its round face reads against the purple doublet; its neck is dark and
# fretted (two white strings down a dark neck were a drawn sword at game size).
BARD_TOP = (246, 212, 140)
BARD_NECK = (122, 78, 54)
BARD_PLUME = (255, 236, 120)
BARD_NOTE = (124, 52, 164)


def _bard_lute(draw, x, y, ang, flip=1.0, body=True):
    """The lute, its bowl centred on (x, y) and its neck along `ang`: a pear
    of a body, a fretted neck, and the pegbox bent back from its end (to the
    `flip` side), which is what makes it a lute and not a banjo. Without
    `body` only the neck is drawn (from behind the bowl is in front of him,
    and a sliver of it showed under the corner of his cape)."""
    def at(pts):
        return xform(pts, x, y, ang, 1.0, flip)
    nut = 15.0
    cel(draw, Limb(at([(nut - 0.4, 0.3), (nut + 2.0, 5.4)]), [1.5, 1.1], cap0=True), shade(BARD_LUTE, 1.3), sh=None)
    for u, v in ((nut + 0.5, 2.1), (nut + 1.4, 4.1)):
        px, py = at([(u, v)])[0]
        Ell(px, py, 0.45, 0.45).draw(draw, fill=GOLD)
    cel(draw, Limb(at([(5.4, 0.0), (nut, 0.0)]), [1.35, 1.1]), BARD_NECK, sh=None)
    for u in (9.0, 11.0, 13.0):
        stroke(draw, at([(u, -0.75), (u, 0.75)]), 0.5, GOLD)
    if not body:
        return
    rx, ry = 7.0, 6.2
    bowl = [(-0.9 + rx * math.cos(math.radians(a)), ry * math.sin(math.radians(a))) for a in range(50, 311, 10)]
    body = Poly(at(bowl + [(7.8, -1.4), (7.8, 1.4)]))
    c = at([(-0.4, 0.0)])[0]
    cel(draw, body, BARD_LUTE, sh=(1.0, 1.0))
    cel(draw, body.scaled(0.8, c[0], c[1]), BARD_TOP, sh=None, line=False)
    for v in (-0.55, 0.55):
        stroke(draw, at([(-4.0, v), (6.2, v * 0.8)]), 0.35, mix(BARD_TOP, BARD_NECK, 0.45))
    rose = at([(1.2, 0.0)])[0]
    Ell(rose[0], rose[1], 1.9, 1.9).draw(draw, fill=shade(BARD_NECK, 1.2))
    Poly(at([(-4.8, -2.2), (-3.7, -2.2), (-3.7, 2.2), (-4.8, 2.2)])).draw(draw, fill=BARD_NECK)


def _bard_arm(draw, sh, el, wr, far=False):
    """An arm bent at the elbow, from its shoulder to a wrist on the lute (the
    rig's arms hang to the hip): the sleeve as one mass, and its gold cuff."""
    col = BARD_PURPLE if not far else shade(BARD_PURPLE, 0.6)
    blob(draw, [Limb([sh, el], [2.2, 2.0], cap0=True), Limb([el, wr], [2.0, 1.8])], col, sh=(0.8, 0.4))
    cel(draw, Ell(wr[0], wr[1], 1.9, 1.4), GOLD if not far else shade(GOLD, 0.6), sh=None, lw=0.6)


def _bard_hand(draw, h, far=False):
    cel(draw, Ell(h[0], h[1], 2.2, 2.2), SKIN if not far else shade(SKIN, 0.5), sh=(0.6, 0.6))


def _bard_plume(draw, base, c1, peak, c2, tip):
    """The ostrich plume pinned to the band at `base`: up to `peak`, then
    arching over and drooping to `tip` (`c1` and `c2` pull the two curves).
    One soft mass, fullest at the top of its arch, with a brooch on its quill:
    the Ranger's cap in this file has the straight blade of a feather."""
    path = _bard_bez(base, c1, peak, 5) + _bard_bez(peak, c2, tip, 6)[1:]
    n = len(path) - 1
    wd = []
    for i in range(n + 1):
        t = i / float(n)
        wd.append(0.7 + 2.2 * math.sin(math.pi * 0.5 * min(1.0, t / 0.55)) if t <= 0.55 else 2.9 - 1.5 * ((t - 0.55) / 0.45) ** 1.6)
    cel(draw, Limb(path, wd, cap0=True), BARD_PLUME, sh=(0.7, 0.9), lw=0.7)
    stroke(draw, path[:9], 0.45, shade(BARD_PLUME, 1.6))
    cel(draw, Ell(base[0], base[1], 1.4, 1.4), GOLD, sh=None, lw=0.6)
    Ell(base[0] - 0.4, base[1] - 0.4, 0.45, 0.45).draw(draw, fill=(255, 255, 255))


def _bard_beret(r, draw, s):
    """The beret: its band round his head at the hairline, and over it a soft
    puff wider than the head that slouches down toward `s` (his right side
    head-on, the back of his head in profile). It was a flat red plate."""
    hx, top, rx = r.hx, r.head_cy - r.head_ry, r.head_rx
    if r.d:
        mc, mrx, lobe = (hx + s * 1.6, top - 0.4), rx + 1.2, Ell(hx + s * 10.2, top + 3.2, 5.6, 4.6)
        band = ((hx - s * 10.8, top + 3.8), (hx + s * 0.5, top + 6.6), (hx + s * 11.6, top + 6.8))
    else:
        mc, mrx, lobe = (hx + s * 0.8, top - 0.6), rx + 2.2, Ell(hx + s * 10.6, top + 3.2, 6.0, 4.8)
        band = ((hx - s * 11.0, top + 3.4), (hx + s * 0.5, top + 6.6), (hx + s * 12.0, top + 6.6))
    cel(draw, Limb(_bard_bez(band[0], band[1], band[2], 8), 1.15, cap0=True), shade(BARD_RED, 1.3), sh=None)
    main = Poly(_bard_rot(Ell(mc[0], mc[1], mrx, 5.6).pts, mc[0], mc[1], s * 7.0))
    blob(draw, [main, lobe], BARD_RED, sh=(1.2, 1.0))


def _bard_note(draw, x, y):
    """One note in the air, its head on (x, y): big enough to be a note at
    game size. (There were two, specks stuck on the edge of his hat that
    changed places every frame.)"""
    Poly(_bard_rot(Ell(x, y, 1.9, 1.45).pts, x, y, -22.0)).draw(draw, fill=BARD_NOTE)
    stroke(draw, [(x + 1.5, y - 0.3), (x + 1.5, y - 6.0)], 0.95, BARD_NOTE)
    Limb(_bard_bez((x + 1.5, y - 6.0), (x + 4.2, y - 5.0), (x + 3.6, y - 2.4), 5), [0.55, 0.6, 0.6, 0.5, 0.4, 0.25]).draw(
        draw, fill=BARD_NOTE)


def draw_bard(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame)
    d = r.d
    cx, sy, wy = r.cx, r.sh_y, r.waist_y
    # His left is the right of the picture head-on and its left from behind:
    # the lute's neck, the hand that frets it and the plume are on his left,
    # the beret slouches to his right. In profile the neck points ahead.
    m = -1 if r.back else 1
    k = d if d else m
    strum = [0.0, 1.2, 0.0, -1.0][frame]
    lx, ly = (cx + d * 2.4, wy + 2.0) if d else (cx - m * 1.6, wy + 1.8)
    ang = -90 + k * (52 if d else 57)
    at = lambda u, v: xform([(u, v)], lx, ly, ang, 1.0, k)[0]
    fret, pluck = at(9.6, 0.2), at(-1.9, 0.9 + strum)
    if d:
        f_sh, f_el = r.shoulder(-d), (cx + d * 4.4, sy + 5.8)
        s_sh, s_el = r.shoulder(d), (cx - d * 1.6, wy + 0.8)
    else:
        f_sh, f_el = r.shoulder(m), (cx + m * 9.4, sy + 6.4)
        s_sh, s_el = r.shoulder(-m), (cx - m * 8.8, wy - 0.6)
    f_wr = (fret[0] + (f_el[0] - fret[0]) * 0.3, fret[1] + (f_el[1] - fret[1]) * 0.3)
    s_wr = (pluck[0] + (s_el[0] - pluck[0]) * 0.22, pluck[1] + (s_el[1] - pluck[1]) * 0.22)

    rig_cape(r, draw, BARD_RED, layer="under", length=r.hip_y + 2.0)
    if r.back:
        # from behind the lute is on the far side of him: its neck and pegbox
        # stand out past his shoulder, his elbows out either side
        _bard_lute(draw, lx, ly, ang, k, body=False)
        _bard_hand(draw, fret)
        _bard_arm(draw, f_sh, f_el, f_wr)
        _bard_arm(draw, s_sh, s_el, s_wr)
    elif d:
        _bard_arm(draw, f_sh, f_el, f_wr, far=True)
    rig_legs(r, draw, (90, 70, 110), (110, 70, 50))
    rig_torso(r, draw, BARD_PURPLE)
    if not r.back:
        stroke(draw, [(cx - 5.0 + d, sy - 1.4), (cx + d * 2.0, wy - 1.0), (cx + 5.0 + d, sy - 1.4)], 1.0, GOLD)
    rig_belt(r, draw, (90, 60, 60), buckle=GOLD)
    if not r.back:
        # the lute across his front: one hand up on the neck, the other
        # strumming over the rose (they sat level either side of it: a tray)
        if not d:
            _bard_arm(draw, f_sh, f_el, f_wr)
        _bard_lute(draw, lx, ly, ang, k)
        _bard_hand(draw, fret, far=bool(d))
        _bard_arm(draw, s_sh, s_el, s_wr)
        _bard_hand(draw, pluck)
    rig_head(r, draw, SKIN, hair=BARD_HAIR, style="swept", eye_color=(110, 80, 160), expression="grin" if frame % 2 else "smile",
             hat=True)
    hx, hy, top = r.hx, r.head_cy, r.head_cy - r.head_ry
    sway, lag = r.ph * 1.0, (0.8 if frame % 2 else 0.0)
    _bard_beret(r, draw, -k)
    if d:
        # in profile it springs from the band behind his ear and trails out
        # behind his head (arched over the beret it was a yellow stripe on it)
        back = abs(sway) * 0.8
        _bard_plume(draw, (hx - d * 2.0, top + 5.4), (hx - d * 4.0, top - 6.0), (hx - d * 10.5, top - 8.4),
                    (hx - d * (18.5 + back), top - 10.0 + lag * 0.5), (hx - d * (20.5 + back), top - 0.5 + lag))
    else:
        bx, by = hx + k * 9.2, top + 3.6
        _bard_plume(draw, (bx, by), (bx + k * 0.2, by - 9.2), (bx + k * 4.6, by - 11.2),
                    (bx + k * 10.4 + sway * 0.6, by - 12.6), (bx + k * 11.0 + sway, by - 5.6 + lag))
    rig_cape(r, draw, BARD_RED, layer="over", length=r.hip_y + 2.0)
    if r.back:
        # the short cape from behind: a gold hem, and the lute's strap over it
        # from his left shoulder, where the neck stands out, to his right hip
        w, bottom, cs = r.sh_w, r.hip_y + 2.0, r.ph * 1.2
        t = 1.0 - 1.7 / (bottom - (sy - 2.8))
        cel(draw, Poly([(cx - w + 0.6 + (cs - 2.8) * t, bottom - 1.7), (cx + w - 0.6 + (cs + 2.8) * t, bottom - 1.7),
                        (cx + w + 2.2 + cs, bottom), (cx - w - 2.2 + cs, bottom)]), GOLD, sh=None, lw=0.6)
        cel(draw, Limb([(cx - 5.4, sy - 2.2), (cx + 6.4 + cs * 0.7, bottom - 2.4)], [1.0, 1.0]), LEATHER, sh=None, lw=0.6)
    # one note in the air: by his ear head-on, left behind him as he walks
    if d:
        _bard_note(draw, hx - d * 19.6 - 1.4, hy + 5.0)
    else:
        _bard_note(draw, hx - k * 19.4 - 1.4, hy + 4.2)


# ===================================================================
# MONK (48) -- shaven head, saffron robes, prayer beads, wrapped fists
# ===================================================================

from sprite_base import bez as _monk_bez  # this block's own: the file's import list is shared

MONK_ROBE = (244, 160, 56)
MONK_RED = (190, 50, 44)
MONK_BEAD = (134, 84, 50)
MONK_WRAP = (248, 238, 214)
# Linen is lined in a warm brown: in the kit's slate ink the fists were cold
# white orbs on a warm figure.
MONK_WRAP_LINE = (156, 118, 86)
MONK_SKIN = (236, 184, 140)     # a touch deeper than the kit's tan (deeper still and the head closes on the robe)


def _monk_arm(r, draw, side, reach):
    """A bare arm, bound in linen from below the elbow to a big fist: the
    wraps are his weapon, and a white ball at the end of a bare arm was an orb."""
    s, e, w, h = arm_pts(r, side, reach)
    near = r.near(side)
    skin = MONK_SKIN if near else shade(MONK_SKIN, 0.6)
    wrap = MONK_WRAP if near else shade(MONK_WRAP, 0.5)
    aw = 2.2 * min(r.build, 1.3)
    cel(draw, Limb([s, e, w], [aw, aw * 0.9, aw * 0.82]), skin, sh=(0.8, 0.4))
    e2 = (e[0] + (w[0] - e[0]) * 0.25, e[1] + (w[1] - e[1]) * 0.25)
    cel(draw, Limb([e2, w], [aw * 0.98, aw * 0.92]), wrap, sh=(0.5, 0.3), line_color=MONK_WRAP_LINE)
    fist = Ell(h[0], h[1] + 0.3, 2.9, 2.7)
    cel(draw, fist, wrap, sh=(0.6, 0.6), line_color=MONK_WRAP_LINE)
    band = mix(wrap, MONK_WRAP_LINE, 0.55)
    clipped(draw, fist, lambda l: [stroke(l, [(h[0] - 3.2, h[1] + v - 0.9), (h[0] + 3.2, h[1] + v + 0.7)], 0.45, band)
                                   for v in (-0.4, 1.4)])


def _monk_beads(draw, pts, big=None):
    """The prayer beads along `pts`: big ones, one bumpy string, each with its
    own glint (`big` is the index of the guru bead)."""
    parts = [Ell(x, y, 1.9 if i == big else 1.5, 1.9 if i == big else 1.5) for i, (x, y) in enumerate(pts)]
    blob(draw, parts, MONK_BEAD, sh=(0.5, 0.6), lw=0.7)
    for i, (x, y) in enumerate(pts):
        Ell(x - 0.45, y - 0.5, 0.45, 0.45).draw(draw, fill=lit(MONK_BEAD, 2.4))


def draw_monk(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=1.06)
    d = r.d
    cx, sy, wy = r.cx, r.sh_y, r.waist_y
    m = -1 if r.back else 1        # his left is the right of the picture head-on, its left from behind
    if d:
        _monk_arm(r, draw, -d, 0.3)
    rig_legs(r, draw, MONK_ROBE, (130, 90, 60))
    rig_torso(r, draw, MONK_ROBE)
    # the red sash over his left shoulder, down across him to his right hip:
    # one band, in every facing (head-on it was a red wedge at one shoulder
    # that made a bow tie with the red bead under his chin)
    if d:
        band = Poly([(cx - d * 3.2, sy - 2.6), (cx + d * 1.4, sy - 2.6), (cx + d * 5.6, wy - 1.0), (cx + d * 5.2, wy + 1.6),
                     (cx + d * 2.0, wy + 1.6)])
    else:
        band = Poly([(cx + m * 2.0, sy - 2.6), (cx + m * 7.2, sy - 1.0), (cx - m * 6.2, wy + 1.6), (cx - m * 7.6, wy - 2.2)])
    clipped(draw, torso_shape(r), lambda l: cel(l, band, MONK_RED, sh=(0.7, 0.5), lw=0.7))
    rig_robe(r, draw, MONK_ROBE, top=wy + 0.6, hem=r.hip_y + 4.4, flare=2.0, split=True)
    rig_belt(r, draw, MONK_RED, buckle=MONK_ROBE)
    # the prayer beads: round his neck and down his chest, across the back of
    # his neck from behind
    if d:
        pts = _monk_bez((cx - d * 3.8, sy - 1.6), (cx + d * 2.0, sy - 0.2), (cx + d * 4.4, sy + 2.2), 3)
        _monk_beads(draw, pts, big=3)
    elif r.back:
        _monk_beads(draw, [(cx + 6.6 * math.cos(math.radians(a)), sy - 2.4 + 2.8 * math.sin(math.radians(a))) for a in range(8, 173, 27)])
    else:
        _monk_beads(draw, [(cx + 6.2 * math.cos(math.radians(a)), sy - 0.6 + 4.6 * math.sin(math.radians(a))) for a in range(-6, 187, 32)],
                    big=3)
    for side in _sides(r):
        _monk_arm(r, draw, side, 0.3)
    # The shaven head: a hard shine on the scalp and a full shadow side, so it
    # is a head from behind as well and not a peach disc.
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    if not d:
        for side in (-1, 1):
            cel(draw, Ell(hx + side * (rx - 0.3), hy + 2.2, 1.9, 2.5), MONK_SKIN, sh=(0.5, 0.5))
    cel(draw, Ell(hx, hy, rx, ry), MONK_SKIN, sh=(1.7, 1.5))
    Poly(arc_pts(hx, hy, rx * 0.8, ry * 0.8, 200, 258, 10) + arc_pts(hx, hy, rx * 0.66, ry * 0.64, 252, 206, 10)).draw(
        draw, fill=lit(MONK_SKIN, 1.2))
    if d:
        # the ear, a C well back from the face (the kit's ring ear sat by his closed eye as a monocle)
        ex, ey = hx - d * 6.4, hy + 2.4
        cel(draw, Ell(ex, ey, 2.0, 2.8), MONK_SKIN, sh=(0.4, 0.5))
        a0 = 90 if d > 0 else -90
        stroke(draw, arc_pts(ex + d * 0.5, ey + 0.1, 1.0, 1.6, a0, a0 + 180, 6), 0.6, shade(MONK_SKIN, 2.2))
    if r.back:
        return
    # eyes closed, in every frame: they opened on the strides, so he blinked at walking pace
    head_face(r, draw, MONK_SKIN, iris=(90, 70, 50), mood="closed", mouth="smile", brows=True, brow_color=(80, 60, 50))
    for u in ((-1.9, 0.0, 1.9) if not d else (d * 0.4, d * 2.3, d * 4.2)):
        Ell(hx + u + d * 2.0, hy - 6.6 + abs(u if not d else u - d * 2.3) * 0.2, 0.8, 0.8).draw(draw, fill=shade(MONK_SKIN, 2.0))


# ===================================================================
# CLERIC (49) -- a halo, white and gold robes, a starred staff
# ===================================================================

from sprite_base import bez  # noqa: E402  (the kit's curve; not among the file's shared imports)

CLR_ROBE = (246, 246, 252)
CLR_BLUE = (110, 170, 240)
CLR_HAIR = (248, 212, 118)
CLR_HALO = (255, 230, 120)
CLR_WOOD = (176, 120, 70)
CLR_STAR = (255, 250, 206)


def _robe_band(draw, shape, y0, color, line):
    """A hem band wide enough to read as a colour: everything of `shape` (what
    rig_robe returns) below y0, with the robe's outline put back over it. The
    kit's own `trim` is 1.6 units, which comes out as its ink line. (The
    Enchantress, the Warlock and the Inquisitor use it too.)"""
    xs = [p[0] for p in shape.pts]
    ys = [p[1] for p in shape.pts]
    band = Poly([(min(xs) - 2, y0), (max(xs) + 2, y0), (max(xs) + 2, max(ys) + 2), (min(xs) - 2, max(ys) + 2)])
    clipped(draw, shape, lambda l: cel(l, band, color, sh=None))
    shape.draw(draw, fill=None, outline=line, width=1.0)


def _clr_staff(r, draw, h, lean, top_y, ring=3.6):
    """Her staff through the hand at `h`, its foot on the ground, leaning
    `lean` units sideways for every unit it rises: a wooden shaft, and at
    `top_y` a gold ring with a star in it. It leans so that the star stands
    clear of her hair head-on and the shaft clear of her face in profile."""
    foot_y = r.base_y - 0.8

    def at(y):
        return (h[0] + (h[1] - y) * lean, y)

    cel(draw, Limb([at(foot_y), at(top_y + ring - 0.4)], [1.1, 1.0]), CLR_WOOD, sh=(0.5, 0.0))
    x, y = at(top_y)
    bx, by = at(top_y + ring + 0.6)
    cel(draw, Ell(bx, by, 1.7, 1.3), GOLD, sh=None, lw=0.7)
    cel(draw, Ell(x, y, ring + 0.9, ring + 0.9), GOLD, sh=(0.6, 0.6))
    cel(draw, Ell(x, y, ring - 0.7, ring - 0.7), CLR_BLUE, sh=None, lw=0.6)
    star(draw, x, y, ring - 0.5, CLR_STAR, line=ink(GOLD))


def draw_cleric(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=0.96)
    d = r.d
    cx = r.cx
    hs = _hold_side(r)
    hx, hy, ry = r.hx, r.head_cy, r.head_ry
    # the staff arm: out to the side head-on; reaching ahead in profile, where
    # the hand keeps still while the other arm swings
    reach, out = (0.0, 1.6) if not d else (0.35, 5.5 + r.ph * 2.7)
    h = hand_at(r, hs, reach, 0.0, out)
    rig_hair(r, draw, CLR_HAIR, "long", layer="back")
    if r.back:
        _clr_staff(r, draw, h, hs * 0.25, hy - 14.6)
    if d:
        rig_arms(r, draw, CLR_ROBE, SKIN, layer="far", cuff=GOLD)
    rig_legs(r, draw, shade(CLR_ROBE, 1.0), (200, 170, 110))
    robe = rig_robe(r, draw, CLR_ROBE, flare=3.2)
    _robe_band(draw, robe, r.base_y - 5.6, GOLD, ink(CLR_ROBE))
    # the blue stole, down her front and down her back
    sx = cx + d * 1.8
    cel(draw, Poly([(sx - 1.8, r.sh_y - 1.6), (sx + 1.8, r.sh_y - 1.6), (sx + 2.6, r.base_y - 3.2), (sx - 2.6, r.base_y - 3.2)]),
        CLR_BLUE, sh=None)
    if not r.back:
        star(draw, sx, r.sh_y + 2.0, 1.8, GOLD)
    rig_belt(r, draw, CLR_BLUE, buckle=GOLD)
    if not d:
        rig_arms(r, draw, CLR_ROBE, SKIN, layer="near", cuff=GOLD, hands=False, sides=(-hs,))
    rig_arms(r, draw, CLR_ROBE, SKIN, layer="near", cuff=GOLD, hands=False, sides=(hs,), reach=reach, out=out)
    rig_head(r, draw, SKIN, hair=CLR_HAIR, style="long", eye_color=(90, 150, 220), expression="smile")
    if r.back:
        # her hair tied low with a blue bow, and the fall of it under the bow
        by = hy + 11.4
        for k in (-1, 1):
            stroke(draw, [(hx + k * 2.4, by + 2.6), (hx + k * 3.0, by + 6.4)], 0.6, ink(CLR_HAIR))
            cel(draw, Poly([(hx, by), (hx + k * 5.0, by - 2.4), (hx + k * 5.4, by + 2.6)]), CLR_BLUE, sh=(0.0, 0.6), lw=0.7)
        cel(draw, Ell(hx, by, 1.5, 1.5), lit(CLR_BLUE, 0.8), sh=None, lw=0.6)
    # halo
    halo_y = hy - ry - 3.6 + [0, -0.6, 0, -0.6][frame]
    outer = Ell(hx - d * 0.6, halo_y, 8.0 if not d else 5.6, 2.4)
    inner = Ell(hx - d * 0.6, halo_y, 5.8 if not d else 3.8, 1.2)
    layer = draw.sub()
    cel(layer, outer, CLR_HALO, sh=None, line_color=shade(CLR_HALO, 1.8))
    inner.draw(layer, fill=(0, 0, 0, 0))
    inner.draw(layer, fill=None, outline=shade(CLR_HALO, 1.8), width=0.6)
    draw.merge(layer)
    if not r.back:
        if d:
            _clr_staff(r, draw, h, d * 0.42, hy - 8.8, ring=3.2)
        else:
            _clr_staff(r, draw, h, hs * 0.25, hy - 14.6)
    for side in _sides(r):
        held = side == hs
        rig_hand(r, draw, side, SKIN, reach=reach if held else 0.0, out=out if held else 0.0)


# ===================================================================
# ROGUE (50) -- a domino mask, a teal cowl with its hood thrown back,
# twin daggers
# ===================================================================

# He is dark, and stays dark: black hair, a dark vest and legs. What reads at
# game size is the pale face with the black mask across it, the teal of his
# cowl and cloak (in every facing), and the two blades.
ROG_CLOAK = (46, 112, 118)
ROG_LINING = (34, 76, 86)
ROG_VEST = (70, 64, 76)
ROG_LEG = (60, 56, 70)
ROG_BOOT = (80, 60, 48)
ROG_HAIR = (56, 50, 64)
ROG_MASK = (32, 30, 40)
ROG_EYE = (90, 200, 190)


def _rog_hem(cx, w, flare, sway, top, bottom, inset):
    """The cloak seen flat: shoulders to a swallowtail hem."""
    return [(cx - w + inset, top), (cx + w - inset, top), (cx + w + flare + sway, bottom - 0.8),
            (cx + w * 0.5 + 1.2 + sway, bottom + 1.2), (cx + sway * 0.5, bottom - 2.4),
            (cx - w * 0.5 - 1.2 + sway, bottom + 1.2), (cx - w - flare + sway, bottom - 0.8)]


def _rog_bag(r):
    """The hood hanging down his back, seen from behind."""
    cx, top = r.cx, r.sh_y - 5.8
    return Poly([(cx - 8.4, top), (cx + 8.4, top), (cx + 8.8, top + 2.4), (cx + 8.2, top + 5.6), (cx + 4.4, top + 10.6),
                 (cx, top + 12.8), (cx - 4.4, top + 10.6), (cx - 8.2, top + 5.6), (cx - 8.8, top + 2.4)])


def _rog_cloak(r, draw, layer):
    """A short cloak: its lining behind him head-on, streaming out behind on
    the strides in profile, over his back from behind."""
    cx, d = r.cx, r.d
    w = r.sh_w
    sway = r.ph * 1.2
    lift = abs(r.ph)
    bottom = r.hip_y + 4.6
    if layer == "under" and not r.back:
        if d:
            shape = Poly([(cx - d * 0.6, r.sh_y - 2.6), (cx - d * 4.8, r.sh_y - 1.2),
                          (cx - d * (8.8 + lift * 2.6), bottom - lift * 1.6),
                          (cx - d * (6.2 + lift * 1.6), bottom - 1.6 - lift),
                          (cx - d * (4.6 + lift), bottom + 0.8 - lift * 0.6),
                          (cx - d * 1.6, bottom - 1.2), (cx + d * 0.6, r.hip_y - 2.0)])
            cel(draw, shape, ROG_CLOAK, sh=(1.0, 0.6))
        else:
            cel(draw, Poly(_rog_hem(cx, w, 3.4, sway, r.sh_y - 2.8, bottom, 0.4)), ROG_LINING, sh=(1.2, 0.0))
    elif layer == "over" and r.back:
        cel(draw, Poly(_rog_hem(cx, w, 2.8, sway, r.sh_y - 2.8, bottom, 0.6)), ROG_CLOAK, sh=(1.8, 0.0),
            regions=[(_rog_bag(r).shift(0.5, 1.5), shade(ROG_CLOAK, 1.2))])


def _rog_cowl(r, draw):
    """The hood, thrown back: a roll of cloth behind the neck and a mantle over
    the shoulders head-on, a bag behind the neck in profile, and hanging open
    down his back from behind (where it also covers the nape)."""
    cx, d, y, w = r.cx, r.d, r.sh_y, r.sh_w
    if r.back:
        cel(draw, _rog_bag(r), ROG_CLOAK, sh=(1.4, 1.0))
        # its seam, down the middle to the point
        stroke(draw, [(cx, y - 4.4), (cx, y + 5.4)], 0.6, ink(ROG_CLOAK))
        return
    if d:
        mantle = Poly([(cx - d * 4.6, y - 3.0), (cx + d * 3.6, y - 3.0), (cx + d * 5.6, y - 0.4), (cx + d * 5.2, y + 2.2),
                       (cx + d * 1.0, y + 4.4), (cx - d * 5.4, y + 2.4)])
        bag = Poly([(cx - d * 2.0, y - 3.2), (cx - d * 7.4, y - 2.8), (cx - d * 9.8, y + 0.2), (cx - d * 9.2, y + 3.4),
                    (cx - d * 6.6, y + 4.8), (cx - d * 3.6, y + 3.0)])
        blob(draw, [bag, mantle], ROG_CLOAK, sh=(1.0, 1.0))
        stroke(draw, [(cx - d * 4.6, y - 1.6), (cx - d * 1.0, y + 1.6), (cx + d * 3.6, y - 0.6)], 0.6, ink(ROG_CLOAK))
        return
    mantle = Poly([(cx - 3.0, y - 3.2), (cx + 3.0, y - 3.2), (cx + w + 2.0, y - 1.2), (cx + w + 2.6, y + 1.6),
                   (cx + 3.8, y + 3.0), (cx, y + 5.4), (cx - 3.8, y + 3.0), (cx - w - 2.6, y + 1.6), (cx - w - 2.0, y - 1.2)])
    blob(draw, [Ell(cx, y - 2.6, 9.4, 3.6), mantle], ROG_CLOAK, sh=(1.2, 1.0),
         regions=[(Ell(r.hx, y - 2.6, 8.4, 2.9), shade(ROG_CLOAK))])
    stroke(draw, [(cx - 6.8, y - 0.8), (cx, y + 2.2), (cx + 6.8, y - 0.8)], 0.6, ink(ROG_CLOAK))


def _rog_face(r, draw):
    """The domino mask, and his eyes in it: the lids are the mask's colour."""
    hx, hy, rx, ry, d = r.hx, r.head_cy, r.head_rx, r.head_ry, r.d
    e1, e2, ey, mx, my = face_anchor(r)
    if d:
        band = Poly([(hx + d * (rx + 1.0), ey - 3.8), (hx - d * 0.6, ey - 3.6), (hx - d * 4.2, ey - 1.5),
                     (hx - d * (rx + 1.0), ey - 1.1), (hx - d * (rx + 1.0), ey + 0.7), (hx - d * 4.2, ey + 0.9),
                     (hx - d * 1.2, ey + 3.0), (hx + d * 4.6, ey + 3.5), (hx + d * (rx + 1.0), ey + 3.0)])
    else:
        band = Poly([(hx - 10.0, ey - 3.9), (hx - 2.2, ey - 4.0), (hx, ey - 3.1), (hx + 2.2, ey - 4.0), (hx + 10.0, ey - 3.9),
                     (hx + 12.6, ey - 1.0), (hx + 9.4, ey + 3.0), (hx + 3.4, ey + 3.6), (hx + 1.3, ey + 1.5), (hx, ey + 0.9),
                     (hx - 1.3, ey + 1.5), (hx - 3.4, ey + 3.6), (hx - 9.4, ey + 3.0), (hx - 12.6, ey - 1.0)])
    clipped(draw, Ell(hx, hy, rx, ry), lambda l: cel(l, band, ROG_MASK, sh=None))
    if d:
        ms_eye(draw, e2, ey, 3.0, 5.2, ROG_EYE, r.eye_look(), "sharp", None, ROG_MASK, d, ROG_MASK)
        ms_eye(draw, e1, ey, 4.2, 5.6, ROG_EYE, r.eye_look(), "sharp", None, ROG_MASK, -d, ROG_MASK)
        ms_mouth(draw, mx, my, "smirk", SKIN, 0.8)
    else:
        for side, ex in ((-1, e1), (1, e2)):
            ms_eye(draw, ex, ey, 4.2, 5.6, ROG_EYE, (0.0, 0.0), "sharp", None, ROG_MASK, side, ROG_MASK)
        ms_mouth(draw, mx, my, "smirk", SKIN)


def _rog_tails(r, draw, frame):
    """The mask's ties: a knot at the back of his head and two tails that
    flutter with the stride."""
    hx, hy, rx, d = r.hx, r.head_cy, r.head_rx, r.d
    wave = [0.0, 1.0, 0.0, -1.0][frame]
    if r.back:
        cel(draw, Limb([(hx - 0.5, hy + 2.6), (hx - 1.2 + wave * 0.4, hy + 5.6), (hx - 0.6 + wave * 0.8, hy + 7.8)],
                       [1.2, 1.1, 0.4]), ROG_CLOAK, sh=None, lw=0.7)
        cel(draw, Limb([(hx + 0.5, hy + 2.6), (hx + 2.4 + wave * 0.5, hy + 5.8), (hx + 4.4 + wave, hy + 8.6)],
                       [1.2, 1.1, 0.4]), ROG_CLOAK, sh=None, lw=0.7)
        cel(draw, Ell(hx, hy + 2.2, 2.0, 1.5), ROG_CLOAK, sh=(0.4, 0.4), lw=0.7)
        return
    k = -d if d else 1
    x0, y0 = hx + k * (rx + (0.4 if d else -1.0)), hy + 1.6
    cel(draw, Limb([(x0, y0), (x0 + k * 3.6, y0 + 1.4 + wave), (x0 + k * 6.6, y0 + 0.2 - wave)], [1.2, 1.1, 0.4]),
        ROG_CLOAK, sh=None, lw=0.7)
    cel(draw, Limb([(x0, y0 + 0.8), (x0 + k * 3.0, y0 + 3.8 - wave * 0.6), (x0 + k * 4.8, y0 + 5.8 + wave)], [1.1, 1.0, 0.4]),
        ROG_CLOAK, sh=None, lw=0.7)


def _rog_dagger(r, draw, side, reach, out):
    """A dagger in one fist, the hand closed round its grip."""
    d = r.d
    h = hand_at(r, side, reach, 0.0, out)
    ang = -90 + d * 62 if d else -90 + side * 32
    a = math.radians(ang)
    steel = STEEL if r.near(side) else shade(STEEL, 0.8)
    blade(draw, h[0] + math.cos(a) * 1.8, h[1] + math.sin(a) * 1.8, ang, length=8.6, width=1.6, guard=2.2,
          flip=d if d else 1.0, color=steel, hilt=(204, 206, 218), grip=shade(LEATHER, 1.4))
    rig_hand(r, draw, side, SKIN, reach=reach, out=out)


def draw_rogue(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=0.96)
    d = r.d
    cx = r.cx
    # he prowls with both blades forward in profile, and carries them wide head-on
    reach, out = (0.22, 0.0) if d else (0.0, 0.9)
    _rog_cloak(r, draw, "under")
    if r.front:
        _rog_tails(r, draw, frame)
    if d:
        rig_arms(r, draw, ROG_VEST, SKIN, layer="far", cuff=LEATHER, reach=reach, hands=False)
        _rog_dagger(r, draw, -d, reach, out)
    rig_legs(r, draw, ROG_LEG, ROG_BOOT)
    rig_torso(r, draw, ROG_VEST)
    rig_belt(r, draw, shade(LEATHER, 1.6), buckle=(214, 216, 226))
    if not r.back:
        px = cx + (3.6 if not d else d * 1.6)
        cel(draw, RRect(px - 1.7, r.waist_y + 2.4, px + 1.7, r.waist_y + 5.4, 0.7), LEATHER, sh=(0.4, 0.4))
    rig_arms(r, draw, ROG_VEST, SKIN, layer="near", hands=False, cuff=LEATHER, reach=reach, out=out)
    if not r.back:
        _rog_cowl(r, draw)
    # no ear in profile: the kit's is a ring, and it lay on the mask's end
    head_skull(r, draw, SKIN, ears=not d)
    if not r.back:
        _rog_face(r, draw)
    rig_hair(r, draw, ROG_HAIR, "wild", skin=SKIN)
    if r.back:
        _rog_cloak(r, draw, "over")
        _rog_cowl(r, draw)
    if not r.front:
        _rog_tails(r, draw, frame)
    for side in _sides(r):
        _rog_dagger(r, draw, side, reach, out)


# ===================================================================
# BARBARIAN (51) -- horned helmet, braided blond beard, a great axe
# carried over his shoulder, a fur pelt down his back
# ===================================================================

BAR_IRON = (152, 158, 174)
BAR_HAIR = (242, 202, 110)
BAR_FUR = (126, 86, 58)
BAR_HORN = (240, 230, 206)
BAR_TUNIC = (170, 140, 104)
BAR_BLADE = (184, 192, 210)
BAR_EDGE = (236, 240, 248)


def _bar_horn(draw, x, y, s, near=True, side_on=False):
    """A helmet horn from its iron socket. Head-on it goes out to side `s`
    and turns up; from the side it is on the side of the helm, a crescent
    bellying back (`s`) with its tip come forward again."""
    if side_on:
        path = bez((x, y), (x + s * 5.0, y - 4.2), (x - s * 0.8, y - 12.8), 9)
    else:
        path = bez((x, y), (x + s * 6.6, y + 0.8), (x + s * 6.8, y - 6.6), 6) + \
            bez((x + s * 6.8, y - 6.6), (x + s * 6.9, y - 10.2), (x + s * 5.0, y - 12.6), 4)[1:]
    n = len(path) - 1
    widths = [3.3 * (1.0 - i / float(n)) ** 0.75 + 0.35 for i in range(n + 1)]
    cel(draw, Limb(path, widths), BAR_HORN if near else shade(BAR_HORN, 0.9), sh=(0.8, 0.6))
    cel(draw, Limb(path[:2], [widths[0] + 0.4, widths[1] + 0.4], cap=False), shade(BAR_IRON, 1.3), sh=None)


def _bar_axe(draw, x, y, ang, length, flip=1.0):
    """His great axe, gripped at (x, y): the haft along `ang`, and one broad
    crescent bit on the `flip` side of it, its edge ground bright."""
    L = length
    cel(draw, Limb(xform([(-3.4, 0.0), (L + 1.2, 0.0)], x, y, ang), [1.25, 1.15]), WOOD, sh=None)
    edge = bez((L - 7.8, 5.8), (L - 2.6, 10.4), (L + 2.8, 5.8), 8)
    body = bez((L - 4.4, 0.6), (L - 5.0, 4.2), (L - 7.8, 5.8), 4) + edge[1:] + bez((L + 2.8, 5.8), (L + 0.2, 4.2), (L - 0.4, 0.6), 4)[1:]
    bevel = edge + bez((L + 2.0, 5.0), (L - 2.6, 7.6), (L - 7.0, 5.0), 8)
    cel(draw, Poly(xform(body, x, y, ang, 1.0, flip)), BAR_BLADE, sh=None,
        regions=[(Poly(xform(bevel, x, y, ang, 1.0, flip)), BAR_EDGE)])
    cel(draw, Poly(xform([(L - 4.9, -1.6), (L + 0.1, -1.6), (L + 0.1, 1.6), (L - 4.9, 1.6)], x, y, ang, 1.0, flip)),
        shade(BAR_IRON, 1.0), sh=None)


def _bar_pelt(r, draw):
    """The fur: a mantle over the shoulders head-on, and from the side and
    from behind the whole pelt hanging down his back."""
    cx, d, y = r.cx, r.d, r.sh_y
    sway = r.ph * 0.8
    if r.back:
        parts = [Ell(cx - 6.6, y - 1.2, 5.2, 3.4), Ell(cx, y - 0.6, 5.6, 3.2), Ell(cx + 6.6, y - 1.2, 5.2, 3.4),
                 Poly([(cx - 9.2, y - 1.0), (cx + 9.2, y - 1.0), (cx + 9.6 + sway, y + 10.0), (cx - 9.6 + sway, y + 10.0)])]
        parts += [Ell(cx + u + sway, y + 10.2 + (0.9 if i % 2 else 0.0), 2.7, 2.3) for i, u in enumerate((-7.4, -3.7, 0.0, 3.7, 7.4))]
    elif d:
        parts = [Ell(cx, y - 1.2, 7.0, 3.2),
                 Poly([(cx - d * 0.6, y - 2.6), (cx - d * 6.0, y - 1.6), (cx - d * (7.8 + abs(sway)), y + 6.0),
                       (cx - d * (7.6 + abs(sway) * 1.6), y + 10.6), (cx - d * 3.4, y + 10.2), (cx - d * 2.4, y + 1.0)])]
        parts += [Ell(cx - d * (u + abs(sway) * 1.4), y + 10.8, 2.3, 2.0) for u in (3.6, 6.6)]
    else:
        parts = [Ell(cx - 6.4, y - 1.4, 5.0, 3.2), Ell(cx, y - 0.8, 5.4, 3.0), Ell(cx + 6.4, y - 1.4, 5.0, 3.2)]
    blob(draw, parts, BAR_FUR, sh=(0.8, 1.0))


def draw_barbarian(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=1.32, head=0.96)
    d = r.d
    cx = r.cx
    hs = _hold_side(r)
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    # the axe arm: the fist in front of the hip, the haft up over that shoulder
    reach, out = (0.2, 2.6) if not d else (0.35, 1.2 + r.ph * 2.2)
    if d:
        rig_arms(r, draw, SKIN_TAN, SKIN_TAN, layer="far")
    rig_legs(r, draw, (120, 90, 70), (100, 72, 52), width=1.1)
    rig_torso(r, draw, BAR_TUNIC)
    rig_belt(r, draw, (90, 64, 46), buckle=BAR_IRON)
    if not d:
        rig_arms(r, draw, SKIN_TAN, SKIN_TAN, layer="near", hands=False, sides=(-hs,))
    rig_arms(r, draw, SKIN_TAN, SKIN_TAN, layer="near", hands=False, sides=(hs,), reach=reach, out=out)
    _bar_pelt(r, draw)
    head_skull(r, draw, SKIN_TAN, ears=False)
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
        stroke(draw, [(mcx - 3.6, hy + 6.6), (mcx, hy + 5.8), (mcx + 3.6, hy + 6.6)], 1.3, shade(BAR_HAIR, 1.3))
    # hair that lies close, so the helm covers it
    rig_hair(r, draw, BAR_HAIR, dict(HAIR_STYLES["long"], vol=1.0, side=8.0, back=12.0), hat=True, skin=SKIN_TAN)
    # the horned helmet: an iron dome with a riveted band and a nasal
    kx = hx - d * 0.4
    if d:
        _bar_horn(draw, kx + d * 4.4, hy - 7.0, -d, near=False, side_on=True)
    dome = arc_pts(kx, hy - 1.0, rx + 2.0, ry + 1.8, 182, 358, 26)
    cel(draw, Poly(dome + [(kx + rx + 2.1, hy - 2.0), (kx - rx - 2.1, hy - 2.0)]), BAR_IRON, sh=(1.4, 1.0), hi=(0.8, 0.8))
    cel(draw, RRect(kx - rx - 2.3, hy - 4.6, kx + rx + 2.3, hy - 1.8, 0.8), shade(BAR_IRON, 1.5), sh=None)
    for u in ((-8.0, 0.0, 8.0) if not d else (-7.0, 0.0, 7.0)):
        if r.back or abs(u - d * 3.0) > 2.0:
            Ell(kx + u, hy - 3.2, 0.8, 0.8).draw(draw, fill=lit(BAR_IRON, 1.6))
    if not r.back:
        cel(draw, RRect(hx + d * 3.0 - 1.0, hy - 4.6, hx + d * 3.0 + 1.0, hy + 3.0, 0.6), BAR_IRON, sh=(0.5, 0.0))
    if d:
        _bar_horn(draw, kx - d * 2.0, hy - 5.6, -d, side_on=True)
    else:
        for s in (-1, 1):
            _bar_horn(draw, kx + s * (rx + 1.2), hy - 5.4, s)
    # the great axe, over his shoulder
    h = hand_at(r, hs, reach, 0.0, out)
    if d:
        _bar_axe(draw, h[0], h[1], -90 - d * 30, 20.0, flip=-d)
    else:
        _bar_axe(draw, h[0], h[1], -90 + hs * 8, 16.0, flip=hs)
    for side in _sides(r):
        held = side == hs
        rig_hand(r, draw, side, SKIN_TAN, reach=reach if held else 0.0, out=out if held else 0.0)


# ===================================================================
# ENCHANTRESS (52) -- violet twin tails, a jewelled tiara, a heart wand
# ===================================================================

from sprite_base import bez as _enc_bez  # this block's own: the file's import list is shared

# Her hair is a deep violet and her gown a lighter, pinker orchid: at one
# purple they were a single bell with a face in it from every side.
ENC_GOWN = (176, 102, 218)
ENC_PINK = (255, 132, 194)
ENC_HAIR = (84, 52, 146)
ENC_ROD = (250, 212, 104)
# On her head a fringe and a bob to the jaw; the length of her hair is in the
# two tails, which hang clear of her body. (The kit's long hair behind her
# joined them, her head and her gown into one cloak, and in profile was a
# slab behind her head.)
ENC_STYLE = dict(HAIR_STYLES["long"], side=8.6, back=8.6)


def _enc_tail(draw, x, y, s, sway=0.0, far=False, length=15.0, out=6.2):
    """One of her twin tails from where it is tied at (x, y): out to side `s`
    as it leaves the head and then falling (hair that rises reads as a horn),
    fullest a third of the way down, curling in at its tip."""
    col = ENC_HAIR if not far else shade(ENC_HAIR, 0.9)
    path = _enc_bez((x, y), (x + s * out, y + length * 0.3), (x + s * 2.4 + sway, y + length), 8)
    cel(draw, Limb(path, [1.9, 2.7, 3.1, 3.3, 3.2, 2.9, 2.4, 1.6, 0.6]), col, sh=(0.9, 0.7))


def _enc_bow(draw, x, y, k=1.0, tails=0.0, sway=0.0):
    """A pink ribbon bow knotted at (x, y); `tails` is how far its ends hang."""
    if tails:
        for s in (-1, 1):
            cel(draw, Limb([(x + s * 0.6, y + 0.4), (x + s * 1.8 + sway * 0.5, y + tails * 0.6), (x + s * 2.6 + sway, y + tails)],
                           [1.2, 1.2, 0.7]), shade(ENC_PINK, 0.7), sh=None, lw=0.7)
    for s in (-1, 1):
        cel(draw, Poly([(x, y), (x + s * 3.4 * k, y - 2.3 * k), (x + s * 3.7 * k, y + 2.3 * k)]), ENC_PINK, sh=(0.0, 0.6), lw=0.7)
    cel(draw, Ell(x, y, 1.1 * k, 1.1 * k), lit(ENC_PINK, 0.8), sh=None, lw=0.6)


def _enc_tiara(draw, x, top, wb, wf, s=1):
    """The tiara across her fringe, centred on (x, top): a gold band with
    three points, a pink stone under the tallest. `wb` and `wf` are its two
    half-widths (equal head-on; in profile the half nearer the camera is the
    longer, running back toward `-s`)."""
    def at(u, v):
        return (x + s * u * (wf if u > 0 else wb), top + v)
    low = _enc_bez(at(-1, 4.8), at(0, 2.0), at(1, 4.8), 8)
    crest = [at(1, 3.0), at(0.62, -0.4), at(0.36, 1.5), at(0, -3.4), at(-0.36, 1.5), at(-0.62, -0.4), at(-1, 3.0)]
    cel(draw, Poly(low + crest), GOLD, sh=(0.5, 0.5), lw=0.7)
    gem(draw, x, top + 0.5, 1.5, ENC_PINK)


def _enc_wand(draw, h, tip, frame, s=1):
    """The heart wand through the hand at `h`: a gold rod to `tip`, and on it
    a heart as big as her hand can carry, a twinkle beside it."""
    dx, dy = tip[0] - h[0], tip[1] - h[1]
    ln = math.hypot(dx, dy)
    cel(draw, Limb([(h[0] - dx / ln * 2.4, h[1] - dy / ln * 2.4), tip], [0.95, 0.85], cap0=True), ENC_ROD, sh=None, lw=0.6)
    x, y, k = tip[0], tip[1] - 1.8, 1.55
    blob(draw, [Ell(x - 1.25 * k, y - 0.9 * k, 1.45 * k, 1.4 * k), Ell(x + 1.25 * k, y - 0.9 * k, 1.45 * k, 1.4 * k),
                Poly([(x - 2.5 * k, y - 0.2 * k), (x + 2.5 * k, y - 0.2 * k), (x, y + 2.6 * k)])], ENC_PINK, sh=(0.8, 0.8), lw=0.8)
    Ell(x - 1.9, y - 1.7, 1.0, 0.8).draw(draw, fill=(255, 255, 255))
    big = frame % 2 == 0
    sparkle(draw, x + s * (2.8 if big else 4.0), y - (6.4 if big else 4.4), 2.4 if big else 1.5, (255, 246, 196))


def draw_enchantress(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=0.92)
    d = r.d
    cx = r.cx
    hs = _hold_side(r)
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    sway = r.ph * 1.2
    # The wand arm: out to her side head-on and from behind, so the heart
    # stands clear of her hair; in profile ahead of her and low, the heart
    # under her chin and not on her mouth, and the hand keeps still while the
    # other arm swings.
    reach, out = (0.3, 3.0) if not d else (0.35, 4.6 + r.ph * 2.8)
    h = hand_at(r, hs, reach, 0.0, out)
    tip = (h[0] + hs * 9.0, h[1] - 5.6) if not d else (h[0] + d * 8.2, h[1] - 8.4)
    tie = hy - 6.4
    if d:
        rig_arms(r, draw, ENC_GOWN, SKIN, layer="far", cuff=ENC_PINK)
    if r.back:
        _enc_wand(draw, h, tip, frame, s=hs)
    rig_legs(r, draw, shade(ENC_GOWN, 1.0), ENC_PINK)
    robe = rig_robe(r, draw, ENC_GOWN, flare=4.0)
    _robe_band(draw, robe, r.base_y - 5.6, ENC_PINK, ink(ENC_GOWN))
    # a pink sash, its bow at her waist; tied behind, where it is the big one, its ends hanging
    rig_belt(r, draw, ENC_PINK, buckle=ENC_PINK, y=r.waist_y + 0.4)
    if r.back:
        _enc_bow(draw, cx, r.waist_y + 1.4, 1.5, tails=6.4, sway=sway)
    else:
        _enc_bow(draw, cx + d * 1.8, r.waist_y + 1.6)
    if not d:
        rig_arms(r, draw, ENC_GOWN, SKIN, layer="near", cuff=ENC_PINK, hands=False, sides=(-hs,), reach=0.3)
    rig_arms(r, draw, ENC_GOWN, SKIN, layer="near", cuff=ENC_PINK, hands=False, sides=(hs,), reach=reach, out=out)
    rig_head(r, draw, SKIN, hair=ENC_HAIR, style=ENC_STYLE, eye_color=(200, 80, 170), expression="smile")
    top = hy - ry
    if d:
        # in profile the one tail, tied at the back of her head and hanging
        # clear behind it, trailing as she strides
        _enc_tiara(draw, hx + d * 5.4, top, 5.4, 3.8, s=d)
        _enc_tail(draw, hx - d * (rx + 1.2), hy - 3.2, -d, sway=-d * abs(sway) * 1.3, length=15.0, out=7.0)
        _enc_bow(draw, hx - d * (rx + 1.4), hy - 3.0, 0.8)
    else:
        if not r.back:
            _enc_tiara(draw, hx, top, 6.8, 6.8)
        else:
            # her parting, down the back of her head
            stroke(draw, [(hx, top - 1.0), (hx, hy + 6.4)], 0.8, lit(ENC_HAIR, 1.0))
        for s in (-1, 1):
            _enc_tail(draw, hx + s * (rx + 0.2), tie, s, sway=sway)
            _enc_bow(draw, hx + s * (rx + 0.6), tie + 0.2, 0.8)
    if not r.back:
        _enc_wand(draw, h, tip, frame, s=hs)
    for side in _sides(r):
        held = side == hs
        rig_hand(r, draw, side, SKIN, reach=reach if held else 0.3, out=out if held else 0.0)


# ===================================================================
# JESTER (53) -- a floppy three-pointed cap with a bell on every point,
# a wink and a grin, a pointed collar, red and purple motley, curly
# shoes, and a fan of playing cards
# ===================================================================

JES_RED = (228, 52, 76)
JES_PURPLE = (128, 78, 200)
JES_GOLD = (252, 206, 72)
JES_HAIR = (156, 92, 56)
CARD = (252, 252, 250)


def _bell(draw, x, y, k=1.0):
    cel(draw, Ell(x, y, 1.8 * k, 1.7 * k), JES_GOLD, sh=(0.4, 0.4), hi=(0.3, 0.3), lw=0.7)
    stroke(draw, [(x - 0.9 * k, y + 0.5 * k), (x + 0.9 * k, y + 0.5 * k)], 0.4, shade(JES_GOLD, 2.2))


def _cap(r, draw, frame):
    """The cap and bells: a snug two-tone cap down to a gold band at the brow,
    with three fat floppy points, a bell swinging from each."""
    hx, hy, rx, ry, d = r.hx, r.head_cy, r.head_rx, r.head_ry, r.d
    wob = [0.0, 1.0, 0.0, -1.0][frame]
    band_y = hy - 2.6
    near_col, far_col = (JES_RED, JES_PURPLE) if d > 0 else (JES_PURPLE, JES_RED)
    # The gold point stands up off the crown and flops over at its tip (it was
    # drawn for a frame with no room over the head, and lay flat along the cap).
    if d:
        # a point trailing behind and one standing up and flopping back
        k = -d
        points = [
            ([(hx + k * 4.0, hy - 6.6), (hx + k * 11.0, hy - 9.6 + wob * 0.6), (hx + k * 16.0, hy - 6.6 + wob), (hx + k * 17.2, hy - 1.6 + wob)],
             far_col),
            ([(hx - k * 1.0, hy - 9.6), (hx - k * 0.6, hy - 15.6), (hx + k * 3.0, hy - 19.8 - wob * 0.4), (hx + k * 8.4, hy - 18.6 + wob * 0.4)],
             JES_GOLD),
        ]
    else:
        points = [
            ([(hx - 5.4, hy - 7.4), (hx - 11.6, hy - 11.6 - wob * 0.6), (hx - 17.0, hy - 9.4 - wob), (hx - 18.4, hy - 3.6 - wob)], JES_RED),
            ([(hx + 5.4, hy - 7.4), (hx + 11.6, hy - 11.6 + wob * 0.6), (hx + 17.0, hy - 9.4 + wob), (hx + 18.4, hy - 3.6 + wob)], JES_PURPLE),
            ([(hx - 0.2, hy - 9.4), (hx + 1.0, hy - 15.6), (hx - 1.6, hy - 20.0), (hx - 6.8, hy - 19.4 + wob * 0.5)], JES_GOLD),
        ]
        if r.back:
            points = [(pts, JES_PURPLE if col == JES_RED else JES_RED if col == JES_PURPLE else col) for (pts, col) in points]
    for pts, col in points:
        cel(draw, Limb(pts, [4.0, 3.4, 2.2, 0.6]), col, sh=(0.9, 0.9))
    cap = arc_pts(hx - d * 0.4, hy - 1.0, rx + 1.2, ry + 0.6, 180, 360, 26)
    shape = Poly(cap + [(hx + rx + 1.2, band_y), (hx - rx - 1.2, band_y)])
    if d:
        cel(draw, shape, near_col, sh=(1.2, 0.8))
    else:
        left, right = (JES_RED, JES_PURPLE) if not r.back else (JES_PURPLE, JES_RED)
        cel(draw, shape, left, sh=(1.2, 0.8), regions=[(Poly([(hx, hy - 30), (hx + 30, hy - 30), (hx + 30, hy), (hx, hy)]), right)])
    # the gold band, with diamonds on it
    band = RRect(hx - rx - 1.2, band_y - 1.6, hx + rx + 1.2, band_y + 1.0, 1.0)
    cel(draw, band, JES_GOLD, sh=(0.0, 0.5))
    if not r.back:
        for u in ((-6.0, 0.0, 6.0) if not d else (d * 2.0, d * 7.6)):
            x = hx + u
            cel(draw, Poly([(x, band_y - 1.5), (x + 1.0, band_y - 0.3), (x, band_y + 0.9), (x - 1.0, band_y - 0.3)]),
                JES_RED if abs(u) < 1 or u * (d or 1) > 4 else JES_PURPLE, sh=None, lw=0.4)
    for pts, col in points:
        _bell(draw, pts[-1][0], pts[-1][1] + 1.6)


def _shoe(r, draw, side, color, near):
    fx, fy = r.foot(side)
    col = color if near else shade(color, 0.5)
    t = side if not r.d else r.d
    toe = (fx + t * 4.8, fy - 2.6)
    shape = Poly([(fx - t * 2.4, fy + 1.3), (fx - t * 2.6, fy - 0.6), (fx - t * 1.2, fy - 2.0), (fx + t * 1.4, fy - 1.6),
                  (fx + t * 3.6, fy - 1.0), toe, (fx + t * 4.4, fy - 0.2), (fx + t * 2.6, fy + 1.3)])
    cel(draw, shape, col, sh=(0.0, 0.7))
    if near:
        cel(draw, Ell(toe[0], toe[1] - 0.6, 1.1, 1.0), JES_GOLD, sh=None, lw=0.5)


def _collar(r, draw):
    """Five floppy points round the neck, red and purple by turns, a gold
    bobble on each."""
    cx, d = r.cx, r.d
    y = r.sh_y - 1.4
    if d:
        angles = (40, 90, 140)
        ccx = cx + d * 1.2
        sx = 0.7
    else:
        angles = (8, 48, 90, 132, 172)
        ccx = cx
        sx = 1.0
    for i, a in enumerate(angles):
        ra = math.radians(a)
        tip = (ccx + math.cos(ra) * 8.4 * sx, y + math.sin(ra) * 4.6 + 1.0)
        l = (ccx + math.cos(ra - 0.42) * 3.6 * sx, y + math.sin(ra - 0.42) * 2.0)
        rr = (ccx + math.cos(ra + 0.42) * 3.6 * sx, y + math.sin(ra + 0.42) * 2.0)
        col = (JES_RED, JES_PURPLE)[i % 2] if not r.back else (JES_PURPLE, JES_RED)[i % 2]
        cel(draw, Poly([l, tip, rr, (ccx, y - 0.6)]), col, sh=(0.0, 0.5), lw=0.7)
        cel(draw, Ell(tip[0], tip[1] + 0.4, 0.9, 0.9), JES_GOLD, sh=None, lw=0.45)


def _cards(draw, h, d, backs=False):
    """A fan of three playing cards, big enough to read. `backs` is the fan
    seen from behind him: the cards' red backs, leaning out past his arm."""
    spread = (-34, -6, 22) if not d else (-28, 0, 28)
    if backs:
        spread = (-46, -20, 6)
    base_ang = -90 + (0 if not d else d * 26)
    for j, a in enumerate(spread):
        ang = base_ang + a
        c = xform([(4.6, 0.0)], h[0], h[1] - 0.6, ang)[0]
        card = Poly(xform([(-2.4, -1.7), (2.4, -1.7), (2.4, 1.7), (-2.4, 1.7)], c[0], c[1], ang + 90))
        cel(draw, card, CARD, sh=None, lw=0.6)
        if backs:
            Poly(xform([(-1.6, -0.95), (1.6, -0.95), (1.6, 0.95), (-1.6, 0.95)], c[0], c[1], ang + 90)).draw(draw, fill=JES_RED)
            continue
        pip = JES_RED if j != 1 else (52, 44, 70)
        px, py = c
        if j == 1:
            Ell(px, py - 0.3, 0.9, 0.9).draw(draw, fill=pip)
            Poly([(px - 0.4, py + 0.2), (px + 0.4, py + 0.2), (px, py + 1.3)]).draw(draw, fill=pip)
        else:
            Poly([(px, py - 1.2), (px + 0.85, py), (px, py + 1.2), (px - 0.85, py)]).draw(draw, fill=pip)


def draw_jester(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=0.94, head=0.98)
    d = r.d
    hs = _hold_side(r)
    cx = r.cx
    # From behind he still has his cards: the fan held out past his hip, its
    # red backs to the camera, his arm coming down in front of it.
    back_out = 2.4
    if r.back:
        _cards(draw, hand_at(r, hs, 0.3, 0.0, back_out), 0, backs=True)
    if d:
        rig_arms(r, draw, JES_PURPLE, (250, 250, 250), layer="far", cuff=JES_GOLD)
    # motley, which keeps to its side of him as he turns: his right half red
    # and his left purple, each leg the other colour from the half above it
    near_col = JES_RED if d > 0 else JES_PURPLE
    for side in ((-1, 1) if not d else (-d, d)):
        if d:
            col = (JES_PURPLE if d > 0 else JES_RED) if side == d else (JES_RED if d > 0 else JES_PURPLE)
        else:
            col = (JES_PURPLE if side < 0 else JES_RED) if not r.back else (JES_RED if side < 0 else JES_PURPLE)
        rig_legs(r, draw, col, col, sides=(side,), width=0.9, bare=True)
        _shoe(r, draw, side, col, r.near(side))
    left, right = (JES_RED, JES_PURPLE) if not r.back else (JES_PURPLE, JES_RED)
    shape = torso_shape(r, None, r.hip_y + 1.6)
    if d:
        cel(draw, shape, near_col, sh=(1.5, 0.9), regions=[(Ell(r.hx, shape.pts[0][1] + 0.4, r.head_rx * 0.62, 2.3), shade(near_col))])
    else:
        cel(draw, shape, left, sh=(1.5, 0.9),
            regions=[(Poly([(cx, 0), (cx + 30, 0), (cx + 30, 64), (cx, 64)]), right),
                     (Ell(r.hx, shape.pts[0][1] + 0.4, r.head_rx * 0.62, 2.3), shade(left))])
    # a zigzag hem, and gold buttons down the front
    hem_y = r.hip_y + 1.6
    if not d:
        for i in range(5):
            x = cx - r.hip_w + (2 * r.hip_w) * (i + 0.5) / 5.0
            cel(draw, Poly([(x - 1.4, hem_y - 0.6), (x + 1.4, hem_y - 0.6), (x, hem_y + 1.6)]),
                (left if x < cx else right) if i != 2 else JES_GOLD, sh=None, lw=0.5)
    if r.front:
        for y in (r.sh_y + 2.6, r.sh_y + 5.6):
            cel(draw, Ell(cx, y, 0.9, 0.9), JES_GOLD, sh=None, lw=0.45)
    rig_belt(r, draw, JES_GOLD, buckle=JES_RED, y=r.waist_y + 0.6)
    if r.back:
        rig_arms(r, draw, JES_PURPLE, (250, 250, 250), layer="near", hands=False, cuff=JES_GOLD, sides=(-hs,))
        rig_arms(r, draw, JES_PURPLE, (250, 250, 250), layer="near", hands=False, cuff=JES_GOLD, sides=(hs,), reach=0.3,
                 out=back_out)
    else:
        rig_arms(r, draw, JES_PURPLE, (250, 250, 250), layer="near", hands=False, cuff=JES_GOLD)
    _collar(r, draw)
    # no ear in profile: beside the wink it reads as a monocle
    head_skull(r, draw, SKIN, ears=not d)
    hx, hy = r.hx, r.head_cy
    if not r.back:
        e1, e2, ey, mx, my = face_anchor(r)
        k = r.head_rx / 12.4
        # a wink: the eye nearer the cards stays open
        if d:
            ms_eye(draw, e2, ey, 3.0 * k, 5.2 * k, (110, 60, 150), r.eye_look(), "bright", skin=SKIN, side=d)
            ms_eye(draw, e1, ey, 4.2 * k, 5.6 * k, (110, 60, 150), r.eye_look(), "closed", skin=SKIN, side=-d)
            Ell(e1 - d * 1.6, ey + 3.4, 1.8, 0.8).draw(draw, fill=mix(SKIN, (255, 128, 128), 0.4))
            ms_mouth(draw, mx, my, "grin", SKIN, 0.8)
        else:
            ms_eye(draw, e1, ey, 4.2 * k, 5.6 * k, (110, 60, 150), (0.0, 0.0), "closed", skin=SKIN, side=-1)
            ms_eye(draw, e2, ey, 4.2 * k, 5.6 * k, (110, 60, 150), (0.0, 0.0), "bright", skin=SKIN, side=1)
            for s in (-1, 1):
                Ell(hx + s * 7.4 * k, ey + 3.4, 1.9, 0.85).draw(draw, fill=mix(SKIN, (255, 128, 128), 0.4))
            ms_brow(draw, e1, ey - 4.2, 3.4, mix(LASH, SKIN, 0.25), -0.4, -1)
            ms_brow(draw, e2, ey - 4.6, 3.4, mix(LASH, SKIN, 0.25), 0.0, 1)
            ms_mouth(draw, mx, my, "grin", SKIN)
            # a painted diamond under the open eye
            cel(draw, Poly([(e2, ey + 3.6), (e2 + 0.9, ey + 4.9), (e2, ey + 6.2), (e2 - 0.9, ey + 4.9)]), JES_PURPLE, sh=None, line=False)
        # curls of hair out from under the cap
        if d:
            blob(draw, [Ell(hx - d * 7.8, hy - 1.0, 3.8, 2.6), Ell(hx - d * 9.6, hy + 1.6, 2.2, 2.2), Ell(hx - d * 5.6, hy + 0.2, 1.8, 2.0)],
                 JES_HAIR, sh=(0.4, 0.4))
        else:
            for s in (-1, 1):
                bx = hx + s * (r.head_rx - 0.6)
                blob(draw, [Ell(bx, hy - 0.6, 2.0, 2.4), Ell(bx + s * 1.0, hy + 1.6, 1.4, 1.8)], JES_HAIR, sh=(0.4, 0.4))
    else:
        # From behind his hair covers the back of his head down to the nape, in
        # curls, the same curls out past his ears as from the front. (It used
        # to stop at a wavy line across the middle of the skull, and with an
        # ear either side the bare half under it was a face in a blindfold.)
        rx_, ry_ = r.head_rx, r.head_ry
        clipped(draw, Ell(hx, hy, rx_ - 0.5, ry_ - 0.5),
                lambda l: Poly([(hx - rx_, hy - 3.0), (hx + rx_, hy - 3.0), (hx + rx_, hy + ry_ + 1), (hx - rx_, hy + ry_ + 1)]).draw(
                    l, fill=shade(JES_HAIR)))
        curls = [Ell(hx, hy + 1.4, rx_ + 0.2, 6.6)]
        for s in (-1, 1):
            bx = hx + s * (rx_ - 0.6)
            curls += [Ell(bx, hy - 0.6, 2.0, 2.4), Ell(bx + s * 1.0, hy + 1.6, 1.4, 1.8)]
        for u in (-8.8, -4.4, 0.0, 4.4, 8.8):
            curls.append(Ell(hx + u, hy + 8.2 - (u / 8.8) ** 2 * 3.6, 2.7, 2.4))
        blob(draw, curls, JES_HAIR, sh=(0.8, 0.9))
    _cap(r, draw, frame)
    for side in _sides(r):
        held = side == hs
        h = rig_hand(r, draw, side, (250, 250, 250), reach=0.3 if held else 0.0, out=back_out if held and r.back else 0.0)
        if held and not r.back:
            _cards(draw, h, d)


# ===================================================================
# VALKYRIE (54) -- winged helm, blond braid, feather wings, a spear
# ===================================================================

# Steel a clear step darker than the feathers: the engine flattens the light
# end, and white wings on near-white armour were one pale shape.
VAL_STEEL = (164, 178, 206)
VAL_BLUE = (72, 122, 212)
VAL_HAIR = (250, 214, 120)
VAL_WING = (250, 250, 255)
VAL_POINT = (228, 234, 246)
VAL_BRAID = (240, 184, 84)


def _val_helm_wing(draw, x, y, s, k=1.0, lean=0.0):
    """One wing of the helm: feathers standing up from its side and fanning
    out to `s`, leaning that way by `lean` degrees."""
    pts = [(0.0, -2.0), (0.4, 4.0), (1.6, 12.6), (3.2, 7.8), (5.2, 10.8), (5.6, 5.8), (8.0, 7.6), (7.0, 2.8), (5.0, -0.6),
           (2.4, -2.4)]
    c, sn = math.cos(math.radians(lean)), math.sin(math.radians(lean))
    cel(draw, Poly([(x + s * (u * c + v * sn) * k, y - (v * c - u * sn) * k) for (u, v) in pts]), VAL_WING, sh=(0.8, 0.8))


def _val_braid(draw, x0, y0, x1, y1, sway=0.0):
    """Her braid from (x0, y0) down to (x1, y1): the plaits as one bumpy mass a
    shade deeper than her loose hair, a blue bow on its end."""
    n = 6
    parts, mids = [], []
    for i in range(n):
        t = i / float(n - 1)
        x, y = x0 + (x1 - x0) * t + sway * t * t, y0 + (y1 - y0) * t
        parts.append(Ell(x + (0.5 if i % 2 else -0.5), y, 2.7 - 0.9 * t, 2.5 - 0.5 * t))
        mids.append((x, y))
    blob(draw, parts, VAL_BRAID, sh=(0.9, 0.9))
    for i in range(1, n):
        (ax, ay), (bx, by) = mids[i - 1], mids[i]
        mx, my, k = (ax + bx) / 2.0, (ay + by) / 2.0, (1 if i % 2 else -1)
        stroke(draw, [(mx - 1.3, my - 0.5 * k), (mx + 1.3, my + 0.5 * k)], 0.5, ink(VAL_BRAID))
    bx, by = x1 + sway, y1 + 2.0
    for k in (-1, 1):
        cel(draw, Poly([(bx, by), (bx + k * 3.0, by - 1.6), (bx + k * 3.0, by + 1.8)]), VAL_BLUE, sh=None, lw=0.6)
    cel(draw, Ell(bx, by, 1.1, 1.1), lit(VAL_BLUE, 0.8), sh=None, lw=0.5)


def draw_valkyrie(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=1.0, head=0.96)
    d = r.d
    cx = r.cx
    hs = _hold_side(r)
    flap = [0.0, 1.0, 0.0, 1.0][frame]
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    # hair that lies close, so the helm covers it
    style = dict(HAIR_STYLES["long"], vol=1.2, back=12.0, side=10.0)
    # the spear arm: out to the side head-on; in profile the hand stays at the
    # hip while the other arm swings, so the point holds steady ahead of her
    reach, out = (0.0, 1.6) if not d else (0.15, 1.6 + r.ph * 2.2)
    rig_hair(r, draw, VAL_HAIR, style, layer="back")
    # feather wings on her back: behind her head-on and in profile
    if not r.back:
        for s in ((-1, 1) if not d else (-d,)):
            if d:
                feather_wing(draw, cx - d * 3.0, r.sh_y + 0.8, s, 16.0, flap=flap)
            else:
                feather_wing(draw, cx + s * 3.0, r.sh_y - 1.0, s, 14.4, flap=flap)
    if d:
        rig_arms(r, draw, VAL_STEEL, VAL_STEEL, layer="far")
    rig_legs(r, draw, VAL_BLUE, VAL_STEEL)
    rig_torso(r, draw, VAL_STEEL)
    rig_robe(r, draw, VAL_BLUE, top=r.waist_y + 0.6, hem=r.hip_y + 4.4, flare=2.4, split=True, trim=VAL_STEEL)
    rig_belt(r, draw, GOLD, buckle=VAL_BLUE)
    if not d:
        rig_arms(r, draw, VAL_STEEL, VAL_STEEL, layer="near", hands=False, sides=(-hs,))
    rig_arms(r, draw, VAL_STEEL, VAL_STEEL, layer="near", hands=False, sides=(hs,), reach=reach, out=out)
    if r.back:
        # ...and over her back and arms from behind
        for s in (-1, 1):
            feather_wing(draw, cx + s * 2.6, r.sh_y - 0.6, s, 14.4, flap=flap)
    rig_head(r, draw, SKIN, hair=VAL_HAIR, style=style, eye_color=(70, 130, 210), expression="set", mood="sharp", hat=True)
    # the winged helm
    kx = hx - d * 0.4
    if not d:
        for s in (-1, 1):
            _val_helm_wing(draw, hx + s * (rx + 0.4), hy - 6.0, s, 1.08)
    dome = arc_pts(kx, hy - 0.8, rx + 2.0, ry + 1.9, 182, 358, 26)
    cel(draw, Poly(dome + [(kx + rx + 2.1, hy - 2.2), (kx - rx - 2.1, hy - 2.2)]), VAL_STEEL, sh=(1.4, 1.0), hi=(0.8, 0.8))
    if not r.back:
        stroke(draw, [(hx + d * 3.0, hy - ry - 1.0), (hx + d * 3.0, hy - 3.6)], 1.5, GOLD)
    cel(draw, RRect(kx - rx - 2.3, hy - 4.4, kx + rx + 2.3, hy - 1.9, 0.8), GOLD, sh=(0.0, 0.7))
    if d:
        # in profile the near wing, on the side of the helm and swept back
        _val_helm_wing(draw, hx - d * 2.6, hy - 3.4, -d, 1.12, lean=32.0)
    # the braid, down her back
    if r.back:
        _val_braid(draw, hx, hy + 1.4, hx + 0.4, hy + 15.6, sway=r.ph * 1.4)
    elif d:
        _val_braid(draw, hx - d * (rx - 0.2), hy + 2.6, cx - d * 8.0, r.sh_y + 5.4, sway=-d * abs(r.ph) * 1.6)
    # the spear: upright beside her head-on, levelled ahead of her in profile
    h = hand_at(r, hs, reach, 0.0, out)
    if d:
        a = math.radians(24.0)
        ux, uy = d * math.cos(a), -math.sin(a)
        spear(draw, h[0] - ux * 15.0, h[1] - uy * 15.0, h[0] + ux * 15.6, h[1] + uy * 15.6, head=VAL_POINT, head_len=7.0,
              head_w=2.8, width=1.2)
    else:
        x = h[0] + 0.2
        spear(draw, x, r.base_y - 1.0, x, r.head_cy - 16.4, head=VAL_POINT, head_len=7.0, head_w=2.8, width=1.2)
    for side in _sides(r):
        held = side == hs
        rig_hand(r, draw, side, VAL_STEEL, reach=reach if held else 0.0, out=out if held else 0.0)


# ===================================================================
# WARLOCK (55) -- pact horns, a high-collared coat, a floating grimoire
# ===================================================================

# He stays dark: black hair, a near-black violet coat. The horns are a dark
# wine so they are horn and not hair, the collar is lined in the violet of
# his fire so it is a collar and not long hair, and the trim is wide enough
# to stay gold.
WLK_COAT = (66, 44, 88)
WLK_LINING = (128, 72, 184)
WLK_TRIM = (232, 188, 84)
WLK_FIRE = ((140, 60, 220), (190, 110, 255), (240, 214, 255))
WLK_HORN = (112, 44, 62)
WLK_HAIR = (40, 34, 54)
WLK_SKIN = (226, 214, 228)


def _wlk_horn(draw, x, y, s, near=True, side_on=False):
    """A pact horn from its root. Head-on it goes out to side `s`, up, and
    curls back in over the head; from the side that curl is seen edge-on, a
    tall spike swept back (`s` is then the way back)."""
    if side_on:
        path = bez((x, y), (x + s * 6.4, y - 4.6), (x + s * 1.6, y - 12.6), 9)
    else:
        path = bez((x, y), (x + s * 5.6, y - 1.6), (x + s * 5.2, y - 8.0), 5) + \
            bez((x + s * 5.2, y - 8.0), (x + s * 4.9, y - 12.8), (x + s * 1.0, y - 12.6), 5)[1:]
    n = len(path) - 1
    widths = [3.0 * (1.0 - i / float(n)) ** 0.8 + 0.35 for i in range(n + 1)]
    col = WLK_HORN if near else shade(WLK_HORN, 1.0)
    cel(draw, Limb(path, widths), col, sh=(0.7, 0.5))
    if near:
        # two ridges across it
        for i in (n * 3 // 10, n * 6 // 10):
            (ax, ay), (bx, by) = path[i - 1], path[i + 1]
            ln = math.hypot(bx - ax, by - ay) or 1.0
            nx, ny = -(by - ay) / ln * widths[i] * 0.85, (bx - ax) / ln * widths[i] * 0.85
            px, py = path[i]
            stroke(draw, [(px - nx, py - ny), (px + nx, py + ny)], 0.6, shade(col, 1.6))


def _wlk_collar(r, draw, layer):
    """The high collar: two fins lined in violet either side of his face
    head-on, one fin out behind the neck in profile, and from behind a wall
    round the back of his head (which also covers the nape)."""
    hx, hy, d, y, cx = r.hx, r.head_cy, r.d, r.sh_y, r.cx
    if layer == "under":
        if r.back or d:
            return
        for s in (-1, 1):
            fin = Poly([(hx + s * 3.6, y - 0.4), (hx + s * 11.0, hy + 1.4), (hx + s * 14.6, hy - 3.4), (hx + s * 14.2, hy + 4.8),
                        (hx + s * 9.6, y + 1.2)])
            cel(draw, fin, WLK_LINING, sh=(1.0, 0.8))
        return
    if r.back:
        top = [(hx - 14.8, hy - 1.6), (hx - 11.2, hy + 3.4), (hx - 5.4, hy + 6.6), (hx, hy + 7.3), (hx + 5.4, hy + 6.6),
               (hx + 11.2, hy + 3.4), (hx + 14.8, hy - 1.6)]
        wall = Poly(top + [(hx + 14.2, hy + 5.4), (hx + 9.6, y + 1.2), (hx + 6.0, y + 1.8), (hx - 6.0, y + 1.8), (hx - 9.6, y + 1.2),
                           (hx - 14.2, hy + 5.4)])
        cel(draw, wall, WLK_COAT, sh=(1.2, 1.0))
        stroke(draw, [(x, yy + 0.7) for (x, yy) in top], 0.9, WLK_TRIM)
    elif d:
        fin = Poly([(cx + d * 3.0, y - 0.2), (cx - d * 1.8, y - 3.0), (hx - d * 8.6, hy + 6.4), (hx - d * 13.4, hy + 3.4),
                    (hx - d * 12.6, hy + 8.4), (cx - d * 5.8, y + 1.0), (cx - d * 1.0, y + 1.6)])
        cel(draw, fin, WLK_COAT, sh=(1.0, 0.8))


def _wlk_book(draw, x, y):
    """The grimoire, floating open."""
    cel(draw, Poly([(x - 5.6, y - 3.4), (x, y - 2.4), (x + 5.6, y - 3.4), (x + 5.6, y + 3.0), (x, y + 4.0), (x - 5.6, y + 3.0)]),
        (132, 44, 70), sh=(0.5, 0.5))
    cel(draw, Poly([(x - 4.6, y - 2.8), (x, y - 1.8), (x + 4.6, y - 2.8), (x + 4.6, y + 2.0), (x, y + 3.0), (x - 4.6, y + 2.0)]),
        (246, 236, 210), sh=None, lw=0.5,
        regions=[(Poly([(x, y - 1.8), (x + 4.6, y - 2.8), (x + 4.6, y + 2.0), (x, y + 3.0)]), (226, 212, 186))])
    stroke(draw, [(x, y - 1.6), (x, y + 2.8)], 0.5, (150, 126, 110))
    star(draw, x + 2.3, y + 0.1, 1.6, WLK_FIRE[0], points=5)
    for k in (-0.9, 0.5):
        stroke(draw, [(x - 3.6, y + k - 0.5), (x - 1.2, y + k)], 0.5, (150, 126, 110))


def draw_warlock(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame)
    d = r.d
    cx = r.cx
    hs = _hold_side(r)
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    bob2 = [0.0, -0.6, -1.0, -0.6][frame]
    # the hand with the fire is held out from the body; the other hangs
    reach, out = (0.3, 2.2) if not d else (0.5, 3.0)
    bxs = cx + ((-13.6 if hs > 0 else 13.6) if not d else -d * 12.0)
    by = r.sh_y - 1.0 + bob2
    if d:
        _wlk_book(draw, bxs, by)
        _wlk_horn(draw, hx + d * 3.6, hy - ry + 2.8, -d, near=False, side_on=True)
    _wlk_collar(r, draw, "under")
    if d:
        rig_arms(r, draw, WLK_COAT, WLK_SKIN, layer="far", cuff=WLK_TRIM)
    rig_legs(r, draw, shade(WLK_COAT, 1.0), (40, 34, 50))
    robe = rig_robe(r, draw, WLK_COAT, flare=2.8, split=True)
    _robe_band(draw, robe, r.base_y - 5.6, WLK_TRIM, ink(WLK_COAT))
    rig_belt(r, draw, shade(WLK_COAT, 1.6), buckle=WLK_TRIM)
    if not d:
        rig_arms(r, draw, WLK_COAT, WLK_SKIN, layer="near", cuff=WLK_TRIM, hands=False, sides=(-hs,))
    rig_arms(r, draw, WLK_COAT, WLK_SKIN, layer="near", cuff=WLK_TRIM, hands=False, sides=(hs,), reach=reach, out=out)
    rig_head(r, draw, WLK_SKIN, hair=WLK_HAIR, style="swept", eye_color=(190, 90, 255), expression="smirk", mood="sharp",
             blush=False, ears=not d)
    # curling pact horns: one either side head-on; in profile the near one, on the side of his head
    if d:
        _wlk_horn(draw, hx - d * 1.0, hy - ry + 3.8, -d, side_on=True)
    else:
        for s in (-1, 1):
            _wlk_horn(draw, hx + s * (rx - 3.6), hy - ry + 2.8, s)
    _wlk_collar(r, draw, "over")
    if not d:
        _wlk_book(draw, bxs, by)
    for side in _sides(r):
        held = side == hs
        if held:
            h = hand_at(r, side, reach, 0.0, out)
            flame(draw, h[0] + (hs * 0.4 if not d else d * 0.6), h[1] - 1.6, 7.4, 11.0 + [0, 1, 0, 1][frame], WLK_FIRE,
                  sway=[0, 1, 0, -1][frame])
        rig_hand(r, draw, side, WLK_SKIN, reach=reach if held else 0.0, out=out if held else 0.0)


# ===================================================================
# INQUISITOR (56) -- a tall buckled hat, a long coat, a holy chain
# ===================================================================

# He is the grey one and stays grey: what reads at game size is the hat, and
# then the three things that are not grey, each drawn big enough to be a
# colour and not a line: the white falling collar, the red of his belt and
# hem, and the chain in his fist with its gold cross.
INQ_COAT = (84, 78, 92)
INQ_RED = (178, 40, 48)
INQ_COLLAR = (246, 246, 250)
INQ_CHAIN = (176, 182, 202)
INQ_HAT = (48, 44, 54)
INQ_HAIR = (104, 82, 68)
INQ_BOOT = (92, 64, 50)


def _inq_chain(draw, x, y, swing):
    """The chain hanging from his fist at (x, y), the gold cross on its end
    `swing` units to one side: five links a hand could count, by turns a ring
    seen flat and a ring seen on edge."""
    n, pitch = 5, 1.9

    def at(t):
        return (x + swing * t + math.sin(t * math.pi) * swing * 0.25, y + n * pitch * t)

    for i in range(n):
        lx, ly = at((i + 0.5) / n)
        if i % 2:
            cel(draw, RRect(lx - 0.7, ly - 1.4, lx + 0.7, ly + 1.4, 0.6), INQ_CHAIN, sh=None, lw=0.5)
        else:
            cel(draw, Ell(lx, ly, 1.4, 1.5), INQ_CHAIN, sh=None, lw=0.55)
            Ell(lx, ly, 0.45, 0.6).draw(draw, fill=ink(INQ_CHAIN))
    ex, ey = at(1.0)
    cross(draw, ex, ey + 2.5, 2.3, GOLD)


def _inq_arm(r, draw, side, hand):
    """The chain arm, from its shoulder to a fist held out from the body (the
    rig's arms end at the hip), with the white cuff at its wrist."""
    sx, sy = r.shoulder(side)
    bend = (side if not r.d else -r.d * 0.4) * 0.7
    ex, ey = (sx + hand[0]) / 2.0 + bend, (sy + hand[1]) / 2.0 + 1.2
    cel(draw, Limb([(sx, sy), (ex, ey), (hand[0], hand[1] - 0.2)], [2.2, 2.0, 1.8]), INQ_COAT, sh=(0.8, 0.4))
    ln = math.hypot(hand[0] - ex, hand[1] - ey) or 1.0
    ux, uy = (hand[0] - ex) / ln, (hand[1] - ey) / ln
    cel(draw, Limb([(hand[0] - ux * 3.0, hand[1] - uy * 3.0), (hand[0] - ux * 1.9, hand[1] - uy * 1.9)], [1.95, 1.95], cap=False),
        INQ_COLLAR, sh=None, lw=0.6)


def draw_inquisitor(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, head=0.95)
    d = r.d
    cx, sy = r.cx, r.sh_y
    hs = _hold_side(r)
    swing = [0.0, 1.5, 0.0, -1.5][frame]
    # The chain is held up and out, where it hangs clear of his coat against
    # the ground: out to his side head-on and from behind, at arm's length
    # ahead of him in profile (that hand keeps still while the other swings).
    reach, lift, out = (0.0, 5.0, 2.6) if not d else (0.5, 3.4, 5.6 + r.ph * 2.8)
    h = hand_at(r, hs, reach, lift, out)
    # Bare hands at white cuffs: in dark gloves the fist that holds the chain
    # was a black ball on the end of it.
    if d:
        rig_arms(r, draw, INQ_COAT, SKIN, layer="far", cuff=INQ_COLLAR)
    # the coat stops short of his boots, so that he is seen to walk
    rig_legs(r, draw, (60, 56, 66), INQ_BOOT)
    hem = r.base_y - 5.0
    coat = rig_robe(r, draw, INQ_COAT, flare=3.0, split=True, hem=hem)
    _robe_band(draw, coat, hem - 2.6, INQ_RED, ink(INQ_COAT))
    if r.back:
        # the vent up the back of the coat
        stroke(draw, [(cx + r.ph * 0.4, r.waist_y + 3.4), (cx + r.ph * 0.8, hem - 2.8)], 0.9, shade(INQ_COAT, 1.8))
    rig_belt(r, draw, INQ_RED, buckle=GOLD)
    if not d:
        rig_arms(r, draw, INQ_COAT, SKIN, layer="near", hands=False, sides=(-hs,), cuff=INQ_COLLAR)
    _inq_arm(r, draw, hs, h)
    # the white falling collar, wide over his shoulders: two square tabs in
    # front, one band behind
    if d:
        band = Poly([(cx + d * 4.9, sy - 2.4), (cx + d * 5.6, sy + 2.2), (cx - d * 0.6, sy + 3.0), (cx - d * 4.9, sy + 1.6),
                     (cx - d * 4.4, sy - 2.4)])
    elif r.back:
        band = Poly([(cx - 6.8, sy - 2.6), (cx + 6.8, sy - 2.6), (cx + 6.2, sy + 1.8), (cx, sy + 2.5), (cx - 6.2, sy + 1.8)])
    else:
        band = Poly([(cx - 6.8, sy - 2.6), (cx + 6.8, sy - 2.6), (cx + 6.4, sy + 2.4), (cx + 1.7, sy + 3.2), (cx, sy - 0.4),
                     (cx - 1.7, sy + 3.2), (cx - 6.4, sy + 2.4)])
    cel(draw, band, INQ_COLLAR, sh=(0.6, 0.5))
    head_skull(r, draw, SKIN)
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    if not r.back:
        head_face(r, draw, SKIN, iris=(64, 100, 164), mood="sharp", mouth="set", brows=True, brow_color=(70, 60, 60), blush=False)
    rig_hair(r, draw, INQ_HAIR, "short", hat=True, skin=SKIN)
    # capotain: a tall, slightly tapered crown on a wide brim, gold buckle
    cx0 = hx - d * 0.6
    brim = Ell(cx0 + d * 1.0, hy - 6.2, 15.0 if not d else 13.8, 4.0)
    cel(draw, brim, INQ_HAT, sh=(0.0, 1.2))
    crown = Poly([(cx0 - 7.4, hy - 6.4), (cx0 - 6.0, hy - 20.2), (cx0 + 6.0, hy - 20.2), (cx0 + 7.4, hy - 6.4)])
    cel(draw, crown, INQ_HAT, sh=(1.4, 0.6), hi=(0.6, 0.6))
    cel(draw, Poly([(cx0 - 7.4, hy - 6.4), (cx0 + 7.4, hy - 6.4), (cx0 + 7.1, hy - 9.0), (cx0 - 7.1, hy - 9.0)]), INQ_RED, sh=None)
    if not r.back:
        bx = cx0 + d * 3.0
        layer = draw.sub()
        cel(layer, RRect(bx - 2.3, hy - 9.9, bx + 2.3, hy - 5.5, 0.5), GOLD, sh=None)
        RRect(bx - 1.1, hy - 8.8, bx + 1.1, hy - 6.6, 0.3).draw(layer, fill=(0, 0, 0, 0))
        draw.merge(layer)
    _inq_chain(draw, h[0], h[1] + 1.2, swing)
    rig_hand(r, draw, hs, SKIN, reach=reach, lift=lift, out=out)
    if not d:
        rig_hand(r, draw, -hs, SKIN)


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
