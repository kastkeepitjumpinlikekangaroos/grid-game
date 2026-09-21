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
    spear, star, stroke, xform, arm_pts, face_anchor,
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

MINO_FUR = (152, 96, 62)
MINO_LT = (214, 170, 124)
MINO_HORN = (242, 232, 208)
MINO_KILT = (182, 48, 50)


def draw_minotaur(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=1.36, head=1.0)
    d = r.d
    ax_side = _hold_side(r)
    if d:
        rig_arms(r, draw, MINO_FUR, MINO_FUR, layer="far")
    rig_legs(r, draw, MINO_FUR, (70, 60, 66), width=1.14)
    rig_torso(r, draw, MINO_FUR)
    cx = r.cx
    if not r.back:
        cel(draw, Ell(cx + d * 2.0, r.sh_y + 3.4, 4.6 if not d else 3.0, 4.0), MINO_LT, sh=None, line=False)
        stroke(draw, [(cx - r.sh_w + 1.0 + d, r.sh_y - 1.6), (cx + r.hip_w - 0.6 + d, r.waist_y + 0.6)], 1.2, LEATHER)
    rig_robe(r, draw, MINO_KILT, top=r.waist_y + 0.6, hem=r.hip_y + 4.0, flare=2.0, split=True, trim=GOLD)
    rig_belt(r, draw, LEATHER, buckle=GOLD)
    rig_arms(r, draw, MINO_FUR, MINO_FUR, layer="near", hands=False)
    for side in _sides(r):
        _, e, w, h = arm_pts(r, side)
        cel(draw, Ell((e[0] + w[0]) / 2, (e[1] + w[1]) / 2 + 0.6, 2.8, 1.2), GOLD, sh=None, lw=0.6)
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    # horns first: the head covers their roots
    for s in ((-1, 1) if not d else (-d, d)):
        near = not d or s == -d
        hb = (hx + s * (rx - 2.4), hy - ry + 4.4) if not d else (hx + s * 2.4 - d * 1.0, hy - ry + 2.4)
        _horn(draw, hb, s, MINO_HORN if near else shade(MINO_HORN, 0.6), 1.1 if not d else 0.8, 1.0, 2.8)
    # ears, sideways under the horns
    for s in ((-1, 1) if not d else (-d,)):
        ex = hx + s * (rx + 0.6) if not d else hx - d * 3.0
        cel(draw, Poly([(ex - s * 1.0, hy - 3.0), (ex + s * 4.4, hy - 4.2), (ex + s * 3.4, hy - 1.0), (ex - s * 1.0, hy + 0.4)]) if not d else
            Ell(ex, hy - 2.0, 2.6, 1.8), MINO_FUR, sh=(0.4, 0.4))
    cel(draw, Ell(hx, hy, rx, ry), MINO_FUR, sh=(1.2, 1.0), hi=(0.6, 0.6))
    cel(draw, Ell(hx - d * 1.0, hy - ry + 1.6, 3.4, 2.4), shade(MINO_FUR, 0.8), sh=None)
    if r.back:
        return
    # muzzle, broad and pale, with a gold ring through it
    if d:
        mz = Ell(hx + d * 7.0, hy + 4.0, 5.6, 4.6)
        cel(draw, mz, MINO_LT, sh=(0.0, 0.8))
        Ell(hx + d * 10.4, hy + 3.0, 0.9, 0.7).draw(draw, fill=VOID)
        layer = draw.sub()
        Ell(hx + d * 10.6, hy + 5.6, 1.6, 1.8).draw(layer, fill=None, outline=GOLD, width=0.8)
        draw.merge(layer)
        eye_pair(draw, hx + d * 1.6, hx + d * 6.0, hy - 2.4, d, (220, 50, 40), skin=MINO_FUR, w=3.6, h=4.2)
    else:
        mz = Ell(hx, hy + 5.0, 7.4, 5.4)
        cel(draw, mz, MINO_LT, sh=(0.8, 0.8))
        for s in (-1, 1):
            Ell(hx + s * 2.6, hy + 4.4, 1.1, 0.8).draw(draw, fill=VOID)
        layer = draw.sub()
        Ell(hx, hy + 7.4, 2.0, 2.0).draw(layer, fill=None, outline=GOLD, width=0.9)
        draw.merge(layer)
        eye_pair(draw, hx - 5.0, hx + 5.0, hy - 2.6, 0, (220, 50, 40), skin=MINO_FUR, w=3.6, h=4.4)
    # a labrys, the double axe
    h = hand_at(r, ax_side)
    ang = -90 + (ax_side * 14 if not d else d * 24)
    top = xform([(15.0, 0.0)], h[0], h[1] + 3.0, ang)[0]
    cel(draw, Limb([xform([(-2.0, 0.0)], h[0], h[1] + 3.0, ang)[0], top], [1.1, 1.1]), WOOD, sh=None)
    for sgn in (1, -1):
        bitp = xform([(10.0, 0.8 * sgn), (15.4, 0.8 * sgn), (17.0, 6.6 * sgn), (13.0, 8.4 * sgn), (9.0, 6.0 * sgn)], h[0], h[1] + 3.0, ang)
        cel(draw, Poly(bitp), STEEL, sh=None, regions=[(Poly(bitp[1:4]), shade(STEEL, 0.8))])
    for side in _sides(r):
        rig_hand(r, draw, side, MINO_FUR)


# ===================================================================
# MEDUSA (88) -- snakes for hair, a serpent's tail, eyes that petrify
# ===================================================================

MED_SKIN = (216, 234, 204)
MED_SNAKE = (96, 174, 92)
MED_BELLY = (236, 222, 136)
MED_ROBE = (132, 70, 160)
MED_EYE = (255, 210, 40)


def draw_medusa(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=0.94, bob=0)
    d = r.d
    sway = [0.0, 1.0, 0.0, -1.0][frame]
    cx, base = r.cx, r.base_y
    # the serpent body: coils on the ground rising into the waist
    for (u, v, rx_, ry_) in ((0.0, -3.4, 11.6, 3.8), (-1.2, -7.0, 9.0, 3.4)):
        cel(draw, Ell(cx + u - d * 1.4 + sway * 0.4, base + v, rx_, ry_), MED_SNAKE, sh=(1.0, 0.9))
        if not r.back:
            cel(draw, Poly(arc_pts(cx + u - d * 1.4 + sway * 0.4, base + v, rx_ - 1.4, ry_ - 1.2, 20, 160, 12)), MED_BELLY, sh=None, line=False)
    tip = cx + (10.0 if not d else -d * 11.0)
    cel(draw, Limb([(tip - 2.0, base - 2.4), (tip + (3.0 if not d else -d * 3.0), base - 4.0 + sway), (tip + (4.0 if not d else -d * 4.4), base - 8.0)],
                   [2.0, 1.2, 0.3]), MED_SNAKE, sh=None)
    cel(draw, Limb([(cx - d * 0.8, base - 9.0), (cx, r.hip_y - 1.0)], [5.0, 5.4]), MED_SNAKE, sh=(0.8, 0.0))
    # a purple chiton over the torso, gold trim
    rig_torso(r, draw, MED_ROBE)
    cel(draw, RRect(cx - r.hip_w - 0.6, r.waist_y + 0.6, cx + r.hip_w + 0.6, r.hip_y + 1.4, 1.2) if not d else
        RRect(cx - 5.0, r.waist_y + 0.6, cx + 5.4, r.hip_y + 1.4, 1.2), MED_ROBE, sh=(0.8, 0.4))
    rig_belt(r, draw, GOLD, buckle=MED_SNAKE)
    if d:
        rig_arms(r, draw, MED_SKIN, MED_SKIN, layer="far")
    rig_arms(r, draw, MED_SKIN, MED_SKIN, layer="near", reach=0.3)
    for side in _sides(r):
        _, e, w, h = arm_pts(r, side, 0.3)
        cel(draw, Ell(e[0], e[1], 2.4, 1.2), GOLD, sh=None, lw=0.6)
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    # snakes: behind the head first, then the ones that curl forward
    snakes_back = ((-9.0, -10.0, -150), (9.0, -10.0, -30), (-12.0, -3.0, 180), (12.0, -3.0, 0), (0.0, -12.0, -90))
    snakes_front = ((-6.0, -9.0, -120), (6.0, -9.0, -60), (-11.0, 3.0, 150), (11.0, 3.0, 30))

    def snake(u, v, a, i):
        if d:
            u = u * 0.7 - d * 2.4
            a = a if d < 0 else 180 - a
        ang = math.radians(a)
        wig = math.sin(frame * math.pi / 2.0 + i) * 1.4
        base_p = (hx + u * 0.55, hy + v * 0.55)
        mid = (base_p[0] + math.cos(ang) * 4.4 - math.sin(ang) * wig, base_p[1] + math.sin(ang) * 4.4 + math.cos(ang) * wig)
        end = (base_p[0] + math.cos(ang) * 8.4, base_p[1] + math.sin(ang) * 8.4)
        cel(draw, Limb([base_p, mid, end], [2.2, 1.8, 1.6]), MED_SNAKE, sh=(0.4, 0.4))
        cel(draw, Ell(end[0], end[1], 2.2, 1.8), MED_SNAKE, sh=None, lw=0.6)
        Ell(end[0] + math.cos(ang) * 0.6 - 0.4, end[1] - 0.4, 0.45, 0.45).draw(draw, fill=(250, 60, 60))

    for i, (u, v, a) in enumerate(snakes_back):
        snake(u, v, a, i)
    head_skull(r, draw, MED_SKIN, ears=False)
    if not r.back:
        head_face(r, draw, MED_SKIN, iris=MED_EYE, mood="sharp", mouth="smirk", blush=False, brows=True, brow_color=shade(MED_SNAKE, 1.4))
        e1, e2, ey, _, _ = face_anchor(r)
        for ex in ((e1, e2) if not d else (e1, e2)):
            if frame % 2 == 0:
                sparkle(draw, ex + 2.4, ey - 2.4, 1.6, (255, 250, 180))
    # a snake-scale cap of hair, then a gold tiara
    rig_hair(r, draw, MED_SNAKE, dict(vol=1.2, fringe=((-0.6, 3.6), (-0.2, 4.4), (0.2, 4.4), (0.6, 3.6)), sweep=0.0, side=4.0, back=7.0),
             skin=MED_SKIN)
    if not r.back:
        tx = hx + d * 3.0
        cel(draw, Poly([(tx - 5.6, hy - ry + 3.6), (tx - 2.8, hy - ry + 1.4), (tx, hy - ry - 1.6), (tx + 2.8, hy - ry + 1.4),
                        (tx + 5.6, hy - ry + 3.6), (tx, hy - ry + 2.4)]), GOLD, sh=(0.4, 0.4))
        gem(draw, tx, hy - ry + 0.8, 1.2, (60, 200, 140))
    for i, (u, v, a) in enumerate(snakes_front):
        snake(u, v, a, i + 5)


# ===================================================================
# CERBERUS (89) -- three heads, spiked collars, fire in its fur
# ===================================================================

CER = (108, 70, 84)
CER_LT = (160, 116, 128)
CER_LAVA = (255, 118, 44)
CER_EYE = (255, 196, 60)
CER_COLLAR = (60, 56, 66)


def _dog_head(draw, x, y, d, k=1.0, back=False, frame=0):
    for s in ((-1, 1) if not d else (-d, d)):
        near = not d or s == -d
        ex = x + s * 4.4 * k if not d else x - d * 1.4 * k + s * 1.8 * k
        cel(draw, Poly([(ex - 2.0 * k, y - 3.6 * k), (ex + (s * 0.8 if not d else -d * 1.0) * k, y - 9.4 * k), (ex + 2.0 * k, y - 3.6 * k)]),
            CER if near else shade(CER, 0.6), sh=(0.3, 0.3))
    cel(draw, Ell(x, y, 6.2 * k, 5.6 * k), CER, sh=(0.8, 0.8), hi=(0.4, 0.4))
    if back:
        return
    if d:
        cel(draw, Ell(x + d * 5.0 * k, y + 1.8 * k, 3.8 * k, 2.8 * k), CER_LT, sh=(0.0, 0.5))
        Ell(x + d * 8.2 * k, y + 1.0 * k, 1.1 * k, 0.9 * k).draw(draw, fill=VOID)
        Poly([(x + d * 3.4 * k, y + 3.4 * k), (x + d * 8.0 * k, y + 3.0 * k), (x + d * 7.0 * k, y + 4.6 * k), (x + d * 3.6 * k, y + 4.6 * k)]).draw(
            draw, fill=(150, 30, 40))
        Poly([(x + d * 6.0 * k, y + 3.1 * k), (x + d * 6.8 * k, y + 3.1 * k), (x + d * 6.4 * k, y + 4.6 * k)]).draw(draw, fill=TOOTH)
        Poly([(x + d * 0.6 * k, y - 2.4 * k), (x + d * 3.4 * k, y - 1.2 * k), (x + d * 2.8 * k, y + 0.2 * k), (x + d * 0.4 * k, y - 0.6 * k)]).draw(
            draw, fill=CER_EYE)
        return
    cel(draw, Ell(x, y + 2.4 * k, 3.8 * k, 3.0 * k), CER_LT, sh=(0.4, 0.4))
    Ell(x, y + 1.0 * k, 1.4 * k, 1.0 * k).draw(draw, fill=VOID)
    Poly([(x - 2.6 * k, y + 3.6 * k), (x + 2.6 * k, y + 3.6 * k), (x + 1.6 * k, y + 5.2 * k), (x - 1.6 * k, y + 5.2 * k)]).draw(draw, fill=(150, 30, 40))
    for s in (-1, 1):
        Poly([(x + s * 1.6 * k - 0.4, y + 3.6 * k), (x + s * 1.6 * k + 0.4, y + 3.6 * k), (x + s * 1.6 * k, y + 5.0 * k)]).draw(draw, fill=TOOTH)
        ex = x + s * 2.8 * k
        Poly([(ex - s * 1.6 * k, y - 0.6 * k), (ex - s * 0.4 * k, y - 2.2 * k), (ex + s * 1.8 * k, y - 2.4 * k), (ex + s * 1.4 * k, y - 0.6 * k)]).draw(
            draw, fill=CER_EYE)
        Ell(ex, y - 1.4 * k, 0.4 * k, 0.4 * k).draw(draw, fill=(255, 255, 230))


def _collar(draw, x, y, w, k=1.0):
    cel(draw, RRect(x - w, y - 1.2 * k, x + w, y + 1.2 * k, 1.0 * k), CER_COLLAR, sh=None, lw=0.7)
    n = max(2, int(w / 1.8))
    for i in range(n):
        sx = x - w + (2 * w) * (i + 0.5) / n
        Poly([(sx - 0.7 * k, y - 1.0 * k), (sx + 0.7 * k, y - 1.0 * k), (sx, y - 2.8 * k)]).draw(draw, fill=(214, 216, 226))


def draw_cerberus(draw, ox, oy, direction, frame):
    d, back, base, cx, ph = creature_setup(ox, oy, direction, frame)
    sway = [0.0, 1.0, 0.0, -1.0][frame]
    if d:
        # a fire-tipped tail
        cel(draw, Limb([(cx - d * 8.0, base - 14.0), (cx - d * 13.0, base - 17.0 + sway), (cx - d * 14.0, base - 21.0)], [1.8, 1.4, 0.8]), CER, sh=None)
        flame(draw, cx - d * 14.0, base - 19.6, 4.0, 6.0, (CER_LAVA, (255, 190, 80), (255, 240, 170)), sway=sway)
        quad_legs(draw, cx, base, d, ph, CER, 5.4, -6.6, base - 11.0, paw=CER_LT, w=2.2)
        body = Poly(rot_pts(arc_pts(cx - d * 1.0, base - 14.0, 11.0, 6.8, 0, 360)[:-1], cx, base - 14.0, -d * 8))
        cel(draw, body, CER, sh=(1.2, 1.2))
        for (u, v) in ((-4.0, -2.0), (1.0, -3.4), (-1.0, 1.0)):
            stroke(draw, [(cx + d * u, base - 14.0 + v), (cx + d * (u + 2.0), base - 13.0 + v)], 0.7, CER_LAVA)
        # three heads stacked: far, middle, near
        for (u, v, k) in ((6.4, -27.0, 0.86), (9.6, -22.6, 1.0), (6.0, -18.4, 0.9)):
            _dog_head(draw, cx + d * u, base + v, d, k, frame=frame)
            _collar(draw, cx + d * (u - 2.4), base + v + 5.0 * k, 3.4 * k, k)
        return
    if back:
        for s in (-1, 1):
            fwd = ph * s
            cel(draw, Limb([(cx + s * 5.4, base - 10.0), (cx + s * 5.6, base - 1.8 - (1.0 if fwd < 0 else 0))], [2.4, 2.2]), CER, sh=None)
            draw_paw(draw, cx + s * 5.6, base - 1.2 - (1.0 if fwd < 0 else 0), CER_LT, claws=False)
        cel(draw, Ell(cx, base - 14.0, 10.0, 7.4), CER, sh=(1.2, 1.2))
        cel(draw, Limb([(cx, base - 13.0), (cx + 2.0 + sway, base - 18.0), (cx + 1.0 + sway, base - 22.0)], [1.8, 1.4, 0.8]), CER, sh=None)
        flame(draw, cx + 1.0 + sway, base - 20.6, 4.0, 6.0, (CER_LAVA, (255, 190, 80), (255, 240, 170)), sway=sway)
        for (u, k) in ((-8.0, 0.86), (8.0, 0.86), (0.0, 1.0)):
            _dog_head(draw, cx + u, base - 26.0 + (2.0 if u else 0.0), 0, k, back=True)
        return
    # head-on: three heads over a burly chest
    for s in (-1, 1):
        cel(draw, Limb([(cx + s * 7.6, base - 8.0), (cx + s * 7.8, base - 1.8)], [2.0, 1.8]), shade(CER, 0.6), sh=None)
    cel(draw, Ell(cx, base - 12.0, 9.4, 6.8), CER, sh=(1.2, 1.0))
    for s in (-1, 1):
        fwd = ph * s
        lift = 1.0 if fwd < 0 else 0.0
        cel(draw, Limb([(cx + s * 4.0, base - 11.0), (cx + s * 4.0, base - 1.8 - lift)], [2.6, 2.2]), CER, sh=(0.6, 0.0))
        draw_paw(draw, cx + s * 4.0, base - 1.0 - lift, CER_LT)
    for (u, v) in ((-3.0, -13.0), (3.0, -11.0)):
        stroke(draw, [(cx + u - 1.0, base + v), (cx + u + 1.0, base + v + 1.4)], 0.7, CER_LAVA)
    for (u, v, k) in ((-10.6, -22.4, 0.84), (10.6, -22.4, 0.84), (0.0, -27.4, 1.06)):
        _dog_head(draw, cx + u, base + v, 0, k, frame=frame)
        _collar(draw, cx + u, base + v + 5.4 * k, 4.4 * k, k)


# ===================================================================
# CENTAUR (90) -- a chestnut horse body, a ranger's torso, a bow
# ===================================================================

HORSE = (178, 114, 70)
HORSE_DK = (104, 62, 42)
HORSE_SOCK = (246, 240, 228)
CEN_TUNIC = (88, 150, 94)


def draw_centaur(draw, ox, oy, direction, frame):
    d, back, base, cx, ph = creature_setup(ox, oy, direction, frame)
    sway = [0.0, 1.0, 0.0, -1.0][frame]
    # the rider-torso sits on the horse's withers: a rig raised to meet it
    r = rig(ox + (d * 7.0 if d else 0.0), oy - 7.0, direction, frame, build=0.96, head=0.92, step=0)
    if d:
        # tail and legs, then the barrel
        cel(draw, Limb([(cx - d * 9.4, base - 17.0), (cx - d * 13.0, base - 13.0 + sway), (cx - d * 12.4, base - 6.0)], [2.2, 2.4, 0.8]), HORSE_DK, sh=None)
        quad_legs(draw, cx - d * 1.0, base, d, ph, HORSE, 6.0, -7.6, base - 12.0, paw=HORSE_SOCK, w=1.8)
        body = Poly(rot_pts(arc_pts(cx - d * 1.4, base - 15.4, 11.0, 6.4, 0, 360)[:-1], cx, base - 15.4, -d * 4))
        cel(draw, body, HORSE, sh=(1.2, 1.2))
    elif back:
        for s in (-1, 1):
            fwd = ph * s
            cel(draw, Limb([(cx + s * 5.0, base - 11.0), (cx + s * 5.2, base - 2.0 - (1.0 if fwd < 0 else 0))], [2.2, 1.8]), HORSE, sh=None)
            cel(draw, Ell(cx + s * 5.2, base - 1.4 - (1.0 if fwd < 0 else 0), 2.2, 1.4), HORSE_SOCK, sh=None)
        cel(draw, Ell(cx, base - 15.0, 9.4, 7.0), HORSE, sh=(1.2, 1.2))
        cel(draw, Limb([(cx, base - 17.0), (cx + sway, base - 12.0), (cx + sway * 1.4, base - 6.0)], [2.4, 2.4, 0.8]), HORSE_DK, sh=None)
    else:
        # the barrel and hindquarters show either side, behind the chest
        cel(draw, Ell(cx, base - 15.4, 12.6, 5.6), shade(HORSE, 0.6), sh=None)
        for s in (-1, 1):
            cel(draw, Limb([(cx + s * 8.4, base - 10.0), (cx + s * 8.6, base - 2.0)], [2.0, 1.8]), shade(HORSE, 0.6), sh=None)
            cel(draw, Ell(cx + s * 8.6, base - 1.4, 2.2, 1.3), shade(HORSE_SOCK, 0.6), sh=None)
        cel(draw, Ell(cx, base - 14.0, 10.4, 6.8), HORSE, sh=(1.2, 1.0))
        for s in (-1, 1):
            fwd = ph * s
            lift = 1.0 if fwd < 0 else 0.0
            cel(draw, Limb([(cx + s * 3.6, base - 12.0), (cx + s * 3.6, base - 2.4 - lift)], [2.2, 1.8]), HORSE, sh=(0.5, 0.0))
            cel(draw, Ell(cx + s * 3.6, base - 1.6 - lift, 2.2, 1.4), HORSE_SOCK, sh=(0.0, 0.5))
    # the rider
    rig_torso(r, draw, CEN_TUNIC, bottom=r.hip_y + 2.0)
    rig_belt(r, draw, LEATHER, buckle=GOLD)
    rig_arms(r, draw, SKIN_TAN, SKIN_TAN, layer="near", hands=False)
    rig_head(r, draw, SKIN_TAN, hair=(120, 74, 44), style="long", eye_color=(80, 120, 60), expression="set", mood="sharp")
    # laurel wreath
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    for i, u in enumerate((-7.0, -3.6, 0.0, 3.6, 7.0) if not d else (-d * 4.0, -d * 0.6, d * 3.0)):
        leaf(draw, hx + u, hy - ry + 3.4, -90 + u * 8 + (i % 2) * 20, 3.2, 2.0, (130, 190, 90), vein=False)
    side = r.d if d else (1 if not r.back else -1)
    h = hand_at(r, side, 0.3)
    if not r.back:
        bow(draw, h[0], h[1] - 4.0, d, 22.0, side=side)
    for s in _sides(r):
        rig_hand(r, draw, s, SKIN_TAN, reach=0.3 if s == side else 0.0)


# ===================================================================
# KRAKEN (91) -- a bulbous mantle, big fierce eyes, a mass of tentacles
# ===================================================================

KRAK = (64, 86, 176)
KRAK_LT = (120, 150, 226)
KRAK_SUCK = (206, 222, 252)


def draw_kraken(draw, ox, oy, direction, frame):
    d, back, base, cx, ph = creature_setup(ox, oy, direction, frame, bob=[0, -1, -1, 0])
    wave = frame * math.pi / 2.0
    my = base - 24.0
    # tentacles fanned out on the ground
    n = 7
    for i in range(n):
        t = i / float(n - 1) - 0.5
        x0 = cx + t * 12.0 - d * 1.0
        pts = []
        for j in range(5):
            k = j / 4.0
            pts.append((x0 + t * k * 16.0 + math.sin(wave + i + k * 3.0) * 1.4 - d * k * 3.0, my + 6.0 + k * 13.0 - (k ** 2) * (3.0 if abs(t) > 0.3 else 0.0)))
        tip = pts[-1]
        pts.append((tip[0] + (2.4 if t >= 0 else -2.4), tip[1] - 2.4))
        col = KRAK if i % 2 else shade(KRAK, 0.4)
        cel(draw, Limb(pts, [2.6, 2.4, 2.0, 1.6, 1.0, 0.4]), col, sh=(0.4, 0.4))
        if not back and i % 2:
            for kk in (1, 2, 3):
                Ell(pts[kk][0], pts[kk][1] + 1.2, 0.7, 0.6).draw(draw, fill=KRAK_SUCK)
    # two long tentacles raised, waving
    for s in (-1, 1):
        a = math.sin(wave + (s > 0) * 2.0)
        pts = [(cx + s * 7.0, my + 4.0), (cx + s * 13.0, my + 1.0 + a * 1.4), (cx + s * 16.0, my - 5.0 + a), (cx + s * 14.0, my - 10.0 - a),
               (cx + s * 11.0, my - 11.0)]
        cel(draw, Limb(pts, [2.4, 2.2, 1.8, 1.2, 0.4]), KRAK, sh=(0.5, 0.5))
        if not back:
            for kk in (1, 2):
                Ell(pts[kk][0] - s * 1.2, pts[kk][1] + 0.6, 0.7, 0.6).draw(draw, fill=KRAK_SUCK)
    # the mantle
    mantle = arc_pts(cx - d * 1.0, my - 2.0, 12.0 if not d else 11.0, 14.0, 180, 360, 30) + \
        arc_pts(cx - d * 1.0, my + 2.0, 11.4 if not d else 10.4, 6.0, 0, 180, 16)[1:-1]
    cel(draw, Poly(mantle), KRAK, sh=(1.6, 1.4), hi=(1.0, 1.0))
    for (u, v, k) in ((-5.0, -10.0, 1.6), (4.0, -12.0, 1.2), (6.0, -6.0, 1.0), (-7.0, -4.0, 1.0)):
        Ell(cx + u - d * 1.0, my + v, k, k * 0.8).draw(draw, fill=KRAK_LT)
    if back:
        return
    fx = cx + d * 3.0
    eye_pair(draw, fx - 4.6 if not d else fx + d * 0.6, fx + 4.6 if not d else fx + d * 5.4, my + 0.4, d, (250, 210, 50), mood="sharp",
             skin=KRAK, w=4.6, h=5.4, lash=(20, 24, 60))
    stroke(draw, [(fx - 2.4 + d * 1.6, my + 5.4), (fx + d * 1.6, my + 4.8), (fx + 2.4 + d * 1.6, my + 5.4)], 0.7, shade(KRAK, 1.8))


# ===================================================================
# SPHINX (92) -- a golden lion with a pharaoh's head and nemes
# ===================================================================

SPX = (230, 190, 116)
SPX_DK = (190, 144, 82)
NEMES_BLUE = (56, 90, 180)
SPX_SKIN = (238, 196, 150)


def _nemes(draw, hx, hy, rx, ry, d, back=False):
    """The striped headcloth: a crown over the head and lappets to the shoulders."""
    if d:
        pts = arc_pts(hx - d * 0.6, hy - 0.6, rx + 1.4, ry + 1.2, 180, 360, 26)
        cloth = Poly(pts + ([(hx + d * (rx - 1.0), hy + 3.0), (hx + d * 1.0, hy + 2.0), (hx - d * 2.0, hy + 12.0), (hx - d * (rx + 3.0), hy + 12.0)]
                            if d > 0 else [(hx - d * (rx + 3.0), hy + 12.0), (hx - d * 2.0, hy + 12.0), (hx + d * 1.0, hy + 2.0), (hx + d * (rx - 1.0), hy + 3.0)]))
    else:
        pts = arc_pts(hx, hy - 0.6, rx + 1.4, ry + 1.2, 180, 360, 26)
        cloth = Poly(pts + [(hx + rx + 1.6, hy + 2.0), (hx + rx + 3.4, hy + 13.0), (hx + rx - 2.4, hy + 13.0), (hx + rx - 2.6, hy + 2.6),
                            (hx - rx + 2.6, hy + 2.6), (hx - rx + 2.4, hy + 13.0), (hx - rx - 3.4, hy + 13.0), (hx - rx - 1.6, hy + 2.0)]) \
            if not back else Poly(pts + [(hx + rx + 2.0, hy + 12.0), (hx - rx - 2.0, hy + 12.0)])
    cel(draw, cloth, GOLD, sh=(1.2, 1.0))
    layer = draw.sub()
    for k in range(-6, 7):
        y = hy - ry + 2.0 + k * 2.6
        Poly([(hx - 30, y), (hx + 30, y), (hx + 30, y + 1.2), (hx - 30, y + 1.2)]).draw(layer, fill=NEMES_BLUE)
    m = draw.sub()
    cloth.draw(m, fill=(255, 255, 255))
    draw.merge(layer, clip=m)
    cloth.draw(draw, fill=None, outline=ink(GOLD), width=1.0)


def draw_sphinx(draw, ox, oy, direction, frame):
    d, back, base, cx, ph = creature_setup(ox, oy, direction, frame)
    sway = [0.0, 1.0, 0.0, -1.0][frame]
    if d:
        cel(draw, Limb([(cx - d * 9.0, base - 13.0), (cx - d * 13.0, base - 15.0 + sway), (cx - d * 14.0, base - 19.0)], [1.2, 1.0, 0.6]), SPX, sh=None)
        cel(draw, Ell(cx - d * 14.0, base - 19.6, 1.8, 2.0), SPX_DK, sh=None)
        quad_legs(draw, cx, base, d, ph, SPX, 5.6, -6.4, base - 10.0, paw=SPX, w=2.2)
        body = Poly(rot_pts(arc_pts(cx - d * 1.0, base - 13.0, 10.4, 6.2, 0, 360)[:-1], cx, base - 13.0, -d * 4))
        cel(draw, body, SPX, sh=(1.2, 1.2))
        hx, hy = cx + d * 7.0, base - 25.0
    elif back:
        for s in (-1, 1):
            fwd = ph * s
            cel(draw, Limb([(cx + s * 5.0, base - 10.0), (cx + s * 5.2, base - 2.0 - (1.0 if fwd < 0 else 0))], [2.4, 2.2]), SPX, sh=None)
            draw_paw(draw, cx + s * 5.2, base - 1.2 - (1.0 if fwd < 0 else 0), SPX, claws=False)
        cel(draw, Ell(cx, base - 13.0, 9.4, 7.0), SPX, sh=(1.2, 1.2))
        cel(draw, Limb([(cx, base - 12.0), (cx + 2.0 + sway, base - 16.0), (cx + 1.0 + sway, base - 20.0)], [1.2, 1.0, 0.6]), SPX, sh=None)
        hx, hy = cx, base - 26.0
    else:
        for s in (-1, 1):
            cel(draw, Limb([(cx + s * 7.6, base - 9.0), (cx + s * 7.8, base - 1.8)], [2.0, 1.8]), shade(SPX, 0.6), sh=None)
        cel(draw, Ell(cx, base - 11.0, 8.6, 6.0), SPX, sh=(1.2, 1.0))
        for s in (-1, 1):
            fwd = ph * s
            lift = 1.0 if fwd < 0 else 0.0
            cel(draw, Limb([(cx + s * 4.0, base - 10.0), (cx + s * 4.0, base - 1.8 - lift)], [2.6, 2.2]), SPX, sh=(0.6, 0.0))
            draw_paw(draw, cx + s * 4.0, base - 1.0 - lift, SPX)
        hx, hy = cx, base - 26.0
    # the pharaoh's head
    r = rig(ox, oy, direction, frame, head=0.9)
    r.hx, r.head_cy = hx, hy
    rx, ry = r.head_rx, r.head_ry
    if back:
        _nemes(draw, hx, hy, rx, ry, 0, back=True)
        return
    head_skull(r, draw, SPX_SKIN, ears=False)
    head_face(r, draw, SPX_SKIN, iris=(60, 50, 60), mood="calm", mouth="smirk", blush=False, brows=True, brow_color=(60, 44, 40),
              lash=(20, 16, 24))
    _nemes(draw, hx, hy, rx, ry, d)
    # the gold cobra on the brow and the braided beard
    ux = hx + d * 3.6
    cel(draw, Poly([(ux - 1.4, hy - ry + 1.0), (ux, hy - ry - 3.4), (ux + 1.4, hy - ry + 1.0)]), GOLD, sh=None, lw=0.6)
    cel(draw, Ell(ux, hy - ry - 2.6, 1.3, 1.1), GOLD, sh=None, lw=0.6)
    bx = hx + d * 5.4
    cel(draw, RRect(bx - 1.1, hy + ry - 1.4, bx + 1.1, hy + ry + 4.6, 0.8), NEMES_BLUE, sh=None, lw=0.6)
    for k in (0.6, 2.2, 3.6):
        stroke(draw, [(bx - 1.0, hy + ry - 1.2 + k), (bx + 1.0, hy + ry - 1.2 + k)], 0.4, GOLD)


# ===================================================================
# CYCLOPS (93) -- one enormous eye, a horn, tusks, a spiked club
# ===================================================================

CYC_SKIN = (190, 178, 138)
CYC_FUR = (128, 92, 60)
CYC_IRIS = (60, 150, 90)


def draw_cyclops(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=1.36, head=1.02)
    d = r.d
    cl_side = _hold_side(r)
    if d:
        rig_arms(r, draw, CYC_SKIN, CYC_SKIN, layer="far")
    rig_legs(r, draw, CYC_SKIN, CYC_FUR, bare=True, width=1.14)
    rig_torso(r, draw, CYC_SKIN)
    cx = r.cx
    if not r.back:
        cel(draw, Ell(cx + d * 2.0 - 2.6, r.sh_y + 2.4, 2.8, 2.0), shade(CYC_SKIN, 0.5), sh=None, line=False)
        cel(draw, Ell(cx + d * 2.0 + 2.6, r.sh_y + 2.4, 2.8, 2.0), shade(CYC_SKIN, 0.5), sh=None, line=False)
    # fur loincloth and a shoulder strap
    cel(draw, Poly([(cx - r.hip_w - 0.6, r.waist_y + 0.6), (cx + r.hip_w + 0.6, r.waist_y + 0.6), (cx + r.hip_w + 1.6, r.hip_y + 3.2),
                    (cx + 2.0, r.hip_y + 2.2), (cx, r.hip_y + 3.8), (cx - 2.0, r.hip_y + 2.2), (cx - r.hip_w - 1.6, r.hip_y + 3.2)]) if not d else
        Poly([(cx - 5.0, r.waist_y + 0.6), (cx + 5.4, r.waist_y + 0.6), (cx + 6.0, r.hip_y + 3.2), (cx - 6.0, r.hip_y + 3.4)]), CYC_FUR, sh=(0.8, 0.6))
    if not r.back:
        stroke(draw, [(cx - r.sh_w + 1.0 + d, r.sh_y - 1.4), (cx + r.hip_w - 1.0 + d, r.waist_y + 0.6)], 1.2, CYC_FUR)
    rig_arms(r, draw, CYC_SKIN, CYC_SKIN, layer="near", hands=False)
    head_skull(r, draw, CYC_SKIN, ears=False)
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    # a stubby horn
    hb = (hx - d * 1.0, hy - ry + 1.4)
    cel(draw, Poly([(hb[0] - 2.0, hb[1] + 1.0), (hb[0] - d * 1.0 + 0.4, hb[1] - 5.0), (hb[0] + 2.0, hb[1] + 1.0)]), (236, 226, 204), sh=(0.3, 0.4))
    if not r.back:
        # the one great eye
        ex = hx + d * 4.0
        ey = hy + 0.6
        w = 8.4 if not d else 6.4
        ms_eye(draw, ex, ey, w, 9.0, CYC_IRIS, (float(d), 0.0), "sharp", skin=CYC_SKIN, side=1)
        cel(draw, Poly([(ex - w * 0.7, ey - 5.6), (ex, ey - 6.6), (ex + w * 0.7, ey - 5.6), (ex + w * 0.6, ey - 4.6), (ex - w * 0.6, ey - 4.6)]),
            shade(CYC_SKIN, 1.6), sh=None, line=False)
        # underbite and tusks
        mx = hx + d * 5.0
        my = hy + 7.2
        stroke(draw, [(mx - 3.6, my), (mx, my + 0.8), (mx + 3.6, my)], 0.8, shade(CYC_SKIN, 2.4))
        for s in ((-1, 1) if not d else (d,)):
            tx = mx + s * 2.6 if not d else mx + d * 2.0
            cel(draw, Poly([(tx - 0.9, my + 0.4), (tx + 0.9, my + 0.4), (tx + (0.2 * s if not d else 0), my - 2.6)]), TOOTH, sh=None, lw=0.5)
    # a spiked club
    h = hand_at(r, cl_side)
    ang = -90 + (cl_side * 22 if not d else d * 34)
    shaft = xform([(-2.0, 0.0), (14.0, 0.0)], h[0], h[1], ang)
    cel(draw, Limb(shaft, [1.4, 3.0]), WOOD, sh=(0.6, 0.0))
    for t in (8.0, 11.0, 13.4):
        for sgn in (-1, 1):
            p = xform([(t, sgn * (1.6 + t * 0.1))], h[0], h[1], ang)[0]
            q = xform([(t + 0.4, sgn * (3.6 + t * 0.12))], h[0], h[1], ang)[0]
            Poly([p, q, xform([(t + 1.4, sgn * (1.6 + t * 0.1))], h[0], h[1], ang)[0]]).draw(draw, fill=(214, 216, 226))
    for side in _sides(r):
        rig_hand(r, draw, side, CYC_SKIN)


# ===================================================================
# HARPY (94) -- wild feathered hair, wings for arms, a bird's legs
# ===================================================================

HARPY = (156, 84, 178)
HARPY_TIP = (238, 180, 240)
HARPY_SKIN = (250, 222, 214)
HARPY_TALON = (250, 196, 70)


def draw_harpy(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=0.9, bob=[-1, -3, -2, -3][frame], step=0)
    d = r.d
    flap = [0.0, 1.0, 0.4, 1.0][frame]
    cx = r.cx
    # wings for arms
    for s in ((-1, 1) if not d else (-d, d)):
        near = not d or s == d
        col = HARPY if near else shade(HARPY, 0.6)
        sx = cx + s * 4.0 if not d else cx + s * 1.0
        sy = r.sh_y - 0.6
        tipx = sx + s * (15.0 + flap * 1.6) if not d else sx + s * (11.0 + flap * 1.6)
        tipy = sy - 6.0 - flap * 5.0
        pts = [(sx, sy - 1.0), (sx + s * 6.0, sy - 5.0 - flap * 2.0), (tipx, tipy), (tipx - s * 1.2, tipy + 4.0), (tipx + s * 0.4, tipy + 6.0),
               (tipx - s * 2.6, tipy + 8.0), (tipx - s * 2.0, tipy + 11.0), (sx + s * 5.0, sy + 8.0), (sx, sy + 4.0)]
        cel(draw, Poly(pts), col, sh=(0.8, 0.8))
        cel(draw, Poly([pts[3], pts[4], pts[5], pts[6], (tipx - s * 5.0, tipy + 8.0)]), HARPY_TIP if near else shade(HARPY_TIP, 0.6), sh=None, line=False)
    # bird legs with talons
    for s in ((-1, 1) if not d else (-d, d)):
        near = not d or s == d
        col = HARPY_TALON if near else shade(HARPY_TALON, 0.6)
        lx = cx + s * 2.6 if not d else cx + s * 1.0
        cel(draw, Limb([(lx, r.hip_y), (lx + (s * 0.6 if not d else d * 1.4), r.base_y - 5.0), (lx, r.base_y - 1.6)], [1.1, 0.9, 0.8]), col, sh=None, lw=0.7)
        for k in ((-1, 0, 1) if not d else (0, 1, 2)):
            tx = lx + (k * 1.4 if not d else d * (0.8 + k * 0.9))
            stroke(draw, [(lx, r.base_y - 1.6), (tx, r.base_y - 0.4)], 0.7, col)
            Ell(tx, r.base_y - 0.2, 0.45, 0.45).draw(draw, fill=VOID)
    # a feather skirt and a bodice
    rig_torso(r, draw, HARPY)
    skirt = [(cx - r.hip_w - 0.4, r.waist_y + 0.6), (cx + r.hip_w + 0.4, r.waist_y + 0.6)]
    n = 5
    for i in range(n + 1):
        t = i / float(n)
        skirt.append((cx + r.hip_w + 2.0 - (2 * r.hip_w + 4.0) * t, r.hip_y + (4.0 if i % 2 == 0 else 1.6)))
    cel(draw, Poly(skirt), HARPY, sh=(0.8, 0.6), regions=[(Poly([(cx - 20, r.hip_y + 1.0), (cx + 20, r.hip_y + 1.0), (cx + 20, r.hip_y + 6),
                                                                  (cx - 20, r.hip_y + 6)]), HARPY_TIP)])
    head_skull(r, draw, HARPY_SKIN, ears=False)
    if not r.back:
        head_face(r, draw, HARPY_SKIN, iris=(230, 60, 120), mood="sharp", mouth="open", blush=False, brows=True, brow_color=shade(HARPY, 1.4))
    rig_hair(r, draw, HARPY, "wild", skin=HARPY_SKIN)
    # a crest of long feathers
    hx, hy, ry = r.hx, r.head_cy, r.head_ry
    for i, a in enumerate((-120, -90, -60) if not d else ((-130, -105) if d > 0 else (-50, -75))):
        ang = math.radians(a)
        bx, by = hx - d * 2.0, hy - ry + 1.0
        cel(draw, Limb([(bx, by), (bx + math.cos(ang) * 5.0, by + math.sin(ang) * 5.0), (bx + math.cos(ang) * 9.0, by + math.sin(ang) * 9.0)],
                       [1.2, 1.6, 0.3]), HARPY_TIP if i % 2 else HARPY, sh=None)
    if not r.back and frame % 2 == 1:
        mx = hx + d * 7.0
        for k, rad in enumerate((3.0, 5.4)):
            pts = arc_pts(mx + (d * 3.0 if d else 0), hy + 7.4 + (0 if d else 5.0), rad, rad * 0.7,
                          (-40 if d > 0 else 140) if d else 40, (40 if d > 0 else 220) if d else 140, 8)
            stroke(draw, pts, 0.6, HARPY_TIP)


# ===================================================================
# GRIFFIN (95) -- an eagle's head on a lion, great wings raised
# ===================================================================

GRF_BODY = (226, 186, 120)
GRF_HEAD = (246, 196, 84)
GRF_WING = (150, 92, 54)
GRF_BEAK = (74, 72, 86)
GRF_TIP = (70, 176, 190)
GRF_TALON = (250, 206, 90)


def _griffin_wing(draw, x, y, side, flap):
    tip = (x + side * 13.0, y - 14.0 - flap * 3.0)
    pts = [(x, y), (x + side * 4.0, y - 8.0 - flap), tip, (tip[0] + side * 0.6, tip[1] + 5.0), (tip[0] - side * 1.4, tip[1] + 6.4),
           (tip[0] - side * 0.4, tip[1] + 10.0), (tip[0] - side * 3.4, tip[1] + 10.4), (x + side * 4.0, y + 4.0)]
    cel(draw, Poly(pts), GRF_WING, sh=(0.8, 0.8))
    cel(draw, Poly([(x + side * 0.6, y - 1.0), (x + side * 4.0, y - 7.0 - flap), (x + side * 7.0, y - 6.0), (x + side * 3.0, y + 1.0)]),
        lit(GRF_WING, 0.8), sh=None, line=False)
    cel(draw, Poly([pts[2], pts[3], pts[4], pts[5], pts[6], (tip[0] - side * 5.0, tip[1] + 6.0)]), GRF_TIP, sh=None, line=False)
    for k in (0.0, 1.8, 3.6):
        stroke(draw, [(tip[0] - side * (0.4 + k), tip[1] + 2.0 + k), (tip[0] - side * (1.6 + k), tip[1] + 8.0 + k * 0.4)], 0.5, shade(GRF_WING, 1.3))


def draw_griffin(draw, ox, oy, direction, frame):
    d, back, base, cx, ph = creature_setup(ox, oy, direction, frame)
    flap = [0.0, 1.0, 0.0, 1.0][frame]
    sway = [0.0, 1.0, 0.0, -1.0][frame]
    if d:
        cel(draw, Limb([(cx - d * 9.0, base - 13.0), (cx - d * 13.0, base - 15.0 + sway), (cx - d * 14.0, base - 19.0)], [1.2, 1.0, 0.6]), GRF_BODY, sh=None)
        cel(draw, Ell(cx - d * 14.0, base - 19.6, 1.8, 2.0), GRF_WING, sh=None)
        _griffin_wing(draw, cx - d * 2.0, base - 19.0, -d, flap * 0.6)
        quad_legs(draw, cx, base, d, ph, GRF_BODY, 5.6, -6.4, base - 10.0, paw=GRF_TALON, w=2.0)
        body = Poly(rot_pts(arc_pts(cx - d * 1.0, base - 13.0, 10.4, 6.2, 0, 360)[:-1], cx, base - 13.0, -d * 6))
        cel(draw, body, GRF_BODY, sh=(1.2, 1.2))
        blob(draw, [Ell(cx + d * 6.0, base - 15.0, 4.4, 5.0)], GRF_HEAD, sh=(0.6, 0.8))
        _griffin_wing(draw, cx + d * 0.0, base - 19.0, -d, flap)
        hx, hy = cx + d * 8.0, base - 24.0
        cel(draw, Ell(hx, hy, 8.0, 7.6), GRF_HEAD, sh=(0.8, 0.8))
        for (u, v, a) in ((-6.0, -4.0, 200), (-7.0, 1.0, 180)):
            x, y = hx + d * u, hy + v
            ang = a if d > 0 else 180 - a
            Poly([(x - 1.6 * math.sin(math.radians(ang)), y + 1.6 * math.cos(math.radians(ang))),
                  (x + math.cos(math.radians(ang)) * 4.0, y + math.sin(math.radians(ang)) * 4.0),
                  (x + 1.6 * math.sin(math.radians(ang)), y - 1.6 * math.cos(math.radians(ang)))]).draw(draw, fill=GRF_HEAD)
        beak = Poly([(hx + d * 4.4, hy - 1.6), (hx + d * 9.4, hy - 0.4), (hx + d * 10.6, hy + 2.4), (hx + d * 9.6, hy + 4.4), (hx + d * 8.6, hy + 2.4),
                     (hx + d * 4.8, hy + 2.8)])
        cel(draw, beak, GRF_BEAK, sh=(0.0, 0.6))
        ms_eye(draw, hx + d * 2.4, hy - 1.4, 3.6, 4.2, (240, 170, 40), (float(d), 0.0), "sharp", skin=GRF_HEAD, side=-d)
        return
    # head-on / from behind: wings raised behind on both sides
    for s in (-1, 1):
        _griffin_wing(draw, cx + s * 4.0, base - 18.0, s, flap)
    if back:
        for s in (-1, 1):
            fwd = ph * s
            cel(draw, Limb([(cx + s * 5.0, base - 10.0), (cx + s * 5.2, base - 2.0 - (1.0 if fwd < 0 else 0))], [2.4, 2.2]), GRF_BODY, sh=None)
            draw_paw(draw, cx + s * 5.2, base - 1.2 - (1.0 if fwd < 0 else 0), GRF_BODY, claws=False)
        cel(draw, Ell(cx, base - 13.0, 9.4, 7.0), GRF_BODY, sh=(1.2, 1.2))
        cel(draw, Limb([(cx, base - 12.0), (cx + 2.0 + sway, base - 16.0), (cx + 1.0 + sway, base - 20.0)], [1.2, 1.0, 0.6]), GRF_BODY, sh=None)
        cel(draw, Ell(cx, base - 26.0, 8.4, 7.6), GRF_HEAD, sh=(0.8, 0.8))
        return
    for s in (-1, 1):
        cel(draw, Limb([(cx + s * 7.6, base - 9.0), (cx + s * 7.8, base - 1.8)], [2.0, 1.8]), shade(GRF_BODY, 0.6), sh=None)
    cel(draw, Ell(cx, base - 11.0, 8.6, 6.0), GRF_BODY, sh=(1.2, 1.0))
    for s in (-1, 1):
        fwd = ph * s
        lift = 1.0 if fwd < 0 else 0.0
        cel(draw, Limb([(cx + s * 4.0, base - 10.0), (cx + s * 4.0, base - 1.8 - lift)], [2.2, 1.8]), GRF_TALON, sh=(0.4, 0.0))
        for k in (-1, 0, 1):
            stroke(draw, [(cx + s * 4.0, base - 1.8 - lift), (cx + s * 4.0 + k * 1.4, base - 0.4 - lift)], 0.8, GRF_TALON)
    blob(draw, [Ell(cx, base - 15.0, 6.0, 4.6)], GRF_HEAD, sh=(0.6, 0.8))
    hx, hy = cx, base - 26.0
    blob(draw, [Ell(hx, hy, 9.4, 8.4)] + [Poly([(hx + s * 7.0, hy - 1.0), (hx + s * 12.0, hy + 2.0), (hx + s * 7.6, hy + 4.0)]) for s in (-1, 1)],
         GRF_HEAD, sh=(1.0, 0.9))
    eye_pair(draw, hx - 4.6, hx + 4.6, hy - 1.4, 0, (240, 170, 40), mood="sharp", skin=GRF_HEAD, w=3.8, h=4.4)
    beak = Poly([(hx - 2.8, hy + 0.8), (hx + 2.8, hy + 0.8), (hx + 2.2, hy + 4.4), (hx, hy + 6.8), (hx - 2.2, hy + 4.4)])
    cel(draw, beak, GRF_BEAK, sh=(0.6, 0.4))


# ===================================================================
# ANUBIS (96) -- a black jackal's head, a gold collar, a was-sceptre
# ===================================================================

ANU_SKIN = (46, 46, 70)
ANU_GOLD = (244, 198, 80)
ANU_LAPIS = (64, 110, 206)
ANU_KILT = (246, 244, 236)


def draw_anubis(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=1.06, head=0.94)
    d = r.d
    st_side = _hold_side(r)
    out = 1.6 if not d else 0.4
    if d:
        rig_arms(r, draw, ANU_SKIN, ANU_SKIN, layer="far")
    rig_legs(r, draw, ANU_SKIN, ANU_GOLD, bare=True)
    rig_torso(r, draw, ANU_SKIN)
    cx = r.cx
    rig_robe(r, draw, ANU_KILT, top=r.waist_y + 0.6, hem=r.hip_y + 4.2, flare=2.0, split=True)
    if not r.back and not d:
        cel(draw, Poly([(cx - 2.0, r.waist_y + 1.4), (cx + 2.0, r.waist_y + 1.4), (cx + 1.6, r.hip_y + 4.0), (cx - 1.6, r.hip_y + 4.0)]), ANU_GOLD, sh=None)
    rig_belt(r, draw, ANU_GOLD, buckle=ANU_LAPIS)
    rig_arms(r, draw, ANU_SKIN, ANU_SKIN, layer="near", hands=False)
    for side in _sides(r):
        _, e, w, h = arm_pts(r, side)
        cel(draw, Limb([((e[0] + w[0]) / 2, (e[1] + w[1]) / 2), w], [2.3, 2.3]), ANU_GOLD, sh=(0.4, 0.3))
    # the usekh: a broad collar in gold and lapis bands
    cy0 = r.sh_y - 2.2
    for i, (col, k) in enumerate(((ANU_GOLD, 1.0), (ANU_LAPIS, 0.78), (ANU_GOLD, 0.56))):
        if d:
            shape = Poly(arc_pts(cx + d * 0.6, cy0, 6.0 * k + 1.0, 5.0 * k + 0.8, 0, 180, 14))
        else:
            shape = Poly(arc_pts(cx, cy0, 9.4 * k + 1.0, 6.4 * k + 1.0, 0, 180, 18))
        cel(draw, shape, col, sh=None, lw=0.7)
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    # tall ears
    for s in ((-1, 1) if not d else (-d, d)):
        near = not d or s == -d
        ex = hx + s * 5.4 if not d else hx - d * 2.0 + s * 2.0
        cel(draw, Poly([(ex - 2.6, hy - 5.0), (ex + (s * 1.4 if not d else -d * 1.6), hy - 17.0), (ex + 2.6, hy - 5.0)]),
            ANU_SKIN if near else shade(ANU_SKIN, 0.4), sh=(0.4, 0.4))
        if not r.back:
            Poly([(ex - 1.2, hy - 6.4), (ex + (s * 1.0 if not d else -d * 1.2), hy - 14.0), (ex + 1.2, hy - 6.4)]).draw(draw, fill=ANU_GOLD)
    head_skull(r, draw, ANU_SKIN, ears=False)
    if r.back:
        return
    # the long jackal snout
    if d:
        snout = Poly([(hx + d * 3.0, hy - 1.0), (hx + d * 13.0, hy + 2.0), (hx + d * 13.4, hy + 4.4), (hx + d * 4.0, hy + 6.0)])
        cel(draw, snout, ANU_SKIN, sh=(0.0, 0.8))
        Ell(hx + d * 13.0, hy + 2.6, 1.1, 0.9).draw(draw, fill=VOID)
        stroke(draw, [(hx + d * 5.0, hy + 4.6), (hx + d * 12.0, hy + 4.0)], 0.5, ANU_GOLD)
        e_x = hx + d * 3.0
        Poly([(e_x - d * 1.8, hy - 1.4), (e_x + d * 2.4, hy - 2.2), (e_x + d * 2.0, hy - 0.2), (e_x - d * 1.4, hy + 0.0)]).draw(draw, fill=ANU_GOLD)
        Ell(e_x + d * 0.6, hy - 1.0, 0.8, 0.7).draw(draw, fill=(40, 30, 20))
        stroke(draw, [(e_x + d * 2.2, hy - 1.2), (e_x + d * 4.4, hy - 0.4)], 0.6, ANU_GOLD)
    else:
        snout = Poly([(hx - 3.6, hy + 1.0), (hx + 3.6, hy + 1.0), (hx + 2.4, hy + 9.0), (hx, hy + 10.4), (hx - 2.4, hy + 9.0)])
        cel(draw, snout, ANU_SKIN, sh=(0.8, 0.6))
        Ell(hx, hy + 9.0, 1.4, 1.0).draw(draw, fill=VOID)
        for s in (-1, 1):
            ex = hx + s * 4.6
            Poly([(ex - s * 2.2, hy - 0.4), (ex - s * 0.4, hy - 2.0), (ex + s * 2.4, hy - 2.2), (ex + s * 1.8, hy + 0.2), (ex - s * 1.2, hy + 0.4)]).draw(
                draw, fill=ANU_GOLD)
            Ell(ex, hy - 0.8, 0.8, 0.7).draw(draw, fill=(40, 30, 20))
            stroke(draw, [(ex + s * 2.2, hy - 1.0), (ex + s * 4.2, hy + 0.4)], 0.6, ANU_GOLD)
    # the was-sceptre
    h = hand_at(r, st_side, 0.0, 0.0, out)
    x = h[0] + 0.2
    top = r.head_cy - 6.0
    cel(draw, Limb([(x, r.base_y - 1.0), (x, top)], [0.9, 0.9]), ANU_GOLD, sh=None)
    cel(draw, Limb([(x, top), (x + (2.6 if not d else d * 2.6), top - 2.4), (x + (4.4 if not d else d * 4.4), top - 1.4)], [1.0, 0.9, 0.6]),
        ANU_GOLD, sh=None)
    stroke(draw, [(x - 1.2, r.base_y - 1.0), (x, r.base_y - 2.6), (x + 1.2, r.base_y - 1.0)], 0.7, ANU_GOLD)
    for side in _sides(r):
        rig_hand(r, draw, side, ANU_SKIN, out=out if side == st_side else 0.0)


# ===================================================================
# YOKAI (97) -- a kitsune: fox ears, a fox mask, three tails, foxfire
# ===================================================================

YK_WHITE = (250, 248, 250)
YK_RED = (216, 50, 60)
YK_FOX = (252, 172, 70)
YK_HAIR = (246, 240, 250)
YK_FIRE = ((80, 160, 255), (140, 210, 255), (230, 246, 255))


def draw_yokai(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=0.94)
    d = r.d
    wag = [0.0, 1.0, 0.0, -1.0][frame]
    cx = r.cx
    # fox tails fanned out wide behind her, so they show either side
    angs = (-168, -138, -42, -12) if not d else ((-20, 10, 40) if d < 0 else (-160, -190, -220))
    for i, a in enumerate(angs):
        ang = math.radians(a + wag * 6 * (1 if i % 2 else -1))
        bx, by = cx - (d * 3.0 if d else 0.0), r.hip_y - 1.0
        tip = (bx + math.cos(ang) * 16.0, by + math.sin(ang) * 11.0 - 4.0)
        mid = (bx + math.cos(ang) * 8.0, by + math.sin(ang) * 6.0 - 3.0)
        cel(draw, Limb([(bx, by), mid, tip], [1.8, 3.4, 1.6]), YK_FOX, sh=(0.8, 0.8))
        cel(draw, Ell(tip[0], tip[1], 2.6, 2.4), YK_WHITE, sh=(0.4, 0.4))
    rig_hair(r, draw, YK_HAIR, "long", layer="back")
    if d:
        rig_arms(r, draw, YK_WHITE, SKIN, layer="far", cuff=YK_RED)
    rig_legs(r, draw, YK_RED, (60, 50, 60))
    rig_torso(r, draw, YK_WHITE)
    rig_robe(r, draw, YK_RED, top=r.waist_y + 0.4, hem=r.base_y - 2.6, flare=3.0, split=True)
    rig_belt(r, draw, YK_RED, buckle=GOLD)
    if not r.back:
        cel(draw, Poly([(cx - 2.8 + d * 1.6, r.sh_y - 2.2), (cx + 2.8 + d * 1.6, r.sh_y - 2.2), (cx + d * 1.6, r.sh_y + 3.0)]), YK_RED, sh=None, lw=0.6)
    rig_arms(r, draw, YK_WHITE, SKIN, layer="near", cuff=YK_RED, hands=True)
    rig_head(r, draw, SKIN, hair=YK_HAIR, style="long", eye_color=(236, 170, 40), expression="smirk", mood="sharp")
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    # fox ears
    for s in ((-1, 1) if not d else (-d, d)):
        near = not d or s == -d
        ex = hx + s * 6.4 if not d else hx - d * 1.0 + s * 3.0
        cel(draw, Poly([(ex - 3.0, hy - ry + 3.4), (ex + (s * 1.0 if not d else -d * 1.4), hy - ry - 6.0), (ex + 3.0, hy - ry + 3.4)]),
            YK_HAIR if near else shade(YK_HAIR, 0.6), sh=(0.4, 0.4))
        Poly([(ex - 1.4, hy - ry + 2.4), (ex + (s * 0.8 if not d else -d * 1.0), hy - ry - 3.4), (ex + 1.4, hy - ry + 2.4)]).draw(draw, fill=YK_FOX)
    # the fox mask, pushed up to the side of the head
    if not r.back:
        mx = hx + (7.4 if not d else -d * 3.4)
        my = hy - 5.4
        cel(draw, Poly([(mx - 3.6, my - 2.4), (mx - 2.4, my - 5.4), (mx - 1.0, my - 2.8), (mx + 1.0, my - 2.8), (mx + 2.4, my - 5.4), (mx + 3.6, my - 2.4),
                        (mx + 3.0, my + 1.4), (mx, my + 4.0), (mx - 3.0, my + 1.4)]), YK_WHITE, sh=(0.4, 0.4))
        for s in (-1, 1):
            stroke(draw, [(mx + s * 2.4, my - 0.6), (mx + s * 0.8, my - 0.2)], 0.6, YK_RED)
        Ell(mx, my + 2.6, 0.7, 0.5).draw(draw, fill=YK_RED)
    # foxfire floating round her
    for i in range(2):
        a = math.radians(frame * 90 + i * 180)
        x = cx + math.cos(a) * 14.0
        y = r.sh_y - 4.0 + math.sin(a) * 3.0
        flame(draw, x, y + 3.0, 4.6, 6.4, YK_FIRE, sway=[0, 1, 0, -1][frame])


# ===================================================================
# GOLEM (98) -- stacked stone, glowing runes, boulder fists
# ===================================================================

GOL = (150, 146, 140)
GOL_DK = (104, 100, 98)
GOL_RUNE = (110, 230, 240)
GOL_MOSS = (110, 164, 82)


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


def draw_golem(draw, ox, oy, direction, frame):
    d, back, base, cx, ph = creature_setup(ox, oy, direction, frame)
    glow = [0, 1, 0, 1][frame]
    rune = GOL_RUNE if glow else lit(GOL_RUNE, 0.5)
    # stubby block legs
    for s in ((-1, 1) if not d else (-d, d)):
        near = not d or s == d
        fwd = ph * s if not d else (ph if near else -ph)
        lx = cx + (s * 5.6 if not d else d * fwd * 2.6 + s * 1.0)
        ly = base - (1.2 if fwd < 0 and not d else 0)
        cel(draw, RRect(lx - 4.0, ly - 7.6, lx + 4.0, ly + 0.2, 1.6), GOL_DK if near else shade(GOL_DK, 0.6), sh=(0.6, 0.6))
    # the far arm behind the body in profile
    def arm(s, near):
        col = GOL if near else shade(GOL, 0.6)
        fwd = -ph * s if not d else (-ph if near else ph)
        sx = cx + (s * 12.4 if not d else s * 1.4)
        _stone(draw, sx, base - 22.0, 4.8, 4.0, col)
        fx = sx + (s * 1.6 if not d else d * (fwd * 3.0 + 1.6))
        _stone(draw, (sx + fx) / 2.0, base - 16.4, 3.6, 3.2, col)
        fist_y = base - 9.4 - fwd * 0.8
        _stone(draw, fx, fist_y, 5.4, 5.0, GOL_DK if near else shade(GOL_DK, 0.6))
        if near:
            for k in (-1.8, 0.0, 1.8):
                stroke(draw, [(fx + k, fist_y - 4.0), (fx + k, fist_y - 2.4)], 0.5, shade(GOL_DK, 1.6))

    if d:
        arm(-d, False)
    # the torso: a broad block of stone with a rune circle on the chest
    bw = 11.4 if not d else 9.4
    torso = Poly([(cx - bw + 1.4, base - 26.0), (cx + bw - 1.4, base - 26.0), (cx + bw + 0.6, base - 21.0), (cx + bw - 0.6, base - 8.6),
                  (cx - bw + 0.6, base - 8.6), (cx - bw - 0.6, base - 21.0)])
    cel(draw, torso, GOL, sh=(1.8, 1.4), hi=(1.0, 1.0))
    if not back:
        rcx, rcy = cx + d * 2.6, base - 17.6
        layer = draw.sub()
        Ell(rcx, rcy, 3.6, 3.6).draw(layer, fill=None, outline=rune, width=0.8)
        draw.merge(layer)
        stroke(draw, [(rcx, rcy - 2.4), (rcx, rcy + 2.4)], 0.7, rune)
        stroke(draw, [(rcx - 2.0, rcy - 0.6), (rcx, rcy + 1.0), (rcx + 2.0, rcy - 0.6)], 0.7, rune)
        stroke(draw, [(cx - bw + 2.0, base - 12.0), (cx - bw + 4.4, base - 10.6)], 0.6, shade(GOL, 1.4))
    else:
        stroke(draw, [(cx - 4.0, base - 22.0), (cx + 1.0, base - 17.0), (cx - 1.0, base - 12.0)], 0.8, rune)
    # moss on the shoulders
    blob(draw, [Ell(cx - bw + 2.6, base - 26.0, 3.6, 1.6), Ell(cx + bw - 2.6, base - 26.2, 3.0, 1.4)] if not d else
         [Ell(cx - d * 3.0, base - 26.0, 3.6, 1.6)], GOL_MOSS, sh=(0.0, 0.6))
    # the head: a big squarish stone with a heavy brow and glowing eyes
    hx, hy = cx + d * 2.4, base - 32.4
    head = Poly([(hx - 8.4, hy - 5.6), (hx - 6.0, hy - 8.0), (hx + 6.0, hy - 8.0), (hx + 8.4, hy - 5.6), (hx + 8.8, hy + 3.4),
                 (hx + 6.4, hy + 6.4), (hx - 6.4, hy + 6.4), (hx - 8.8, hy + 3.4)])
    cel(draw, head, GOL, sh=(1.4, 1.2), hi=(0.8, 0.8))
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
        stroke(draw, [(bx0 - 2.4, hy + 4.0), (bx0 + 2.4, hy + 4.0)], 0.7, shade(GOL, 1.8))
    for s in ((-1, 1) if not d else (d,)):
        arm(s, True)


# ===================================================================
# DJINN (99) -- blue skin, a topknot, gold bangles, a smoke tail from a lamp
# ===================================================================

DJ_SKIN = (100, 146, 230)
DJ_VEST = (130, 60, 150)
DJ_SMOKE = (186, 176, 240)
DJ_HAIR = (40, 36, 60)


def draw_djinn(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=1.12, bob=[-1, -2, -2, -1][frame], step=0)
    d = r.d
    wave = [0.0, 1.0, 0.0, -1.0][frame]
    cx = r.cx
    # the lamp on the ground, smoke rising out of it into his waist
    lx, ly = cx + (6.0 if not d else -d * 5.0), r.base_y + 0.6 - r.bob
    cel(draw, Poly([(lx - 5.4, ly - 2.0), (lx + 3.6, ly - 2.0), (lx + 7.4, ly - 4.2), (lx + 8.4, ly - 3.8), (lx + 4.6, ly - 0.4), (lx + 2.4, ly + 0.4),
                    (lx - 4.4, ly + 0.4)]), GOLD, sh=(0.0, 0.6), hi=(0.4, 0.4))
    cel(draw, Ell(lx - 1.0, ly - 2.6, 2.4, 1.0), shade(GOLD, 1.2), sh=None)
    smoke = [Ell(lx + 6.6, ly - 5.4, 1.8, 1.6), Ell(lx + 4.0 + wave, ly - 9.0, 2.6, 2.4), Ell(cx + 1.0 - wave, r.hip_y + 6.0, 3.4, 3.2),
             Ell(cx - 0.6, r.hip_y + 2.0, 5.0, 3.6), Ell(cx, r.hip_y - 1.0, 6.4, 3.6)]
    blob(draw, smoke, DJ_SMOKE, sh=(1.0, 1.0))
    if d:
        rig_arms(r, draw, DJ_SKIN, DJ_SKIN, layer="far", reach=0.4)
    rig_torso(r, draw, DJ_SKIN)
    # an open vest and a sash
    if not r.back:
        for s in ((-1, 1) if not d else (-d,)):
            x0 = cx + s * r.sh_w if not d else cx - d * 3.0
            cel(draw, Poly([(x0, r.sh_y - 1.4), (x0 - s * 3.0 if not d else x0 + d * 3.0, r.sh_y - 1.4), (x0 - s * 1.6 if not d else x0 + d * 1.6, r.waist_y + 1.0),
                            (x0 + s * 0.2, r.waist_y + 1.0)]), DJ_VEST, sh=(0.5, 0.0))
    else:
        cel(draw, RRect(cx - r.sh_w + 0.6, r.sh_y - 1.8, cx + r.sh_w - 0.6, r.waist_y + 1.0, 1.2), DJ_VEST, sh=(0.8, 0.4))
    rig_belt(r, draw, (220, 60, 80), buckle=GOLD)
    rig_arms(r, draw, DJ_SKIN, DJ_SKIN, layer="near", reach=0.4)
    for side in _sides(r):
        _, e, w, h = arm_pts(r, side, 0.4)
        cel(draw, Ell(w[0], w[1], 2.4, 1.4), GOLD, sh=None, lw=0.6)
    rig_head(r, draw, DJ_SKIN, hair=None, eye_color=(250, 214, 80), expression="grin", mood="sharp", blush=False, ears=True)
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    # topknot, goatee, a gold hoop earring
    tk = (hx - d * 1.0, hy - ry + 0.6)
    cel(draw, Ell(tk[0], tk[1], 2.6, 2.0), DJ_HAIR, sh=None)
    cel(draw, Limb([tk, (tk[0] + 3.0 - d * 3.0, tk[1] - 4.0 + wave), (tk[0] + 7.0 - d * 7.0, tk[1] - 3.0 - wave), (tk[0] + 9.0 - d * 9.4, tk[1] + 1.0)],
                   [1.6, 1.8, 1.2, 0.3]), DJ_HAIR, sh=None)
    if not r.back:
        bx = hx + d * 5.4
        cel(draw, Poly([(bx - 1.4, hy + ry - 1.6), (bx + 1.4, hy + ry - 1.6), (bx, hy + ry + 3.4)]), DJ_HAIR, sh=None, lw=0.6)
        for s in ((-1, 1) if not d else (-d,)):
            ex = hx + s * (rx - 0.6) if not d else hx - d * 3.0
            layer = draw.sub()
            Ell(ex, hy + 5.4, 1.4, 1.6).draw(layer, fill=None, outline=GOLD, width=0.7)
            draw.merge(layer)
    if frame % 2 == 0:
        sparkle(draw, cx + (12.0 if not d else d * 10.0), r.sh_y - 2.0, 2.0, (255, 246, 190))


# ===================================================================
# FENRIR (100) -- a huge dark wolf in a frenzy, broken chains, red eyes
# ===================================================================

FEN = (70, 66, 84)
FEN_MANE = (104, 92, 118)
FEN_EYE = (255, 60, 60)
FEN_CHAIN = (176, 182, 200)


def _fen_head(draw, hx, hy, d, frame, back=False):
    for s in ((-1, 1) if not d else (-d, d)):
        near = not d or s == -d
        ex = hx + s * 6.4 if not d else hx - d * 2.0 + s * 2.4
        col = FEN if near else shade(FEN, 0.6)
        cel(draw, Poly([(ex - 3.0, hy - 5.0), (ex + (s * 1.4 if not d else -d * 1.4), hy - 14.0), (ex + 3.0, hy - 5.0)]), col, sh=(0.5, 0.5))
    # a spiky mane round the head
    spikes = []
    for i in range(10 if not d else 7):
        a = math.radians((200 + i * 16) if not d else ((120 + i * 20) if d < 0 else (60 - i * 20 + 360) % 360))
        spikes.append(Poly([(hx + math.cos(a - 0.3) * 8.0, hy + 2.0 + math.sin(a - 0.3) * 8.0),
                            (hx + math.cos(a) * 14.0, hy + 2.0 + math.sin(a) * 13.0),
                            (hx + math.cos(a + 0.3) * 8.0, hy + 2.0 + math.sin(a + 0.3) * 8.0)]))
    blob(draw, spikes + [Ell(hx, hy + 2.0, 10.4, 9.4)], FEN_MANE, sh=(1.2, 1.0))
    cel(draw, Ell(hx, hy, 9.4 if not d else 8.6, 8.4), FEN, sh=(1.0, 1.0), hi=(0.6, 0.6))
    if back:
        return
    if d:
        mz = Poly([(hx + d * 3.0, hy + 0.0), (hx + d * 13.0, hy + 1.0), (hx + d * 13.6, hy + 3.6), (hx + d * 12.0, hy + 4.4), (hx + d * 13.0, hy + 7.0),
                   (hx + d * 4.0, hy + 7.4)])
        cel(draw, mz, FEN, sh=(0.0, 0.8))
        Ell(hx + d * 13.0, hy + 1.6, 1.3, 1.0).draw(draw, fill=VOID)
        Poly([(hx + d * 5.0, hy + 4.2), (hx + d * 12.2, hy + 4.4), (hx + d * 12.4, hy + 6.4), (hx + d * 5.4, hy + 6.4)]).draw(draw, fill=(150, 30, 40))
        for t in (6.0, 8.6, 11.0):
            Poly([(hx + d * t, hy + 4.3), (hx + d * (t + 0.9), hy + 4.3), (hx + d * (t + 0.4), hy + 5.8)]).draw(draw, fill=TOOTH)
        Poly([(hx + d * 0.4, hy - 2.0), (hx + d * 4.4, hy - 1.0), (hx + d * 3.8, hy + 0.6), (hx + d * 0.6, hy + 0.0)]).draw(draw, fill=FEN_EYE)
        Ell(hx + d * 2.6, hy - 0.8, 0.5, 0.5).draw(draw, fill=(255, 240, 240))
        return
    cel(draw, Ell(hx, hy + 4.4, 5.6, 4.4), FEN_MANE, sh=(0.6, 0.6))
    Ell(hx, hy + 1.8, 2.0, 1.4).draw(draw, fill=VOID)
    Poly([(hx - 3.6, hy + 5.0), (hx + 3.6, hy + 5.0), (hx + 2.4, hy + 8.4), (hx - 2.4, hy + 8.4)]).draw(draw, fill=(150, 30, 40))
    for s in (-1, 1):
        for t in (1.2, 2.8):
            Poly([(hx + s * t - 0.5, hy + 5.0), (hx + s * t + 0.5, hy + 5.0), (hx + s * t, hy + 6.8)]).draw(draw, fill=TOOTH)
            Poly([(hx + s * t * 0.8 - 0.4, hy + 8.4), (hx + s * t * 0.8 + 0.4, hy + 8.4), (hx + s * t * 0.8, hy + 7.0)]).draw(draw, fill=TOOTH)
        ex = hx + s * 4.4
        Poly([(ex - s * 2.2, hy - 0.4), (ex - s * 0.6, hy - 2.4), (ex + s * 2.4, hy - 2.6), (ex + s * 2.0, hy - 0.4), (ex - s * 1.0, hy + 0.4)]).draw(
            draw, fill=FEN_EYE)
        Ell(ex, hy - 1.2, 0.5, 0.5).draw(draw, fill=(255, 240, 240))


def _shackle(draw, x, y, frame, trail=1):
    cel(draw, RRect(x - 2.6, y - 1.2, x + 2.6, y + 1.4, 0.8), FEN_CHAIN, sh=None, lw=0.6)
    sw = [0.0, 1.0, 0.0, -1.0][frame]
    for i in range(3):
        lx, ly = x + trail * (1.4 + i * 1.8), y + 2.0 + i * 1.6 + sw * i * 0.3
        cel(draw, Ell(lx, ly, 1.1, 0.9 if i % 2 else 1.1), FEN_CHAIN, sh=None, lw=0.5)


def draw_fenrir(draw, ox, oy, direction, frame):
    d, back, base, cx, ph = creature_setup(ox, oy, direction, frame, bob=[0, -2, 0, -2])
    sway = [0.0, 1.0, 0.0, -1.0][frame]
    if d:
        cel(draw, Limb([(cx - d * 9.0, base - 16.0), (cx - d * 14.0, base - 19.0 + sway), (cx - d * 17.0, base - 18.0)], [3.0, 3.4, 1.0]), FEN_MANE, sh=(0.8, 0.8))
        quad_legs(draw, cx, base, d, ph, FEN, 6.0, -7.0, base - 12.0, paw=FEN_MANE, w=2.6)
        body = Poly(rot_pts(arc_pts(cx - d * 1.0, base - 15.0, 12.0, 7.4, 0, 360)[:-1], cx, base - 15.0, -d * 8))
        cel(draw, body, FEN, sh=(1.4, 1.2))
        _shackle(draw, cx + d * 6.4 + d * ph * 2.4, base - 6.0, frame, trail=-d)
        _fen_head(draw, cx + d * 9.0, base - 25.0, d, frame)
        return
    if back:
        for s in (-1, 1):
            fwd = ph * s
            cel(draw, Limb([(cx + s * 5.4, base - 11.0), (cx + s * 5.8, base - 1.8 - (1.0 if fwd < 0 else 0))], [3.0, 2.6]), FEN, sh=(0.6, 0.0))
            draw_paw(draw, cx + s * 5.8, base - 1.2 - (1.0 if fwd < 0 else 0), FEN_MANE, claws=False, r=2.8)
        cel(draw, Ell(cx, base - 14.0, 11.0, 8.4), FEN, sh=(1.4, 1.2))
        cel(draw, Limb([(cx, base - 14.0), (cx + 2.0 + sway, base - 19.0), (cx + 1.0 + sway * 1.6, base - 25.0)], [3.0, 3.6, 1.2]), FEN_MANE, sh=(0.8, 0.8))
        _fen_head(draw, cx, base - 27.0, 0, frame, back=True)
        return
    for s in (-1, 1):
        cel(draw, Limb([(cx + s * 8.6, base - 9.0), (cx + s * 8.8, base - 1.8)], [2.4, 2.2]), shade(FEN, 0.6), sh=None)
    cel(draw, Ell(cx, base - 13.0, 10.0, 7.4), FEN, sh=(1.4, 1.0))
    for s in (-1, 1):
        fwd = ph * s
        lift = 1.0 if fwd < 0 else 0.0
        cel(draw, Limb([(cx + s * 4.6, base - 13.0), (cx + s * 4.6, base - 1.8 - lift)], [3.0, 2.6]), FEN, sh=(0.6, 0.0))
        draw_paw(draw, cx + s * 4.6, base - 1.0 - lift, FEN_MANE, r=2.8)
        _shackle(draw, cx + s * 4.6, base - 7.0 - lift, frame, trail=s)
    _fen_head(draw, cx, base - 27.0, 0, frame)


# ===================================================================
# CHIMERA (101) -- a lion with a fire mane, a goat's head, a snake tail
# ===================================================================

CHI_LION = (232, 170, 84)
CHI_MANE = ((226, 70, 40), (255, 140, 50), (255, 214, 100))
CHI_GOAT = (238, 234, 226)
CHI_SNAKE = (96, 170, 90)


def _goat_head(draw, x, y, d, back=False):
    for s in ((-1, 1) if not d else (-d, d)):
        near = not d or s == -d
        _horn(draw, (x + s * 2.0, y - 3.0), s, (140, 120, 110) if near else (100, 86, 80), 0.6, 0.9, 1.6)
    cel(draw, Ell(x, y, 4.4, 4.0), CHI_GOAT, sh=(0.6, 0.6))
    if back:
        return
    if d:
        cel(draw, Ell(x + d * 3.4, y + 1.4, 2.6, 2.0), CHI_GOAT, sh=(0.0, 0.5))
        Ell(x + d * 1.4, y - 0.8, 0.9, 0.9).draw(draw, fill=VOID)
        cel(draw, Poly([(x + d * 2.4, y + 3.0), (x + d * 4.0, y + 3.0), (x + d * 3.0, y + 6.0)]), CHI_GOAT, sh=None, lw=0.5)
    else:
        for s in (-1, 1):
            Ell(x + s * 1.8, y - 0.6, 0.8, 0.8).draw(draw, fill=VOID)
        cel(draw, Poly([(x - 1.0, y + 3.4), (x + 1.0, y + 3.4), (x, y + 6.4)]), CHI_GOAT, sh=None, lw=0.5)


def _snake_tail(draw, pts, d, frame):
    cel(draw, Limb(pts, [1.8, 1.6, 1.4, 1.4]), CHI_SNAKE, sh=(0.4, 0.4))
    hx, hy = pts[-1]
    k = -d if d else 1
    cel(draw, Ell(hx + k * 1.4, hy - 0.6, 2.6, 2.0), CHI_SNAKE, sh=None)
    Ell(hx + k * 2.0, hy - 1.2, 0.5, 0.5).draw(draw, fill=(255, 220, 60))
    if frame % 2 == 0:
        stroke(draw, [(hx + k * 3.8, hy - 0.2), (hx + k * 5.2, hy + 0.4)], 0.4, (230, 60, 80))


def draw_chimera(draw, ox, oy, direction, frame):
    d, back, base, cx, ph = creature_setup(ox, oy, direction, frame)
    sway = [0.0, 1.0, 0.0, -1.0][frame]
    if d:
        _snake_tail(draw, [(cx - d * 9.0, base - 14.0), (cx - d * 14.0, base - 15.0), (cx - d * 16.0, base - 20.0 + sway), (cx - d * 13.0, base - 24.0)], d, frame)
        quad_legs(draw, cx, base, d, ph, CHI_LION, 5.6, -6.6, base - 11.0, paw=CHI_LION, w=2.2)
        body = Poly(rot_pts(arc_pts(cx - d * 1.0, base - 14.0, 11.0, 6.6, 0, 360)[:-1], cx, base - 14.0, -d * 4))
        cel(draw, body, CHI_LION, sh=(1.2, 1.2))
        # the goat head rising from the back
        cel(draw, Limb([(cx - d * 2.0, base - 18.0), (cx - d * 3.0, base - 24.0)], [2.4, 2.0]), CHI_GOAT, sh=None)
        _goat_head(draw, cx - d * 3.0, base - 27.0, d)
        # lion head with a mane of fire
        hx, hy = cx + d * 8.0, base - 22.0
        flame(draw, hx - d * 2.0, hy + 8.0, 20.0, 20.0, CHI_MANE, sway=sway - d * 3.0)
        cel(draw, Ell(hx, hy, 7.4, 6.8), CHI_LION, sh=(0.8, 0.8), hi=(0.5, 0.5))
        cel(draw, Ell(hx + d * 5.0, hy + 2.0, 3.8, 3.0), lit(CHI_LION, 0.6), sh=(0.0, 0.5))
        Ell(hx + d * 8.0, hy + 1.0, 1.2, 0.9).draw(draw, fill=VOID)
        Poly([(hx + d * 3.4, hy + 3.4), (hx + d * 8.0, hy + 3.4), (hx + d * 7.0, hy + 5.4), (hx + d * 3.8, hy + 5.4)]).draw(draw, fill=(150, 30, 40))
        Poly([(hx + d * 6.0, hy + 3.4), (hx + d * 6.8, hy + 3.4), (hx + d * 6.4, hy + 4.8)]).draw(draw, fill=TOOTH)
        ms_eye(draw, hx + d * 1.6, hy - 1.4, 3.4, 4.0, (200, 60, 30), (float(d), 0.0), "sharp", skin=CHI_LION, side=-d)
        return
    if back:
        for s in (-1, 1):
            fwd = ph * s
            cel(draw, Limb([(cx + s * 5.0, base - 10.0), (cx + s * 5.2, base - 2.0 - (1.0 if fwd < 0 else 0))], [2.4, 2.2]), CHI_LION, sh=None)
            draw_paw(draw, cx + s * 5.2, base - 1.2 - (1.0 if fwd < 0 else 0), CHI_LION, claws=False)
        cel(draw, Ell(cx, base - 14.0, 10.0, 7.4), CHI_LION, sh=(1.2, 1.2))
        _snake_tail(draw, [(cx, base - 12.0), (cx + 3.0 + sway, base - 16.0), (cx + 5.0 + sway, base - 22.0), (cx + 2.0, base - 26.0)], 0, frame)
        cel(draw, Limb([(cx - 2.0, base - 18.0), (cx - 3.0, base - 24.0)], [2.4, 2.0]), CHI_GOAT, sh=None)
        _goat_head(draw, cx - 3.0, base - 27.0, 0, back=True)
        flame(draw, cx + 4.0, base - 18.0, 18.0, 16.0, CHI_MANE, sway=sway)
        return
    # head-on: the snake tail rears over one shoulder, the goat over the other
    _snake_tail(draw, [(cx + 6.0, base - 12.0), (cx + 12.0, base - 16.0 + sway), (cx + 14.0, base - 24.0), (cx + 11.0, base - 30.0)], 0, frame)
    cel(draw, Limb([(cx - 4.0, base - 16.0), (cx - 8.0, base - 24.0)], [2.4, 2.0]), CHI_GOAT, sh=None)
    _goat_head(draw, cx - 9.0, base - 28.0, 0)
    for s in (-1, 1):
        cel(draw, Limb([(cx + s * 7.6, base - 9.0), (cx + s * 7.8, base - 1.8)], [2.0, 1.8]), shade(CHI_LION, 0.6), sh=None)
    cel(draw, Ell(cx, base - 11.0, 8.6, 6.0), CHI_LION, sh=(1.2, 1.0))
    for s in (-1, 1):
        fwd = ph * s
        lift = 1.0 if fwd < 0 else 0.0
        cel(draw, Limb([(cx + s * 4.0, base - 10.0), (cx + s * 4.0, base - 1.8 - lift)], [2.6, 2.2]), CHI_LION, sh=(0.6, 0.0))
        draw_paw(draw, cx + s * 4.0, base - 1.0 - lift, CHI_LION)
    hx, hy = cx, base - 22.0
    flame(draw, hx, hy + 10.0, 26.0, 24.0, CHI_MANE, sway=sway)
    cel(draw, Ell(hx, hy, 8.4, 7.6), CHI_LION, sh=(1.0, 1.0), hi=(0.6, 0.6))
    cel(draw, Ell(hx, hy + 3.4, 4.6, 3.4), lit(CHI_LION, 0.6), sh=(0.5, 0.5))
    Ell(hx, hy + 1.8, 1.6, 1.1).draw(draw, fill=VOID)
    Poly([(hx - 2.6, hy + 4.2), (hx + 2.6, hy + 4.2), (hx + 1.6, hy + 6.2), (hx - 1.6, hy + 6.2)]).draw(draw, fill=(150, 30, 40))
    for s in (-1, 1):
        Poly([(hx + s * 1.4 - 0.4, hy + 4.2), (hx + s * 1.4 + 0.4, hy + 4.2), (hx + s * 1.4, hy + 5.6)]).draw(draw, fill=TOOTH)
    eye_pair(draw, hx - 3.8, hx + 3.8, hy - 1.4, 0, (200, 60, 30), mood="sharp", skin=CHI_LION, w=3.4, h=4.2)


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
