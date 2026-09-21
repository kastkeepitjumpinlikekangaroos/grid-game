#!/usr/bin/env python3
"""Specialist character sprite generators (IDs 102-111).

Ten tradespeople and oddballs, in MapleStory's style, each carrying the tool
of their trade oversized: the alchemist's bubbling flask, the puppeteer's
marionette, the gambler's cards, the blacksmith's glowing hammer, the
pirate's parrot, the chef's frying pan, the musician's guitar, the
astronomer's telescope, the runesmith's floating runestone -- and the
shapeshifter, caught halfway through turning into something else.
"""

import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from sprite_base import (
    DOWN, UP, LEFT, RIGHT, GOLD, LEATHER, STEEL, WOOD, SKIN, SKIN_TAN, SKIN_BROWN, SKIN_PALE, TOOTH,
    HAIR_STYLES, Ell, Limb, Poly, RRect, arc_pts, blade, blob, bolt, cel, cloud, crystal, flame, gem,
    generate_character, hand_at, head_face, head_skull, ink, leaf, lit, mix, ms_eye, ms_mouth, rig,
    rig_arms, rig_belt, rig_cape, rig_hair, rig_hand, rig_head, rig_hood, rig_legs, rig_robe, rig_torso,
    shade, sparkle, star, stroke, xform, arm_pts, face_anchor,
)

VOID = (34, 26, 40)


def _sides(r):
    return (-1, 1) if not r.d else (r.d,)


def _hold_side(r):
    return (1 if not r.back else -1) if not r.d else r.d


def _goggles(draw, r, lens=(150, 220, 240), frame_col=(200, 170, 110), y_off=-4.4):
    hx, hy, rx, d = r.hx, r.head_cy, r.head_rx, r.d
    if r.back:
        stroke(draw, [(hx - rx - 0.6, hy + y_off + 0.2), (hx + rx + 0.6, hy + y_off + 0.2)], 1.1, (70, 60, 56))
        return
    stroke(draw, [(hx - rx - 0.6, hy + y_off), (hx + rx + 0.6, hy + y_off)] if not d else
           [(hx - d * 2.0, hy + y_off - 0.2), (hx + d * (rx + 0.6), hy + y_off - 0.4)], 1.1, (70, 60, 56))
    for k in ((-3.2, 3.2) if not d else (d * 4.4,)):
        gx = hx + k + d * 1.4
        cel(draw, Ell(gx, hy + y_off - 0.4, 2.7, 2.3), frame_col, sh=None)
        cel(draw, Ell(gx, hy + y_off - 0.4, 1.7, 1.4), lens, sh=None, line=False)
        Ell(gx - 0.6, hy + y_off - 0.9, 0.5, 0.5).draw(draw, fill=(255, 255, 255))


# ===================================================================
# ALCHEMIST (102) -- goggles up, a belt of vials, a bubbling flask
# ===================================================================

ALC_APRON = (92, 154, 86)
ALC_SHIRT = (240, 236, 226)
ALC_HAIR = (206, 110, 60)
POTIONS = ((120, 230, 100), (250, 110, 150), (110, 180, 255))


def draw_alchemist(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame)
    d = r.d
    hs = _hold_side(r)
    if d:
        rig_arms(r, draw, ALC_SHIRT, SKIN, layer="far")
    rig_legs(r, draw, (104, 90, 80), (110, 76, 52))
    rig_torso(r, draw, ALC_SHIRT)
    cx = r.cx
    # green apron
    if not r.back:
        ap = Poly([(cx - 4.6 + d * 1.4, r.sh_y - 0.6), (cx + 4.6 + d * 1.4, r.sh_y - 0.6), (cx + 5.8 + d * 1.4, r.hip_y + 3.6),
                   (cx - 5.8 + d * 1.4, r.hip_y + 3.6)]) if not d else \
            Poly([(cx + d * 0.6, r.sh_y - 0.6), (cx + d * 4.6, r.sh_y - 0.6), (cx + d * 5.4, r.hip_y + 3.6), (cx - d * 1.0, r.hip_y + 3.6)])
        cel(draw, ap, ALC_APRON, sh=(0.8, 0.6))
        cel(draw, RRect(cx - 2.6 + d * 1.4, r.hip_y - 1.6, cx + 2.6 + d * 1.4, r.hip_y + 1.2, 0.5), shade(ALC_APRON, 0.6), sh=None)
    else:
        stroke(draw, [(cx - 4.0, r.sh_y - 1.0), (cx + 4.0, r.waist_y)], 0.7, ALC_APRON)
        stroke(draw, [(cx + 4.0, r.sh_y - 1.0), (cx - 4.0, r.waist_y)], 0.7, ALC_APRON)
    rig_belt(r, draw, LEATHER, buckle=GOLD)
    if not r.back:
        for i, u in enumerate((-4.6, -1.6, 3.8) if not d else (d * 3.4,)):
            vx = cx + u
            col = POTIONS[i % 3]
            cel(draw, RRect(vx - 1.0, r.waist_y + 2.8, vx + 1.0, r.waist_y + 5.6, 0.6), col, sh=(0.4, 0.0))
            cel(draw, RRect(vx - 0.6, r.waist_y + 2.0, vx + 0.6, r.waist_y + 3.0, 0.3), LEATHER, sh=None)
    rig_arms(r, draw, ALC_SHIRT, SKIN, layer="near", hands=False, reach=0.3)
    rig_head(r, draw, SKIN, hair=ALC_HAIR, style="wild", eye_color=(90, 160, 90), expression="grin", hat=True)
    _goggles(draw, r)
    for side in _sides(r):
        h = rig_hand(r, draw, side, SKIN, reach=0.3)
        if side == hs and not r.back:
            # a round-bottomed flask, bubbling
            fx, fy = h[0] + (1.8 if not d else d * 2.2), h[1] - 3.4
            cel(draw, RRect(fx - 1.1, fy - 5.6, fx + 1.1, fy - 2.4, 0.4), (220, 236, 240), sh=None, line_color=(120, 150, 160))
            cel(draw, Ell(fx, fy, 3.8, 3.6), (220, 236, 240), sh=None, line_color=(120, 150, 160))
            cel(draw, Poly(arc_pts(fx, fy, 3.0, 2.8, -10, 190, 12)), POTIONS[0], sh=None, line=False)
            Ell(fx - 1.4, fy - 1.4, 0.8, 0.8).draw(draw, fill=(255, 255, 255))
            for k in range(2):
                bx = fx + (k - 0.5) * 1.4 + [0, 0.4, 0, -0.4][frame]
                by = fy - 7.4 - ((frame + k * 2) % 4) * 1.4
                cel(draw, Ell(bx, by, 0.9, 0.9), lit(POTIONS[0], 0.6), sh=None, lw=0.4)


# ===================================================================
# PUPPETEER (103) -- a doll-like face, a control bar, a dangling marionette
# ===================================================================

PUP_COAT = (106, 60, 150)
PUP_SKIN = (250, 238, 238)
PUP_HAIR = (70, 50, 96)
PUP_RIBBON = (230, 60, 90)
PUP_DOLL = (214, 170, 116)


def _marionette(draw, x, y, frame):
    sw = [0.0, 1.0, 0.0, -1.0][frame]
    # the doll: jointed wooden body, little red hat
    cel(draw, Limb([(x - 1.4, y + 4.0), (x - 2.0 + sw * 0.4, y + 8.4)], [0.7, 0.6]), PUP_DOLL, sh=None, lw=0.5)
    cel(draw, Limb([(x + 1.4, y + 4.0), (x + 2.0 - sw * 0.4, y + 8.4)], [0.7, 0.6]), PUP_DOLL, sh=None, lw=0.5)
    cel(draw, RRect(x - 2.2, y, x + 2.2, y + 4.6, 1.0), PUP_RIBBON, sh=None, lw=0.5)
    cel(draw, Limb([(x - 2.0, y + 0.6), (x - 4.0, y + 2.6 - sw)], [0.6, 0.5]), PUP_DOLL, sh=None, lw=0.5)
    cel(draw, Limb([(x + 2.0, y + 0.6), (x + 4.0, y + 2.6 + sw)], [0.6, 0.5]), PUP_DOLL, sh=None, lw=0.5)
    cel(draw, Ell(x, y - 2.4, 2.6, 2.6), PUP_DOLL, sh=(0.4, 0.4), lw=0.5)
    Ell(x - 0.9, y - 2.4, 0.4, 0.4).draw(draw, fill=VOID)
    Ell(x + 0.9, y - 2.4, 0.4, 0.4).draw(draw, fill=VOID)
    cel(draw, Poly([(x - 2.2, y - 4.0), (x + 2.2, y - 4.0), (x + 0.4, y - 7.4)]), PUP_RIBBON, sh=None, lw=0.5)


def draw_puppeteer(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=0.94)
    d = r.d
    hs = _hold_side(r)
    style = dict(HAIR_STYLES["bob"], extra="twin")
    rig_hair(r, draw, PUP_HAIR, style, layer="back")
    if d:
        rig_arms(r, draw, PUP_COAT, (250, 250, 252), layer="far")
    rig_legs(r, draw, (60, 50, 76), (40, 34, 50))
    rig_robe(r, draw, PUP_COAT, trim=GOLD, flare=3.0, hem=r.hip_y + 4.6, split=True)
    cx = r.cx
    if not r.back:
        for k in range(3):
            Ell(cx + d * 1.8, r.sh_y + 0.6 + k * 2.4, 0.6, 0.6).draw(draw, fill=GOLD)
        # a big bow at the collar
        bx = cx + d * 1.6
        for s in (-1, 1):
            cel(draw, Poly([(bx, r.sh_y - 1.6), (bx + s * 3.6, r.sh_y - 3.6), (bx + s * 3.6, r.sh_y + 0.4)]), PUP_RIBBON, sh=None, lw=0.6)
        cel(draw, Ell(bx, r.sh_y - 1.6, 1.0, 1.0), lit(PUP_RIBBON, 0.8), sh=None, lw=0.5)
    rig_belt(r, draw, shade(PUP_COAT, 1.6), buckle=GOLD)
    rig_arms(r, draw, PUP_COAT, (250, 250, 252), layer="near", hands=False, reach=0.6)
    rig_head(r, draw, PUP_SKIN, hair=PUP_HAIR, style=style, eye_color=(170, 70, 170), expression="smile", mood="calm")
    # doll-like round blush, and a stitch at the corner of the smile
    if not r.back:
        e1, e2, ey, mx, my = face_anchor(r)
        for ex in ((e1, e2) if not d else (e1,)):
            cel(draw, Ell(ex + (1 if ex > r.hx else -1) * 2.4, ey + 3.6, 1.5, 1.5), (255, 170, 190), sh=None, line=False)
        stroke(draw, [(mx + 1.6, my - 0.4), (mx + 2.4, my + 0.8)], 0.4, shade(PUP_SKIN, 2.0))
    for side in _sides(r):
        h = rig_hand(r, draw, side, (250, 250, 252), reach=0.6)
        if side == hs and not r.back:
            # the control bar and strings down to the marionette
            bx0, by0 = h[0] + (2.0 if not d else d * 2.4), h[1] - 3.0
            cel(draw, Limb([(bx0 - 4.0, by0), (bx0 + 4.0, by0)], [0.8, 0.8]), WOOD, sh=None)
            cel(draw, Limb([(bx0, by0 - 2.6), (bx0, by0 + 2.6)], [0.8, 0.8]), WOOD, sh=None)
            px, py = bx0 + (1.0 if not d else d * 1.6), r.hip_y + 3.0
            for (sx, tx) in ((-4.0, -3.4), (4.0, 3.4), (0.0, 0.0)):
                stroke(draw, [(bx0 + sx, by0), (px + tx, py + (0.8 if sx else -4.8))], 0.3, (236, 236, 244))
            _marionette(draw, px, py, frame)


# ===================================================================
# GAMBLER (104) -- a top hat with a card in the band, vest and bow tie
# ===================================================================

GAM_SUIT = (46, 42, 56)
GAM_RED = (200, 36, 52)
GAM_HAIR = (240, 214, 150)


def draw_gambler(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=0.98, head=0.96)
    d = r.d
    hs = _hold_side(r)
    if d:
        rig_arms(r, draw, GAM_SUIT, (250, 250, 252), layer="far")
    rig_legs(r, draw, GAM_SUIT, (30, 26, 34))
    rig_torso(r, draw, GAM_SUIT)
    cx = r.cx
    if not r.back:
        vx = cx + d * 1.6
        cel(draw, Poly([(vx - 3.4, r.sh_y - 1.8), (vx + 3.4, r.sh_y - 1.8), (vx + 3.0, r.waist_y + 1.8), (vx, r.waist_y + 3.0),
                        (vx - 3.0, r.waist_y + 1.8)]), GAM_RED, sh=(0.6, 0.0))
        cel(draw, Poly([(vx - 1.6, r.sh_y - 2.2), (vx + 1.6, r.sh_y - 2.2), (vx, r.sh_y + 0.8)]), (250, 250, 252), sh=None, lw=0.5)
        for s in (-1, 1):
            cel(draw, Poly([(vx, r.sh_y - 1.4), (vx + s * 2.4, r.sh_y - 2.6), (vx + s * 2.4, r.sh_y - 0.2)]), VOID, sh=None, lw=0.4)
        for k in range(2):
            Ell(vx, r.sh_y + 2.0 + k * 2.2, 0.5, 0.5).draw(draw, fill=GOLD)
        # a gold watch chain
        stroke(draw, [(vx - 2.0, r.waist_y - 0.6), (vx - 0.4, r.waist_y + 0.6), (vx + 2.0, r.waist_y - 0.4)], 0.4, GOLD)
    rig_arms(r, draw, GAM_SUIT, (250, 250, 252), layer="near", hands=False, reach=0.4)
    rig_head(r, draw, SKIN, hair=GAM_HAIR, style="swept", eye_color=(200, 60, 70), expression="smirk", mood="calm", hat=True)
    # top hat, a playing card tucked in the band
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    cx0 = hx - d * 0.6
    cel(draw, Ell(cx0 + d * 0.8, hy - 7.4, 12.6 if not d else 11.0, 3.4), GAM_SUIT, sh=(0.0, 1.0))
    crown = RRect(cx0 - 7.4, hy - 20.0, cx0 + 7.4, hy - 7.6, 1.2)
    cel(draw, crown, GAM_SUIT, sh=(1.6, 0.6), hi=(0.6, 0.6))
    cel(draw, RRect(cx0 - 7.4, hy - 11.0, cx0 + 7.4, hy - 8.6, 0.4), GAM_RED, sh=None)
    if not r.back:
        kx = cx0 + (4.0 if not d else d * 3.0)
        card = Poly(xform([(-1.8, -2.6), (1.8, -2.6), (1.8, 2.6), (-1.8, 2.6)], kx, hy - 12.4, 12))
        cel(draw, card, (252, 252, 252), sh=None, lw=0.5)
        cel(draw, Poly([(kx, hy - 13.8), (kx + 1.0, hy - 12.4), (kx, hy - 11.0), (kx - 1.0, hy - 12.4)]), GAM_RED, sh=None, line=False)
    for side in _sides(r):
        h = rig_hand(r, draw, side, (250, 250, 252), reach=0.4)
        if side == hs and not r.back:
            for j, a in enumerate((-50, -25, 0, 25)):
                ang = -90 + a + (0 if not d else d * 24)
                c = xform([(4.8, 0.0)], h[0], h[1] - 1.0, ang)[0]
                card = Poly(xform([(-2.2, -1.6), (2.2, -1.6), (2.2, 1.6), (-2.2, 1.6)], c[0], c[1], ang + 90))
                cel(draw, card, (252, 252, 252), sh=None, lw=0.5)
                Poly([(c[0], c[1] - 0.9), (c[0] + 0.7, c[1]), (c[0], c[1] + 0.9), (c[0] - 0.7, c[1])]).draw(
                    draw, fill=GAM_RED if j % 2 == 0 else VOID)
    # a die tumbling beside him
    if not r.back:
        dx, dy = r.cx - (12.0 if not d else d * 10.0), r.waist_y - 2.0 - [0, 1.4, 2.0, 1.4][frame]
        die = Poly(xform([(-2.0, -2.0), (2.0, -2.0), (2.0, 2.0), (-2.0, 2.0)], dx, dy, frame * 22.5))
        cel(draw, die, (252, 252, 252), sh=None, lw=0.5)
        Ell(dx, dy, 0.5, 0.5).draw(draw, fill=GAM_RED)


# ===================================================================
# BLACKSMITH (105) -- a leather apron, bare arms, a hammer glowing hot
# ===================================================================

BS_SHIRT = (84, 84, 96)
BS_APRON = (150, 96, 58)
BS_BAND = (200, 60, 50)
BS_HOT = (255, 150, 60)


def draw_blacksmith(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=1.22)
    d = r.d
    hs = _hold_side(r)
    glow = [0, 1, 0, 1][frame]
    if d:
        rig_arms(r, draw, SKIN_TAN, SKIN_TAN, layer="far")
    rig_legs(r, draw, (90, 84, 96), (70, 56, 46), width=1.08)
    rig_torso(r, draw, BS_SHIRT)
    cx = r.cx
    if not r.back:
        ap = Poly([(cx - 4.2 + d * 1.4, r.sh_y - 1.4), (cx + 4.2 + d * 1.4, r.sh_y - 1.4), (cx + 6.4 + d * 1.4, r.hip_y + 4.4),
                   (cx - 6.4 + d * 1.4, r.hip_y + 4.4)]) if not d else \
            Poly([(cx + d * 0.6, r.sh_y - 1.4), (cx + d * 4.8, r.sh_y - 1.0), (cx + d * 5.8, r.hip_y + 4.4), (cx - d * 1.4, r.hip_y + 4.4)])
        cel(draw, ap, BS_APRON, sh=(0.8, 0.6))
        cel(draw, RRect(cx - 2.0 + d * 1.4, r.sh_y + 1.6, cx + 2.0 + d * 1.4, r.sh_y + 4.0, 0.4), shade(BS_APRON, 0.6), sh=None)
    else:
        for s in (-1, 1):
            stroke(draw, [(cx + s * 4.0, r.sh_y - 1.4), (cx - s * 3.0, r.waist_y)], 0.7, BS_APRON)
    rig_belt(r, draw, (80, 60, 44), buckle=(200, 200, 210))
    rig_arms(r, draw, SKIN_TAN, SKIN_TAN, layer="near", hands=False)
    for side in _sides(r):
        sx, sy = r.shoulder(side)
        cel(draw, Ell(sx + (side * 0.6 if not d else 0), sy + 0.4, 2.6, 2.0), BS_SHIRT, sh=(0.4, 0.4))
    head_skull(r, draw, SKIN_TAN)
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    if not r.back:
        head_face(r, draw, SKIN_TAN, iris=(90, 70, 50), mood="bright", mouth="grin", brows=True, brow_color=(60, 44, 40))
        # a short dark beard along the jaw
        if d:
            blob(draw, [Ell(hx + d * 4.4, hy + 8.4, 4.4, 3.4), Ell(hx + d * 1.0, hy + 7.4, 3.0, 3.0)], (70, 50, 44), sh=(0.6, 0.6))
        else:
            blob(draw, [Ell(hx, hy + 9.0, 6.0, 3.0), Ell(hx - 6.0, hy + 6.6, 2.6, 3.0), Ell(hx + 6.0, hy + 6.6, 2.6, 3.0)],
                 (70, 50, 44), sh=(0.6, 0.6))
        ms_mouth(draw, hx + d * 6.0 if d else hx, hy + 7.6, "grin", SKIN_TAN, 0.9)
    rig_hair(r, draw, (70, 50, 44), "short", hat=True, skin=SKIN_TAN)
    # a red headband, knotted behind
    band = RRect(hx - rx - 0.6, hy - 5.4, hx + rx + 0.6, hy - 2.6, 1.2)
    cel(draw, band, BS_BAND, sh=(0.0, 0.6))
    kx = hx + (rx if not d else -d * rx)
    cel(draw, Limb([(kx, hy - 4.0), (kx + (3.0 if not d else -d * 3.0), hy - 2.0 + [0, 1, 0, -1][frame]), (kx + (4.0 if not d else -d * 4.4), hy + 1.6)],
                   [1.0, 0.9, 0.4]), BS_BAND, sh=None)
    # the smithing hammer, its head glowing from the forge
    h = hand_at(r, hs)
    ang = -90 + (hs * 20 if not d else d * 30)
    shaft = xform([(-2.4, 0.0), (11.6, 0.0)], h[0], h[1], ang)
    cel(draw, Limb(shaft, [1.0, 1.0]), WOOD, sh=None)
    hd = xform([(9.0, -3.6), (14.6, -3.6), (14.6, 3.6), (9.0, 3.6)], h[0], h[1], ang)
    cel(draw, Poly(hd), (110, 110, 124), sh=None, hi=None)
    face_ = xform([(13.0, -3.6), (14.6, -3.6), (14.6, 3.6), (13.0, 3.6)], h[0], h[1], ang)
    cel(draw, Poly(face_), BS_HOT if glow else lit(BS_HOT, 0.6), sh=None, line=False)
    if glow:
        p = xform([(15.4, 0.0)], h[0], h[1], ang)[0]
        sparkle(draw, p[0] + 1.0, p[1] - 1.0, 2.0, (255, 220, 120))
    for side in _sides(r):
        rig_hand(r, draw, side, SKIN_TAN)


# ===================================================================
# PIRATE (106) -- tricorn and skull, eyepatch, red coat, a parrot
# ===================================================================

PIR_COAT = (196, 48, 50)
PIR_HAT = (40, 36, 48)
PIR_SHIRT = (246, 244, 236)
PIR_STRIPE = (60, 90, 170)
PARROT = (60, 190, 90)


def _parrot(draw, x, y, d, frame):
    k = -1 if d > 0 else 1
    flap = [0, 1, 0, 1][frame]
    cel(draw, Poly([(x + k * 1.0, y + 1.0), (x + k * 5.0, y + 6.0), (x + k * 3.0, y + 6.4), (x - k * 0.4, y + 2.4)]), (240, 60, 60), sh=None, lw=0.5)
    cel(draw, Ell(x, y, 2.8, 3.4), PARROT, sh=(0.4, 0.4), lw=0.6)
    cel(draw, Poly([(x - k * 1.0, y - 1.0), (x + k * 3.4, y - 2.0 - flap * 1.4), (x + k * 2.6, y + 2.0)]), (80, 150, 240), sh=None, lw=0.5)
    cel(draw, Ell(x - k * 0.6, y - 3.6, 2.2, 2.0), PARROT, sh=None, lw=0.6)
    cel(draw, Poly([(x - k * 2.4, y - 4.0), (x - k * 4.0, y - 2.6), (x - k * 2.4, y - 2.0)]), (252, 200, 60), sh=None, lw=0.4)
    Ell(x - k * 1.2, y - 4.0, 0.5, 0.5).draw(draw, fill=VOID)


def draw_pirate(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=1.04)
    d = r.d
    hs = _hold_side(r)
    if d:
        rig_arms(r, draw, PIR_COAT, SKIN_TAN, layer="far", cuff=GOLD)
    rig_legs(r, draw, (60, 56, 70), (50, 40, 44))
    rig_torso(r, draw, PIR_SHIRT)
    cx = r.cx
    if not r.back:
        for k in range(3):
            y = r.sh_y + 1.0 + k * 2.4
            stroke(draw, [(cx - 3.0 + d * 1.6, y), (cx + 3.0 + d * 1.6, y)], 0.8, PIR_STRIPE)
    # the long red coat, open at the front
    if d:
        coat = Poly([(cx - d * 3.8, r.sh_y - 2.4), (cx + d * 1.0, r.sh_y - 2.4), (cx + d * 0.4, r.hip_y + 5.0), (cx - d * 6.4, r.hip_y + 5.0)])
        cel(draw, coat, PIR_COAT, sh=(0.8, 0.6))
    elif r.back:
        cel(draw, Poly([(cx - r.sh_w + 0.6, r.sh_y - 2.4), (cx + r.sh_w - 0.6, r.sh_y - 2.4), (cx + r.sh_w + 2.4, r.hip_y + 5.0),
                        (cx - r.sh_w - 2.4, r.hip_y + 5.0)]), PIR_COAT, sh=(1.2, 0.6))
    else:
        for s in (-1, 1):
            cel(draw, Poly([(cx + s * 2.8, r.sh_y - 2.4), (cx + s * r.sh_w, r.sh_y - 1.8), (cx + s * (r.sh_w + 2.6), r.hip_y + 5.0),
                            (cx + s * 3.4, r.hip_y + 5.0)]), PIR_COAT, sh=(0.8, 0.6), regions=[(Poly([(cx + s * 2.8, r.sh_y - 2.4), (cx + s * 3.6, r.sh_y - 2.4),
                                                                                                   (cx + s * 4.2, r.hip_y + 5.0), (cx + s * 3.4, r.hip_y + 5.0)]), GOLD)])
    rig_belt(r, draw, (60, 44, 40), buckle=GOLD)
    rig_arms(r, draw, PIR_COAT, SKIN_TAN, layer="near", hands=False, cuff=GOLD)
    head_skull(r, draw, SKIN_TAN)
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    if not r.back:
        head_face(r, draw, SKIN_TAN, iris=(80, 60, 40), mood="sharp", mouth="grin", brows=True, brow_color=(50, 36, 30))
        # the eyepatch over one eye
        e1, e2, ey, _, _ = face_anchor(r)
        ex = e2 if not d else e1
        cel(draw, Ell(ex, ey + 0.4, 2.8, 2.6), VOID, sh=None)
        stroke(draw, [(hx - rx + 0.6, hy - 3.6), (ex, ey), (hx + rx - 0.4, hy - 1.6)] if not d else [(ex - d * 3.0, ey - 2.4), (ex, ey), (hx - d * 3.0, hy - 3.0)],
               0.6, VOID)
    rig_hair(r, draw, (60, 44, 36), "long", hat=True, skin=SKIN_TAN)
    # tricorn: a brim folded up in three points, a white skull on the front
    cx0 = hx - d * 0.6
    by = hy - 6.0
    if d:
        tri = Poly([(cx0 + d * 13.0, by + 0.6), (cx0 + d * 6.0, by - 4.4), (cx0 - d * 4.0, by - 5.4), (cx0 - d * 12.0, by - 1.0), (cx0 - d * 8.0, by + 1.6),
                    (cx0 + d * 6.0, by + 1.8)])
    else:
        tri = Poly([(cx0 - 15.4, by - 3.0), (cx0 - 7.0, by - 1.8), (cx0, by + 1.4), (cx0 + 7.0, by - 1.8), (cx0 + 15.4, by - 3.0), (cx0 + 11.0, by - 7.4),
                    (cx0 + 4.0, by - 9.4), (cx0 - 4.0, by - 9.4), (cx0 - 11.0, by - 7.4)])
    crown = Poly(arc_pts(cx0, by - 3.0, 7.6, 7.4, 180, 360, 20))
    cel(draw, crown, PIR_HAT, sh=(1.2, 0.6))
    cel(draw, tri, PIR_HAT, sh=(0.0, 1.0))
    stroke(draw, [(cx0 - 12.0, by - 3.2), (cx0, by - 0.4), (cx0 + 12.0, by - 3.2)] if not d else [(cx0 - d * 9.0, by - 1.0), (cx0 + d * 10.0, by - 0.8)],
           0.6, GOLD)
    if not r.back:
        sx = cx0 + d * 3.0
        cel(draw, Ell(sx, by - 5.0, 1.8, 1.6), (246, 244, 236), sh=None, lw=0.4)
        for s in (-1, 1):
            Ell(sx + s * 0.7, by - 5.0, 0.4, 0.4).draw(draw, fill=VOID)
    # the parrot on his shoulder
    ps = -hs if not d else -d
    sx, sy = r.shoulder(ps)
    _parrot(draw, sx + (ps * 1.2 if not d else -d * 1.0), sy - 4.4, d if d else -ps, frame)
    # a cutlass
    h = hand_at(r, hs)
    ang = 90 + (hs * 30 if not d else -d * 40)
    blade(draw, h[0], h[1], ang, length=11.0, width=1.7, curve=-0.7, hilt=GOLD, grip=(60, 44, 40), guard=2.4, flip=hs if not d else -d)
    for side in _sides(r):
        rig_hand(r, draw, side, SKIN_TAN)


# ===================================================================
# CHEF (107) -- a towering toque, a double-breasted jacket, a frying pan
# ===================================================================

CHEF_WHITE = (250, 250, 252)
CHEF_RED = (220, 56, 60)


def draw_chef(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=1.08, head=0.94)
    d = r.d
    hs = _hold_side(r)
    if d:
        rig_arms(r, draw, CHEF_WHITE, SKIN, layer="far")
    rig_legs(r, draw, (70, 70, 84), (40, 36, 44))
    rig_torso(r, draw, CHEF_WHITE)
    cx = r.cx
    if not r.back:
        for k in range(3):
            for s in ((-1, 1) if not d else (1,)):
                Ell(cx + s * 2.0 + d * 2.0, r.sh_y + 1.4 + k * 2.4, 0.6, 0.6).draw(draw, fill=(200, 200, 210))
        # a red neckerchief
        cel(draw, Poly([(cx - 3.6 + d * 1.4, r.sh_y - 2.4), (cx + 3.6 + d * 1.4, r.sh_y - 2.4), (cx + d * 1.4, r.sh_y + 1.6)]), CHEF_RED, sh=None, lw=0.6)
    # an apron
    if not r.back:
        cel(draw, Poly([(cx - 5.4 + d, r.waist_y + 0.6), (cx + 5.4 + d, r.waist_y + 0.6), (cx + 6.0 + d, r.hip_y + 4.0), (cx - 6.0 + d, r.hip_y + 4.0)]) if not d else
            Poly([(cx - d * 0.4, r.waist_y + 0.6), (cx + d * 4.8, r.waist_y + 0.6), (cx + d * 5.4, r.hip_y + 4.0), (cx - d * 1.4, r.hip_y + 4.0)]),
            (236, 236, 240), sh=(0.6, 0.4))
    rig_arms(r, draw, CHEF_WHITE, SKIN, layer="near", hands=False)
    head_skull(r, draw, SKIN)
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    if not r.back:
        head_face(r, draw, SKIN, iris=(90, 70, 60), mood="closed" if frame in (1, 3) else "bright", mouth="smile", brows=True,
                  brow_color=(90, 60, 50))
        # a curled moustache
        mx = hx + d * 6.0 if d else hx
        for s in ((-1, 1) if not d else (d, -d)):
            cel(draw, Limb([(mx, hy + 6.4), (mx + s * 2.4, hy + 6.8), (mx + s * 3.8, hy + 5.6)], [0.9, 0.9, 0.4]), (90, 60, 50), sh=None)
    rig_hair(r, draw, (120, 80, 56), "short", hat=True, skin=SKIN)
    # the toque: a band and a tall puffed crown
    cx0 = hx - d * 0.6
    band = RRect(cx0 - rx + 0.6, hy - 8.4, cx0 + rx - 0.6, hy - 4.2, 1.4)
    puff = [Ell(cx0 - 4.4, hy - 13.0, 5.4, 5.0), Ell(cx0 + 4.4, hy - 13.0, 5.4, 5.0), Ell(cx0, hy - 16.4, 6.4, 5.6),
            RRect(cx0 - 8.4, hy - 13.0, cx0 + 8.4, hy - 7.4, 1.0)]
    blob(draw, puff, CHEF_WHITE, sh=(1.4, 1.2), tone=(214, 218, 232))
    cel(draw, band, CHEF_WHITE, sh=(0.0, 0.8), tone=(214, 218, 232))
    for k in (-3.0, 0.0, 3.0):
        stroke(draw, [(cx0 + k, hy - 8.0), (cx0 + k * 1.2, hy - 11.0)], 0.4, (200, 204, 222))
    # a frying pan with an egg in it
    h = hand_at(r, hs)
    ang = -40 if hs > 0 else -140
    if d:
        ang = -20 if d > 0 else -160
    handle = xform([(-1.0, 0.0), (6.0, 0.0)], h[0], h[1], ang)
    cel(draw, Limb(handle, [0.9, 0.9]), (60, 56, 64), sh=None)
    pc = xform([(10.4, 0.0)], h[0], h[1], ang)[0]
    cel(draw, Ell(pc[0], pc[1], 5.0, 3.2), (70, 68, 80), sh=(0.0, 0.8))
    if not r.back:
        cel(draw, Ell(pc[0], pc[1] - 0.4, 3.4, 2.0), (250, 250, 250), sh=None, lw=0.5)
        Ell(pc[0] + 0.4, pc[1] - 0.6, 1.3, 1.0).draw(draw, fill=(255, 196, 60))
    for side in _sides(r):
        rig_hand(r, draw, side, SKIN)


# ===================================================================
# MUSICIAN (108) -- a rocker: dyed spiky hair, studded jacket, a flying-V
# ===================================================================

MUS_JACKET = (48, 44, 58)
MUS_HAIR = (255, 110, 170)
MUS_GUITAR = (230, 46, 70)


def draw_musician(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=0.98)
    d = r.d
    strum = [0.0, 1.0, 0.0, 1.0][frame]
    if d:
        rig_arms(r, draw, MUS_JACKET, SKIN, layer="far", reach=0.6)
    rig_legs(r, draw, (70, 76, 120), (40, 36, 46))
    rig_torso(r, draw, (240, 240, 244))
    cx = r.cx
    # the leather jacket over a white tee, studs on the shoulders
    if not r.back and not d:
        for s in (-1, 1):
            cel(draw, Poly([(cx + s * 2.6, r.sh_y - 2.4), (cx + s * r.sh_w, r.sh_y - 1.8), (cx + s * (r.hip_w + 0.4), r.hip_y + 0.8),
                            (cx + s * 3.0, r.hip_y + 0.8)]), MUS_JACKET, sh=(0.6, 0.4))
    elif d:
        cel(draw, Poly([(cx - d * 3.8, r.sh_y - 2.4), (cx + d * 1.4, r.sh_y - 2.4), (cx + d * 1.0, r.hip_y + 0.8), (cx - d * 4.4, r.hip_y + 0.8)]),
            MUS_JACKET, sh=(0.6, 0.4))
    else:
        cel(draw, Poly([(cx - r.sh_w + 0.6, r.sh_y - 2.4), (cx + r.sh_w - 0.6, r.sh_y - 2.4), (cx + r.hip_w + 0.4, r.hip_y + 0.8),
                        (cx - r.hip_w - 0.4, r.hip_y + 0.8)]), MUS_JACKET, sh=(0.8, 0.4))
        star(draw, cx, r.sh_y + 3.0, 2.8, MUS_HAIR)
    rig_belt(r, draw, (30, 28, 36), buckle=(214, 216, 226))
    rig_arms(r, draw, MUS_JACKET, SKIN, layer="near", hands=False, reach=0.6)
    for side in _sides(r):
        sx, sy = r.shoulder(side)
        for k in (-1.2, 1.2):
            Ell(sx + k, sy - 1.2, 0.5, 0.5).draw(draw, fill=(220, 222, 232))
    rig_head(r, draw, SKIN, hair=MUS_HAIR, style="spiky", eye_color=(90, 200, 230), expression="grin" if frame % 2 else "smirk", mood="sharp")
    # a flying-V guitar slung across the front
    if not r.back:
        gx, gy = (cx + 1.0, r.waist_y + 1.4) if not d else (cx + d * 3.0, r.waist_y + 1.4)
        ang = -30 if not d else (-20 if d > 0 else -160)
        body = Poly(xform([(-1.0, 0.0), (-8.4, -5.4), (-9.6, -3.2), (-4.0, 0.0), (-9.6, 3.2), (-8.4, 5.4)], gx, gy, ang))
        cel(draw, body, MUS_GUITAR, sh=(0.6, 0.6), hi=(0.4, 0.4))
        neck = xform([(-1.0, 0.0), (11.0, 0.0)], gx, gy, ang)
        cel(draw, Limb(neck, [0.9, 0.8]), (60, 50, 50), sh=None)
        hd = Poly(xform([(10.6, -1.2), (14.0, -2.0), (14.0, 1.0), (10.6, 1.2)], gx, gy, ang))
        cel(draw, hd, MUS_GUITAR, sh=None)
        for k in (-0.4, 0.4):
            stroke(draw, xform([(-4.0, k), (11.0, k)], gx, gy, ang), 0.25, (240, 240, 230))
        if strum:
            for k in range(2):
                a = math.radians(-60 + k * 30)
                nx, ny = gx + math.cos(a) * 9.0, gy - 6.0 + math.sin(a) * 4.0
                cel(draw, Ell(nx, ny, 1.2, 0.9), MUS_HAIR, sh=None, lw=0.5)
                stroke(draw, [(nx + 1.0, ny), (nx + 1.0, ny - 3.4)], 0.5, MUS_HAIR)
    for side in _sides(r):
        rig_hand(r, draw, side, SKIN, reach=0.6)


# ===================================================================
# ASTRONOMER (109) -- a crescent-moon circlet, a starry robe, a telescope
# ===================================================================

AST_ROBE = (44, 54, 122)
AST_GOLD = (246, 206, 96)
AST_HAIR = (170, 150, 220)


def draw_astronomer(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=0.96)
    d = r.d
    hs = _hold_side(r)
    rig_hair(r, draw, AST_HAIR, "long", layer="back")
    if d:
        rig_arms(r, draw, AST_ROBE, SKIN, layer="far", cuff=AST_GOLD)
    rig_legs(r, draw, shade(AST_ROBE, 1.0), (60, 50, 70))
    rig_robe(r, draw, AST_ROBE, trim=AST_GOLD, flare=3.4)
    cx = r.cx
    for (u, v, k) in ((-4.0, 1.4, 1.2), (3.6, 4.0, 0.9), (-1.4, 7.4, 1.0), (5.0, 9.4, 1.2), (-5.4, 10.0, 0.8)):
        if d and abs(u) > 4.0:
            continue
        star(draw, cx + u, r.sh_y + v, 1.2 * k, AST_GOLD)
    rig_belt(r, draw, AST_GOLD, buckle=(200, 220, 255))
    rig_arms(r, draw, AST_ROBE, SKIN, layer="near", cuff=AST_GOLD, hands=False, reach=0.4)
    rig_head(r, draw, SKIN, hair=AST_HAIR, style="long", eye_color=(110, 110, 210), expression="smile")
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    # round spectacles
    if not r.back:
        e1, e2, ey, _, _ = face_anchor(r)
        layer = draw.sub()
        for ex in ((e1, e2) if not d else (e1,)):
            Ell(ex, ey, 3.0, 2.9).draw(layer, fill=None, outline=AST_GOLD, width=0.6)
        draw.merge(layer)
        # a crescent moon circlet
        mx, my = hx + d * 3.0, hy - ry + 1.0
        layer = draw.sub()
        cel(layer, Ell(mx, my, 3.4, 3.4), AST_GOLD, sh=None)
        Ell(mx + 1.6, my - 1.0, 2.8, 2.8).draw(layer, fill=(0, 0, 0, 0))
        draw.merge(layer)
    # a planet with a ring, orbiting
    a = math.radians(frame * 90)
    px, py = cx + math.cos(a) * 14.0, r.head_cy - 2.0 + math.sin(a) * 3.0
    cel(draw, Ell(px, py, 2.4, 2.4), (240, 150, 110), sh=(0.5, 0.5))
    stroke(draw, arc_pts(px, py, 4.0, 1.0, 160, 380, 12), 0.5, (250, 220, 170))
    for side in _sides(r):
        h = rig_hand(r, draw, side, SKIN, reach=0.4)
        if side == hs and not r.back:
            # a brass telescope held up, pointing to the sky
            ang = -60 if not d else (-40 if d > 0 else -140)
            if not d:
                ang = -90 + hs * 36
            tube = xform([(-4.0, 0.0), (4.0, 0.0), (11.0, 0.0)], h[0], h[1], ang)
            cel(draw, Limb(tube, [1.4, 1.8, 2.2], cap=False), (214, 170, 84), sh=None)
            for t in (3.8, 8.0):
                p = xform([(t, 0.0)], h[0], h[1], ang)[0]
                q = xform([(t, 2.4)], h[0], h[1], ang)[0]
                q2 = xform([(t, -2.4)], h[0], h[1], ang)[0]
                stroke(draw, [q2, q], 0.6, shade((214, 170, 84), 1.4))
            lens = xform([(11.2, 0.0)], h[0], h[1], ang)[0]
            cel(draw, Ell(lens[0], lens[1], 1.6, 1.6), (180, 220, 255), sh=None, lw=0.5)
            if frame % 2 == 0:
                sparkle(draw, lens[0] + 2.4, lens[1] - 2.4, 1.8, (255, 250, 200))


# ===================================================================
# RUNESMITH (110) -- rune tattoos, a rune hammer, a floating runestone
# ===================================================================

RUNE_CLOAK = (70, 92, 134)
RUNE_GLOW = (100, 232, 255)
RUNE_STONE = (150, 150, 162)
RUNE_HAIR = (210, 118, 66)


def _runestone(draw, x, y, frame):
    k = 1.35
    pts = [(x - 3.6 * k, y - 4.4 * k), (x + 2.6 * k, y - 5.0 * k), (x + 4.0 * k, y + 0.6 * k), (x + 2.4 * k, y + 5.0 * k),
           (x - 3.0 * k, y + 4.4 * k), (x - 4.4 * k, y - 0.4 * k)]
    cel(draw, Poly(pts), RUNE_STONE, sh=(0.8, 0.8), hi=(0.4, 0.4))
    col = RUNE_GLOW if frame % 2 == 0 else lit(RUNE_GLOW, 0.6)
    stroke(draw, [(x, y - 4.0), (x, y + 4.0)], 0.9, col)
    stroke(draw, [(x, y - 4.0), (x + 2.8, y - 1.4), (x, y + 0.8)], 0.9, col)
    stroke(draw, [(x - 2.8, y + 1.2), (x, y + 3.2)], 0.9, col)
    if frame % 2 == 0:
        sparkle(draw, x + 5.0, y - 5.0, 1.6, RUNE_GLOW)


def draw_runesmith(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=1.04)
    d = r.d
    hs = _hold_side(r)
    bob2 = [0.0, -0.8, -1.2, -0.8][frame]
    rig_cape(r, draw, RUNE_CLOAK, layer="under")
    if d:
        rig_arms(r, draw, SKIN_TAN, SKIN_TAN, layer="far")
    rig_legs(r, draw, (80, 76, 90), (70, 56, 46))
    rig_torso(r, draw, (120, 110, 100))
    cx = r.cx
    rig_belt(r, draw, (80, 60, 46), buckle=RUNE_GLOW)
    rig_arms(r, draw, SKIN_TAN, SKIN_TAN, layer="near", hands=False)
    # glowing rune tattoos down the arms
    for side in _sides(r):
        s, e, w, h = arm_pts(r, side)
        for t in (0.35, 0.7):
            p = (s[0] + (w[0] - s[0]) * t, s[1] + (w[1] - s[1]) * t)
            stroke(draw, [(p[0] - 0.8, p[1] - 0.8), (p[0], p[1] + 0.4), (p[0] + 0.8, p[1] - 0.8)], 0.5, RUNE_GLOW)
    # a mantle with runic trim
    cel(draw, Poly([(cx - 8.4, r.sh_y - 2.6), (cx + 8.4, r.sh_y - 2.6), (cx + 6.0, r.sh_y + 2.6), (cx, r.sh_y + 4.0), (cx - 6.0, r.sh_y + 2.6)])
        if not d else Poly([(cx - 5.4, r.sh_y - 2.6), (cx + 5.8, r.sh_y - 2.6), (cx + 4.0, r.sh_y + 3.0), (cx - 4.4, r.sh_y + 3.0)]), RUNE_CLOAK, sh=(0.6, 0.6))
    rig_head(r, draw, SKIN_TAN, hair=RUNE_HAIR, style="swept", eye_color=(60, 200, 230), expression="set", mood="sharp")
    hx, hy, rx = r.hx, r.head_cy, r.head_rx
    # a leather headband with a glowing rune
    cel(draw, RRect(hx - rx - 0.6, hy - 5.8, hx + rx + 0.6, hy - 3.4, 1.0), LEATHER, sh=(0.0, 0.5))
    if not r.back:
        stroke(draw, [(hx + d * 3.0 - 0.8, hy - 5.2), (hx + d * 3.0, hy - 4.0), (hx + d * 3.0 + 0.8, hy - 5.2)], 0.5, RUNE_GLOW)
    rig_cape(r, draw, RUNE_CLOAK, layer="over")
    # the runestone floating at his off side
    os_ = -hs if not d else -d
    _runestone(draw, cx + (os_ * 13.0 if not d else -d * 11.0), r.sh_y - 3.0 + bob2, frame)
    # a rune hammer
    h = hand_at(r, hs)
    ang = -90 + (hs * 20 if not d else d * 30)
    cel(draw, Limb(xform([(-2.4, 0.0), (10.4, 0.0)], h[0], h[1], ang), [0.9, 0.9]), WOOD, sh=None)
    hd = xform([(8.4, -3.0), (12.6, -3.0), (12.6, 3.0), (8.4, 3.0)], h[0], h[1], ang)
    cel(draw, Poly(hd), RUNE_STONE, sh=None)
    c = xform([(10.5, 0.0)], h[0], h[1], ang)[0]
    Ell(c[0], c[1], 1.0, 1.0).draw(draw, fill=RUNE_GLOW)
    for side in _sides(r):
        rig_hand(r, draw, side, SKIN_TAN)


# ===================================================================
# SHAPESHIFTER (111) -- caught halfway: a beast arm, one wolf ear, a tail
# ===================================================================

SS_CLOAK = (120, 70, 170)
SS_FUR = (126, 100, 84)
SS_MAGIC = (220, 150, 255)


def draw_shapeshifter(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame)
    d = r.d
    beast = (1 if not r.back else -1) if not d else d
    wag = [0.0, 1.0, 0.0, -1.0][frame]
    cx = r.cx
    # a bushy tail
    tx = cx - (d * 3.0 if d else (-beast * 3.0))
    ts = -d if d else -beast
    cel(draw, Limb([(tx, r.hip_y), (tx + ts * 5.0, r.hip_y + 2.0 + wag), (tx + ts * 9.0, r.hip_y - 3.0)], [1.6, 2.6, 1.0]), SS_FUR, sh=(0.6, 0.6))
    rig_cape(r, draw, SS_CLOAK, layer="under", length=r.hip_y + 4.0)
    if d:
        rig_arms(r, draw, SS_CLOAK, SKIN, layer="far")
    rig_legs(r, draw, (80, 60, 100), SS_FUR)
    rig_torso(r, draw, (90, 70, 120))
    rig_belt(r, draw, (60, 44, 60), buckle=SS_MAGIC)
    # one arm human, the other a clawed beast arm
    for side in _sides(r):
        if side == beast:
            s, e, w, h = arm_pts(r, side)
            cel(draw, Limb([s, e, w], [2.8, 2.6, 2.4]), SS_FUR, sh=(0.8, 0.4))
            cel(draw, Ell(h[0], h[1], 2.8, 2.6), SS_FUR, sh=(0.5, 0.5))
            for k in (-1, 0, 1):
                x = h[0] + k * 1.3
                Poly([(x - 0.5, h[1] + 1.8), (x + 0.5, h[1] + 1.8), (x + k * 0.4, h[1] + 4.0)]).draw(draw, fill=TOOTH)
        else:
            rig_arms(r, draw, SS_CLOAK, SKIN, sides=(side,))
    head_skull(r, draw, SKIN)
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    if not r.back:
        e1, e2, ey, mx, my = face_anchor(r)
        pairs = ((-1, e1), (1, e2)) if not d else ((-d, e1), (d, e2))
        for side, ex in pairs:
            w = 1.0 if not d or side == -d else 0.72
            is_beast = (side == beast) if not d else (side == d)
            ms_eye(draw, ex, ey, 4.2 * w, 5.6, (250, 200, 50) if is_beast else (110, 80, 170), (float(d), 0.0),
                   "sharp" if is_beast else "bright", skin=SKIN, side=side)
        ms_mouth(draw, mx, my, "fangs", SKIN)
    rig_hair(r, draw, (80, 60, 110), "wild", skin=SKIN)
    # one big furry wolf ear on the beast side
    es = beast if not d else -d
    ex = hx + es * 6.6 if not d else hx - d * 2.0
    ear = Poly([(ex - 4.2, hy - ry + 4.4), (ex - 2.6, hy - ry - 1.0), (ex + es * 1.4, hy - ry - 6.4), (ex + 2.8, hy - ry - 0.6),
                (ex + 4.2, hy - ry + 4.4)])
    blob(draw, [ear, Ell(ex, hy - ry + 3.4, 4.6, 2.2)], SS_FUR, sh=(0.6, 0.6))
    Poly([(ex - 2.0, hy - ry + 2.8), (ex - 1.0, hy - ry - 0.2), (ex + es * 1.0, hy - ry - 3.8), (ex + 1.6, hy - ry + 0.2),
          (ex + 2.2, hy - ry + 2.8)]).draw(draw, fill=(240, 180, 190))
    rig_cape(r, draw, SS_CLOAK, layer="over", length=r.hip_y + 4.0)
    # changing magic swirling round it
    for i in range(3):
        a = math.radians(frame * 90 + i * 120)
        sparkle(draw, cx + math.cos(a) * 14.0, r.waist_y - 4.0 + math.sin(a) * 8.0, 1.8 if i == 0 else 1.3, SS_MAGIC)


SPECIALIST_DRAW_FUNCTIONS = {
    'alchemist': draw_alchemist,
    'puppeteer': draw_puppeteer,
    'gambler': draw_gambler,
    'blacksmith': draw_blacksmith,
    'pirate': draw_pirate,
    'chef': draw_chef,
    'musician': draw_musician,
    'astronomer': draw_astronomer,
    'runesmith': draw_runesmith,
    'shapeshifter': draw_shapeshifter,
}


def main():
    for name, draw_func in SPECIALIST_DRAW_FUNCTIONS.items():
        generate_character(name, draw_func=draw_func)
    print(f"\nGenerated {len(SPECIALIST_DRAW_FUNCTIONS)} specialist character sprites.")


if __name__ == "__main__":
    main()
