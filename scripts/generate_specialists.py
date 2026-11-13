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
    CLEAR, Fixed, bez, clipped, ms_brow, rot_pts, torso_shape,
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
ALC_GLASS = (224, 240, 246)
ALC_GLASS_LINE = (92, 128, 148)


def _alc_flask(draw, x, y, lean, frame, away, rad=4.5, drift=0.8):
    """A round-bottomed flask sitting in her palm on (x, y), half full and bubbling. Its neck leans
    `lean` degrees off upright, and the bubbles drift toward `away` (-1 or +1) as they rise: beside
    her face, never across it. Two bubbles half a cycle apart, so both standing frames are the same."""
    cy = y - rad
    a = math.radians(lean)
    ux, uy = math.sin(a), -math.cos(a)

    def at(u, v):
        return (x + ux * u - uy * v, cy + uy * u + ux * v)

    cel(draw, Poly([at(rad - 1.4, -1.35), at(rad + 3.2, -1.35), at(rad + 3.2, 1.35), at(rad - 1.4, 1.35)]), ALC_GLASS, sh=None,
        line_color=ALC_GLASS_LINE)
    brew = POTIONS[0]
    cel(draw, Ell(x, cy, rad, rad), ALC_GLASS, sh=None, line_color=ALC_GLASS_LINE, regions=[
        (RRect(x - rad - 1.0, cy - 0.9, x + rad + 1.0, cy + rad + 1.0, 0.1), brew),
        (RRect(x - rad - 1.0, cy - 0.9, x + rad + 1.0, cy + 0.2, 0.1), lit(brew, 1.2)),
        (Ell(x + 1.2, cy + rad * 0.9, rad * 0.9, rad * 0.5), shade(brew, 0.9))])
    cel(draw, Poly([at(rad + 2.7, -2.0), at(rad + 4.0, -2.0), at(rad + 4.0, 2.0), at(rad + 2.7, 2.0)]), ALC_GLASS, sh=None,
        line_color=ALC_GLASS_LINE, lw=0.8)
    Ell(x - rad * 0.42, cy - rad * 0.46, 1.15, 1.15).draw(draw, fill=(255, 255, 255))
    top = at(rad + 4.0, 0.0)
    for k in range(2):
        t = ((frame + k * 2) % 4) / 4.0
        bx, by = top[0] + away * (drift + 4.4 * t), top[1] - 1.8 - 8.4 * t
        br = 1.7 - 0.7 * t
        cel(draw, Ell(bx, by, br, br), lit(brew, 0.9), sh=None, line_color=shade(brew, 3.0), lw=0.7)
        if br > 1.2:
            Ell(bx - br * 0.3, by - br * 0.3, 0.5, 0.5).draw(draw, fill=(255, 255, 255))


def _alc_vial(draw, x, y, col):
    """A corked vial hanging from the belt, its top on (x, y)."""
    cel(draw, RRect(x - 1.5, y + 0.8, x + 1.5, y + 5.2, 1.2), col, sh=(0.5, 0.0), lw=0.7)
    cel(draw, RRect(x - 0.9, y, x + 0.9, y + 1.3, 0.3), LEATHER, sh=None, lw=0.6)
    Ell(x - 0.5, y + 2.2, 0.45, 0.6).draw(draw, fill=(255, 255, 255))


def draw_alchemist(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame)
    # the flask is carried level: the arm under it does not swing with the stride
    ra = rig(ox, oy, direction, frame, step=0)
    d = r.d
    hs = _hold_side(r)
    reach, out = (0.6, 5.4) if d else (0.8, 6.0)
    if d:
        rig_arms(r, draw, ALC_SHIRT, SKIN, layer="far")
    rig_legs(r, draw, (104, 90, 80), (110, 76, 52))
    rig_torso(r, draw, ALC_SHIRT)
    cx = r.cx
    # the green apron: a bib and a skirt in front, its straps crossed over her back
    if d:
        ap = Poly([(cx - d * 0.9, r.sh_y - 0.6), (cx + d * 4.7, r.sh_y - 0.6), (cx + d * 5.7, r.hip_y + 3.8), (cx - d * 2.8, r.hip_y + 3.8),
                   (cx - d * 1.4, r.waist_y + 0.6)])
        cel(draw, ap, ALC_APRON, sh=(0.8, 0.6))
    elif not r.back:
        ap = Poly([(cx - 4.6, r.sh_y - 0.6), (cx + 4.6, r.sh_y - 0.6), (cx + 5.8, r.hip_y + 3.8), (cx - 5.8, r.hip_y + 3.8)])
        cel(draw, ap, ALC_APRON, sh=(0.8, 0.6))
    else:
        for s in (-1, 1):
            stroke(draw, [(cx + s * 4.4, r.sh_y - 1.4), (cx - s * 3.6, r.waist_y + 0.8)], 1.5, ALC_APRON)
    rig_belt(r, draw, LEATHER, buckle=GOLD)
    # the apron's strings, tied in a bow behind
    if r.back or d:
        kx, ky = (cx, r.waist_y + 2.0) if r.back else (cx - d * 5.0, r.waist_y + 1.8)
        soft = shade(ALC_APRON, 2.2)
        k = 1.0 if r.back else 0.8      # in profile it sticks out behind her: a little smaller
        for s in ((-1, 1) if r.back else (-d,)):
            cel(draw, Limb([(kx + s * 0.6, ky + 0.6), (kx + s * 2.0, ky + 4.6 + r.ph * s * 0.6)], [1.1, 0.8]), ALC_APRON, sh=None, lw=0.6,
                line_color=soft)
            cel(draw, Poly([(kx, ky - 0.4), (kx + s * 4.4 * k, ky - 2.4 * k), (kx + s * 4.8 * k, ky + 0.2), (kx + s * 4.0 * k, ky + 1.8 * k), (kx, ky + 0.6)]),
                lit(ALC_APRON, 0.5),
                sh=None, lw=0.6, line_color=soft)
        cel(draw, Ell(kx, ky, 1.2, 1.2), ALC_APRON, sh=None, lw=0.6, line_color=soft)
    # two corked vials on the belt
    if d:
        _alc_vial(draw, cx + d * 0.2, r.waist_y + 2.4, POTIONS[1])
    else:
        k = -1 if r.back else 1
        _alc_vial(draw, cx - k * 4.3, r.waist_y + 2.4, POTIONS[1])
        _alc_vial(draw, cx + k * 4.1, r.waist_y + 2.4, POTIONS[2])
    if d:
        rig_arms(ra, draw, ALC_SHIRT, SKIN, layer="near", hands=False, reach=reach, out=out)
    else:
        rig_arms(r, draw, ALC_SHIRT, SKIN, sides=(-hs,))
        rig_arms(ra, draw, ALC_SHIRT, SKIN, sides=(hs,), hands=False, reach=reach, out=out)
    rig_head(r, draw, SKIN, hair=ALC_HAIR, style="wild", eye_color=(90, 160, 90), expression="grin", hat=True)
    _goggles(draw, r)
    # the round-bottomed flask, held up in her right hand and bubbling: out to her side head-on and
    # from behind, out ahead of her chin in profile
    h = hand_at(ra, hs, reach, 0.0, out)
    side = d if d else hs
    _alc_flask(draw, h[0] + side * 0.8, h[1] - 0.8, side * (26.0 if d else 9.0), frame, side, rad=4.2 if d else 4.6, drift=2.2 if d else 0.8)
    rig_hand(ra, draw, hs, SKIN, reach=reach, out=out)


# ===================================================================
# PUPPETEER (103) -- a doll-like face, a control bar, a dangling marionette
# ===================================================================

PUP_COAT = (106, 60, 150)
PUP_SKIN = (250, 238, 238)
PUP_HAIR = (70, 50, 96)
PUP_RIBBON = (230, 60, 90)
PUP_DOLL = (214, 170, 116)
PUP_STRING = (246, 242, 232)
PUP_GLOVE = (250, 250, 252)


def _pup_bow(draw, x, y, k=1.0):
    """A red ribbon bow on (x, y): two loops and a knot."""
    for s in (-1, 1):
        cel(draw, Poly([(x, y), (x + s * 3.3 * k, y - 2.0 * k), (x + s * 3.3 * k, y + 2.0 * k)]), PUP_RIBBON, sh=None, lw=0.6)
    cel(draw, Ell(x, y, 1.05 * k, 1.05 * k), lit(PUP_RIBBON, 0.8), sh=None, lw=0.5)


def _marionette(draw, gx, gy, out, frame, back=False):
    """The marionette, hanging from the control bar in her fist at (gx, gy): the bar runs from the
    fist toward `out` (-1 or +1), and the doll hangs by its wrists from the bar's two ends, arms
    up, its feet clear of the ground. On the strides the bar's far end tips and the doll kicks."""
    sw = [0.0, 1.0, 0.0, -1.0][frame]
    rise = sw * 1.5
    ax, ay = gx + out * 0.6, gy + 0.2             # where the two strings leave the bar
    bx, by = gx + out * 9.2, gy - rise
    x = (ax + bx) / 2.0
    y = gy + 7.6 - rise * 0.5                     # the middle of the doll's head
    hands = ((ax, ay + 6.6), (bx, by + 6.6))
    cel(draw, Limb([(gx - out * 1.8, gy + 0.3), (bx + out * 0.6, by)], [0.95, 0.95], cap0=True), WOOD, sh=None, lw=0.7)
    cel(draw, Ell(bx + out * 0.8, by, 1.3, 1.3), WOOD, sh=None, lw=0.7)
    for (px, py), (qx, qy) in (((ax, ay), hands[0]), ((bx, by), hands[1])):
        stroke(draw, [(px, py + 0.8), (qx, qy)], 0.8, PUP_STRING)
    # the doll: jointed wooden limbs, a red tunic and a little red hat
    for k, lift in ((-1, max(sw, 0.0)), (1, max(-sw, 0.0))):
        cel(draw, Limb([(x + k * 1.2, y + 6.4), (x + k * (1.5 + lift * 1.7), y + 10.0 - lift * 1.0)], [0.9, 0.8]), PUP_DOLL, sh=None, lw=0.6)
    for k, (qx, qy) in zip((-out, out), hands):
        cel(draw, Limb([(x + k * 1.9, y + 3.4), (qx, qy)], [0.8, 0.75]), PUP_DOLL, sh=None, lw=0.6)
    cel(draw, RRect(x - 2.4, y + 2.3, x + 2.4, y + 7.1, 1.0), PUP_RIBBON, sh=(0.5, 0.4), lw=0.6)
    cel(draw, Ell(x, y, 2.8, 2.7), PUP_DOLL, sh=(0.5, 0.5), lw=0.6)
    if not back:
        for k in (-1, 1):
            Ell(x + k * 1.05, y + 0.3, 0.55, 0.6).draw(draw, fill=VOID)
    cel(draw, Poly([(x - 2.6, y - 1.6), (x + 2.6, y - 1.6), (x + 0.5, y - 5.0)]), PUP_RIBBON, sh=None, lw=0.6)


def _pup_arm(r, draw, side, reach, lift, out):
    """The sleeve of the arm that holds the bar up (rig_arms cannot lift a hand): elbow low, fist high."""
    s, _, w, h = arm_pts(r, side, reach, lift, out)
    e = ((s[0] + w[0]) / 2.0, max(s[1], w[1]) + 2.0)
    cel(draw, Limb([s, e, w], [2.1, 1.95, 1.75]), PUP_COAT, sh=(0.8, 0.4))


def _pup_tail(r, draw):
    """Her twin tail in profile: the kit's hangs inside the bob's own outline, so this one stands
    out behind the head and falls, swaying with the stride."""
    hx, hy, rx, d = r.hx, r.head_cy, r.head_rx, r.d
    sw = r.ph * 1.1
    cel(draw, Limb([(hx - d * (rx + 0.4), hy - 4.4), (hx - d * (rx + 6.6), hy + 2.2), (hx - d * (rx + 5.0 - sw), hy + 13.0)], [2.4, 3.1, 0.6]),
        PUP_HAIR, sh=(0.9, 0.9))


def draw_puppeteer(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=0.94)
    # the arm that holds the control bar is held up and still: it does not swing with the stride
    ra = rig(ox, oy, direction, frame, build=0.94, step=0)
    d = r.d
    hs = _hold_side(r)
    hold = (0.8, 5.8, 4.6) if d else (0.0, 8.2, 1.9)        # reach, lift, out
    style = HAIR_STYLES["bob"] if d else dict(HAIR_STYLES["bob"], extra="twin")
    rig_hair(r, draw, PUP_HAIR, style, layer="back")
    if d:
        _pup_tail(r, draw)
        rig_arms(r, draw, PUP_COAT, PUP_GLOVE, layer="far")
    rig_legs(r, draw, (60, 50, 76), (40, 34, 50))
    cx = r.cx
    hem = r.hip_y + 4.6
    robe = rig_robe(r, draw, PUP_COAT, flare=3.0, hem=hem, split=True)
    # the hem's gold band, wide enough to be gold (the kit's 1.6 is all ink)
    clipped(draw, robe, lambda l: RRect(cx - 20.0, hem - 2.5, cx + 20.0, hem + 1.0, 0.1).draw(l, fill=GOLD))
    clipped(draw, robe, lambda l: stroke(l, [(cx - 20.0, hem - 2.5), (cx + 20.0, hem - 2.5)], 0.5, ink(GOLD)))
    robe.draw(draw, fill=None, outline=ink(PUP_COAT), width=1.0)
    if not r.back:
        for k in range(2):
            cel(draw, Ell(cx + d * 1.8, r.sh_y + 2.2 + k * 3.0, 0.95, 0.95), GOLD, sh=None, lw=0.5)
        # a big bow at the collar
        _pup_bow(draw, cx + d * 1.6, r.sh_y - 1.6, 1.1)
    rig_belt(r, draw, shade(PUP_COAT, 1.6), buckle=GOLD)
    if not d:
        rig_arms(r, draw, PUP_COAT, PUP_GLOVE, sides=(-hs,))
    _pup_arm(ra, draw, hs, *hold)
    rig_head(r, draw, PUP_SKIN, hair=PUP_HAIR, style=style, eye_color=(170, 70, 170), expression="smile", blush=False)
    hx, hy, rx = r.hx, r.head_cy, r.head_rx
    if not r.back:
        # a doll's round painted cheeks
        e1, e2, ey, mx, my = face_anchor(r)
        for ex in ((e1, e2) if not d else (e1,)):
            cel(draw, Ell(ex + (1 if ex > r.hx else -1) * 2.4, ey + 3.6, 1.5, 1.5), (255, 170, 190), sh=None, line=False)
    # red ribbons where her twin tails are tied
    if d:
        _pup_bow(draw, hx - d * (rx + 3.0), hy - 2.6, 0.9)
    else:
        for s in (-1, 1):
            _pup_bow(draw, hx + s * (rx + 1.3), hy - 3.6, 0.9)
    # the control bar in her raised fist, and the marionette dangling from it: beside her head-on
    # and from behind, ahead of her in profile
    h = hand_at(ra, hs, *hold)
    _marionette(draw, h[0], h[1], d if d else hs, frame, back=r.back)
    rig_hand(ra, draw, hs, PUP_GLOVE, *hold[:2], out=hold[2])


# ===================================================================
# GAMBLER (104) -- a top hat with a card in the band, vest and bow tie
# ===================================================================

GAM_SUIT = (46, 42, 56)
GAM_RED = (200, 36, 52)
GAM_HAIR = (240, 214, 150)
GAM_TROUSERS = (96, 90, 116)        # a step lighter than the tailcoat, so the coat and its tails read against them
GAM_CARD = (252, 252, 252)
GAM_GLOVE = (250, 250, 252)


def _gam_card(draw, x, y, ang, face=True, red=True, w=4.8, h=6.8, corner=0):
    """One playing card standing on (x, y) along ang: its face with one pip, or its red back. With
    `corner` (-1 or +1) the pip is small and up in that corner, the one that still shows when the
    next card of a fan lies over this one."""
    card = Poly(xform([(0.6, -w / 2.0), (0.6 + h, -w / 2.0), (0.6 + h, w / 2.0), (0.6, w / 2.0)], x, y, ang))
    cel(draw, card, GAM_CARD, sh=None, lw=0.6)
    if not face:
        Poly(xform([(1.5, -w / 2.0 + 0.9), (h - 0.3, -w / 2.0 + 0.9), (h - 0.3, w / 2.0 - 0.9), (1.5, w / 2.0 - 0.9)], x, y, ang)).draw(
            draw, fill=GAM_RED)
        return
    px, py = xform([(0.6 + h * 0.7, corner * w * 0.2) if corner else (0.6 + h * 0.52, 0.0)], x, y, ang)[0]
    k = 1.0 if corner else 1.45
    if red:
        Poly([(px, py - k * 1.15), (px + k * 0.85, py), (px, py + k * 1.15), (px - k * 0.85, py)]).draw(draw, fill=GAM_RED)
    else:
        Ell(px, py, k * 0.85, k * 0.9).draw(draw, fill=VOID)


def _gam_die(draw, x, y, rot, s=3.4):
    """A die seen corner-on, tumbling: three faces in three pale tones (the tones are what make it
    a cube at game size), a red pip on top, two dark ones on one side and three on the other (one
    on each side made it a little skull)."""
    def p(u, v):
        return rot_pts([(x + u * s, y + v * s)], x, y, rot)[0]

    t, ur, lr, b, ll, ul, c = p(0, -1), p(0.87, -0.5), p(0.87, 0.5), p(0, 1), p(-0.87, 0.5), p(-0.87, -0.5), (x, y)
    cel(draw, Poly([t, ur, lr, b, ll, ul]), (255, 255, 255), sh=None, lw=0.7, line_color=(96, 98, 128), regions=[
        (Poly([ul, c, b, ll]), (226, 228, 242)), (Poly([c, ur, lr, b]), (190, 194, 218))])
    tx, ty = p(0, -0.5)
    Ell(tx, ty, 1.05, 0.8).draw(draw, fill=GAM_RED)
    for (u, v, k) in ((-0.6, 0.02, 0.58), (-0.27, 0.52, 0.58), (0.22, 0.6, 0.48), (0.44, 0.26, 0.48), (0.66, -0.08, 0.48)):
        qx, qy = p(u, v)
        Ell(qx, qy, k, k * 1.08).draw(draw, fill=(70, 66, 90))


def _gam_arm(r, draw, side, reach, lift, out):
    """A sleeve to a hand held where rig_arms cannot put it (it has no lift)."""
    s, e, w, _ = arm_pts(r, side, reach, lift, out)
    cel(draw, Limb([s, e, w], [2.15, 1.95, 1.75]), GAM_SUIT if r.near(side) else shade(GAM_SUIT, 0.6), sh=(0.8, 0.4))


def _gam_tails(r, draw):
    """The tailcoat's two tails, swinging with the stride. Head-on they hang behind his legs (call
    it before rig_legs); from behind and in profile they lie over them (after rig_torso)."""
    cx, d, base = r.cx, r.d, r.base_y
    sway = r.ph * 0.9
    top = r.hip_y - 1.0
    if d:
        fl = abs(r.ph) * 1.6
        cel(draw, Poly([(cx - d * 4.6, top - 1.6), (cx - d * 0.4, top + 1.3), (cx - d * 3.0, base - 7.6), (cx - d * (7.4 + fl), base - 4.4 - fl * 0.8),
                        (cx - d * (6.5 + fl * 0.5), base - 9.2)]), GAM_SUIT, sh=(0.8, 0.5))
        return
    for s in (-1, 1):
        if r.back:
            pts = [(cx + s * 0.4, top), (cx + s * (r.hip_w + 0.4), top), (cx + s * (r.hip_w + 1.2) + sway, base - 8.0),
                   (cx + s * 4.4 + sway * 1.4, base - 4.0), (cx + s * 1.6 + sway, base - 7.4)]
        else:
            pts = [(cx + s * 2.0, top), (cx + s * (r.hip_w + 0.4), top), (cx + s * (r.hip_w + 1.5) + sway, base - 8.0),
                   (cx + s * 4.8 + sway * 1.4, base - 4.2), (cx + s * 2.4 + sway, base - 7.0)]
        cel(draw, Poly(pts), GAM_SUIT if r.back else shade(GAM_SUIT, 0.8), sh=(0.8, 0.4))


def _gam_hat(r, draw):
    """The top hat: as wide as his head and taller than his face, a red band, a card tucked in it."""
    hy, d = r.head_cy, r.d
    hcx = r.hx - d * 0.9
    by = hy - 7.6
    top = by - 13.2
    cel(draw, Ell(hcx, by, 15.4 if d else 16.0, 3.7), GAM_SUIT, sh=(0.0, 1.1))
    crown = Poly([(hcx - 9.2, by), (hcx - 10.3, top)] + arc_pts(hcx, top, 10.3, 2.4, 180, 360, 14)[1:-1] + [(hcx + 10.3, top), (hcx + 9.2, by)])
    cel(draw, crown, GAM_SUIT, sh=(1.8, 0.0), regions=[
        (RRect(hcx - 12.0, by - 3.9, hcx + 12.0, by - 0.5, 0.1), GAM_RED),
        (RRect(hcx - 12.0, by - 1.4, hcx + 12.0, by - 0.5, 0.1), shade(GAM_RED, 1.2))])
    cel(draw, Ell(hcx, top, 10.3, 2.4), lit(GAM_SUIT, 1.2), sh=None, lw=0.8)
    if not r.back:
        _gam_card(draw, hcx + (5.0 if not d else d * 4.2), by - 1.6, -90 + (d if d else 1) * 13, w=4.0, h=5.6)


def draw_gambler(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=0.98, head=0.96)
    # his hands are busy and held still as he walks: one fans his cards, the other tosses the die
    ra = rig(ox, oy, direction, frame, build=0.98, head=0.96, step=0)
    d = r.d
    hs = _hold_side(r)
    cx = r.cx
    toss = [2.0, 3.6, 2.0, 0.4][frame]
    spin = [0.0, 28.0, 0.0, -28.0][frame]
    fan = (0.6, 0.0, 3.8) if d else (0.9, 0.0, 4.5)           # reach, lift, out
    die = (0.0, 2.6, -8.0) if d else (0.7, 0.0, 4.0)
    if d:
        # in profile the far hand is held out behind him, the die tumbling over its palm
        _gam_arm(ra, draw, -d, *die)
        h = rig_hand(ra, draw, -d, GAM_GLOVE, die[0], die[1], out=die[2])
        _gam_die(draw, h[0] - d * (0.8 + 0.4 * toss), h[1] - 5.2 - toss * 0.6, spin * d)
    elif not r.back:
        _gam_tails(r, draw)
    rig_legs(r, draw, GAM_TROUSERS, (30, 26, 34))
    rig_torso(r, draw, GAM_SUIT)
    if not r.back:
        vx = cx + d * 1.6
        cel(draw, Poly([(vx - 3.6, r.sh_y - 1.8), (vx + 3.6, r.sh_y - 1.8), (vx + 3.2, r.waist_y + 1.8), (vx, r.waist_y + 3.2),
                        (vx - 3.2, r.waist_y + 1.8)]), GAM_RED, sh=(0.6, 0.0))
        cel(draw, Poly([(vx - 1.9, r.sh_y - 2.2), (vx + 1.9, r.sh_y - 2.2), (vx, r.sh_y + 1.2)]), GAM_GLOVE, sh=None, lw=0.5)
        for s in (-1, 1):
            cel(draw, Poly([(vx, r.sh_y - 1.3), (vx + s * 2.9, r.sh_y - 2.8), (vx + s * 2.9, r.sh_y + 0.2)]), VOID, sh=None, lw=0.4)
        for k in range(2):
            Ell(vx, r.sh_y + 2.6 + k * 2.4, 0.8, 0.8).draw(draw, fill=GOLD)
        # a gold watch chain
        stroke(draw, [(vx - 2.6, r.waist_y - 0.2), (vx - 0.6, r.waist_y + 1.1), (vx + 2.6, r.waist_y)], 0.9, GOLD)
    if d or r.back:
        _gam_tails(r, draw)
    if r.back:
        # the two buttons at the small of a tailcoat's back
        for s in (-1, 1):
            cel(draw, Ell(cx + s * 2.1, r.waist_y + 1.8, 1.0, 1.0), GOLD, sh=None, lw=0.5)
    if d:
        _gam_arm(ra, draw, d, *fan)
    else:
        _gam_arm(ra, draw, hs, *fan)
        _gam_arm(ra, draw, -hs, *die)
    rig_head(r, draw, SKIN, hair=GAM_HAIR, style="swept", eye_color=(200, 60, 70), expression="smirk", mood="sharp", hat=True)
    _gam_hat(r, draw)
    # three cards fanned in one hand: their faces head-on and in profile, their red backs from behind
    side = d if d else hs
    h = hand_at(ra, side, *fan)
    for j, a in enumerate((-14, 14, 42)):
        _gam_card(draw, h[0], h[1] - 0.4, -90 + side * a, face=not r.back, red=j != 1, corner=-side if j < 2 else 0)
    rig_hand(ra, draw, side, GAM_GLOVE, fan[0], fan[1], out=fan[2])
    # and the die tossed from the other
    if not d:
        h = rig_hand(ra, draw, -hs, GAM_GLOVE, die[0], die[1], out=die[2])
        _gam_die(draw, h[0] - hs * (1.6 + 0.5 * toss), h[1] - 5.2 - toss, -hs * spin)


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
        # the apron's straps crossed over his back, and its strings tied at the waist
        for s in (-1, 1):
            stroke(draw, [(cx + s * 4.4, r.sh_y - 1.4), (cx - s * 3.6, r.waist_y + 0.6)], 1.3, BS_APRON)
    rig_belt(r, draw, (80, 60, 44), buckle=(200, 200, 210))
    if r.back:
        for s in (-1, 1):
            cel(draw, Poly([(cx, r.waist_y + 2.0), (cx + s * 3.2, r.waist_y + 0.4), (cx + s * 3.0, r.waist_y + 3.8)]), BS_APRON, sh=None, lw=0.6)
            cel(draw, Limb([(cx + s * 0.5, r.waist_y + 2.4), (cx + s * 1.6, r.waist_y + 6.4 + r.ph * s * 0.5)], [0.8, 0.6]), BS_APRON, sh=None, lw=0.6)
        cel(draw, Ell(cx, r.waist_y + 2.0, 1.1, 1.1), shade(BS_APRON, 0.8), sh=None, lw=0.5)
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
    # the smith's sledge, as big as his head and its striking face still hot from the forge: raised
    # at his side head-on and from behind, held out ahead of him in profile (clear of his beard)
    h = hand_at(r, hs)
    ang = -90 + (hs * 24 if not d else d * 62)
    k = hs if not d else d
    cel(draw, Limb(xform([(-2.6, 0.0), (11.0, 0.0)], h[0], h[1], ang), [1.1, 1.1]), WOOD, sh=None)
    iron = (110, 110, 124)
    hot = BS_HOT if glow else mix(BS_HOT, (230, 96, 40), 0.5)
    cel(draw, Poly(xform([(9.4, -4.7), (15.6, -4.7), (15.6, 4.7), (9.4, 4.7)], h[0], h[1], ang)), iron, sh=None, regions=[
        (Poly(xform([(9.4, -k * 4.8), (15.6, -k * 4.8), (15.6, -k * 3.0), (9.4, -k * 3.0)], h[0], h[1], ang)), shade(iron, 1.0)),
        (Poly(xform([(9.4, k * 2.3), (15.6, k * 2.3), (15.6, k * 4.8), (9.4, k * 4.8)], h[0], h[1], ang)), hot),
        (Poly(xform([(10.2, k * 3.3), (14.8, k * 3.3), (14.8, k * 4.1), (10.2, k * 4.1)], h[0], h[1], ang)), (255, 226, 130))])
    if glow:
        p = xform([(12.5, k * 6.4)], h[0], h[1], ang)[0]
        sparkle(draw, p[0], p[1] - 0.8, 2.0, (255, 220, 120))
    for side in _sides(r):
        rig_hand(r, draw, side, SKIN_TAN)


# ===================================================================
# PIRATE (106) -- tricorn and skull, eyepatch, red coat, a parrot
# ===================================================================

PIR_COAT = (196, 48, 50)
PIR_HAT = (40, 36, 48)
PIR_SHIRT = (246, 244, 236)
PIR_STRIPE = (60, 90, 170)
PIR_HAIR = (86, 56, 40)
PARROT = (60, 190, 90)
PARROT_BLUE = (70, 130, 236)
# dark brown hair to the jaw, the rest of it tied back in a queue (_pir_queue): a step lighter than
# the black hat, so the hat reads as a hat, and short enough to leave the coat showing from behind
PIR_HAIR_STYLE = dict(vol=1.9, fringe=((-0.72, 4.0), (-0.28, 5.2), (0.18, 5.0), (0.62, 4.2)), sweep=0.7, side=7.6, back=9.5, lock=3.4)


def _pir_parrot(draw, x, y, f, frame):
    """A green parrot perched upright with its feet on (x, y), looking toward f (+1 right, -1 left)."""
    flap = [0, 1, 0, 1][frame]
    # tail feathers hanging down behind the perch: blue and gold, so they show on the red coat
    cel(draw, Limb([(x - f * 1.4, y - 2.6), (x - f * 2.6, y + 1.6), (x - f * 3.4, y + 4.6)], [1.1, 1.0, 0.4]), (252, 200, 60), sh=None, lw=0.6)
    cel(draw, Limb([(x - f * 0.6, y - 2.6), (x - f * 1.2, y + 2.4), (x - f * 1.6, y + 6.4)], [1.3, 1.2, 0.5]), PARROT_BLUE, sh=None, lw=0.6)
    cel(draw, Ell(x, y - 3.4, 2.9, 3.6), PARROT, sh=(0.5, 0.5), lw=0.7)
    # the folded wing, lifting on the strides
    cel(draw, Poly([(x + f * 0.8, y - 5.8), (x - f * 3.2, y - 4.6 - flap * 1.8), (x - f * 3.7, y - 0.4 - flap * 1.2), (x - f * 0.2, y - 0.9)]),
        PARROT_BLUE, sh=None, lw=0.6)
    px, py = x + f * 0.9, y - 7.6
    cel(draw, Ell(px, py, 2.4, 2.2), PARROT, sh=(0.4, 0.4), lw=0.7)
    cel(draw, Poly([(px + f * 1.5, py - 1.3), (px + f * 3.8, py - 0.2), (px + f * 2.7, py + 2.0), (px + f * 1.6, py + 1.0)]), (252, 200, 60), sh=None, lw=0.5)
    Ell(px + f * 0.6, py - 0.5, 1.0, 1.0).draw(draw, fill=(255, 255, 255))
    Ell(px + f * 0.85, py - 0.5, 0.6, 0.6).draw(draw, fill=VOID)


def _pir_queue(r, draw):
    """The rest of his hair, tied back with a black bow: behind the nape in profile, down the coat from behind."""
    hx, hy, rx, d = r.hx, r.head_cy, r.head_rx, r.d
    sw = r.ph * 1.3
    if d:
        x0 = hx - d * (rx - 0.2)
        cel(draw, Limb([(x0, hy + 6.0), (x0 - d * 2.2, hy + 10.6), (x0 - d * (2.0 + abs(sw)), hy + 17.0)], [2.0, 2.4, 0.7]), PIR_HAIR, sh=(0.6, 0.6))
        bx, by = x0 - d * 1.8, hy + 10.4
    elif r.back:
        cel(draw, Limb([(hx, hy + 7.0), (hx + sw * 0.4, hy + 14.0), (hx + sw, hy + 20.5)], [2.2, 2.7, 0.8]), PIR_HAIR, sh=(0.7, 0.7))
        bx, by = hx, hy + 12.8
    else:
        return
    for s in (-1, 1):
        cel(draw, Poly([(bx, by), (bx + s * 3.0, by - 1.7), (bx + s * 3.0, by + 1.7)]), PIR_HAT, sh=None, lw=0.5)
    cel(draw, Ell(bx, by, 1.0, 1.0), lit(PIR_HAT, 1.6), sh=None, lw=0.4)


def _pir_epaulette(draw, x, y):
    cel(draw, Poly([(x - 2.3, y), (x + 2.3, y), (x + 2.6, y + 1.8), (x - 2.6, y + 1.8)]), shade(GOLD, 1.3), sh=None, lw=0.6)
    cel(draw, Ell(x, y - 0.2, 2.8, 1.3), GOLD, sh=(0.0, 0.4), lw=0.6)


def draw_pirate(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=1.04)
    d = r.d
    hs = _hold_side(r)
    cx = r.cx
    sway = r.ph * 0.9
    if d:
        rig_arms(r, draw, PIR_COAT, SKIN_TAN, layer="far", cuff=GOLD)
    rig_legs(r, draw, (60, 56, 70), (50, 40, 44))
    rig_torso(r, draw, PIR_SHIRT)
    if not r.back:
        for k in range(3):
            y = r.sh_y + 1.0 + k * 2.4
            stroke(draw, [(cx - 3.0 + d * 1.6, y), (cx + 3.0 + d * 1.6, y)], 0.8, PIR_STRIPE)
    # the long red coat, open at the front, gold braid down its edges and round its tails
    hem = r.hip_y + 5.0
    if d:
        coat = Poly([(cx - d * 3.8, r.sh_y - 2.4), (cx + d * 1.0, r.sh_y - 2.4), (cx + d * 0.4, hem), (cx - d * (6.4 + abs(sway)), hem)])
        cel(draw, coat, PIR_COAT, sh=(0.8, 0.6), regions=[
            (Poly([(cx + d * 0.0, r.sh_y - 2.4), (cx + d * 1.0, r.sh_y - 2.4), (cx + d * 0.4, hem), (cx - d * 0.6, hem)]), GOLD),
            (Poly([(cx - d * 9.0, hem - 1.3), (cx + d * 2.0, hem - 1.3), (cx + d * 2.0, hem), (cx - d * 9.0, hem)]), GOLD)])
    elif r.back:
        coat = Poly([(cx - r.sh_w + 0.6, r.sh_y - 2.4), (cx + r.sh_w - 0.6, r.sh_y - 2.4), (cx + r.sh_w + 2.4 + sway, hem),
                     (cx - r.sh_w - 2.4 + sway, hem)])
        cel(draw, coat, PIR_COAT, sh=(1.2, 0.6), regions=[
            (Poly([(cx - 0.5, r.waist_y + 3.0), (cx + 0.5, r.waist_y + 3.0), (cx + 1.5 + sway, hem), (cx - 1.5 + sway, hem)]), shade(PIR_COAT, 2.4)),
            (Poly([(cx - 12.0, hem - 1.3), (cx + 12.0, hem - 1.3), (cx + 12.0, hem), (cx - 12.0, hem)]), GOLD)])
    else:
        for s in (-1, 1):
            cel(draw, Poly([(cx + s * 2.8, r.sh_y - 2.4), (cx + s * r.sh_w, r.sh_y - 1.8), (cx + s * (r.sh_w + 2.6) + sway, hem),
                            (cx + s * 3.4, hem)]), PIR_COAT, sh=(0.8, 0.6), regions=[
                (Poly([(cx + s * 2.8, r.sh_y - 2.4), (cx + s * 3.8, r.sh_y - 2.4), (cx + s * 4.4, hem), (cx + s * 3.4, hem)]), GOLD),
                (Poly([(cx - 12.0, hem - 1.3), (cx + 12.0, hem - 1.3), (cx + 12.0, hem), (cx - 12.0, hem)]), GOLD)])
    rig_belt(r, draw, (60, 44, 40), buckle=GOLD)
    rig_arms(r, draw, PIR_COAT, SKIN_TAN, layer="near", hands=False, cuff=GOLD)
    # a captain's epaulettes: they carry the shoulders out past the head, and give the parrot its perch
    ps = -hs if not d else d
    for side in _sides(r):
        sx, sy = r.shoulder(side)
        _pir_epaulette(draw, sx + (side * 2.6 if not d else -d * 0.6), sy - 1.0)
    _pir_queue(r, draw)
    head_skull(r, draw, SKIN_TAN)
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    brow = (50, 36, 30)
    e1, e2, ey, mx, my = face_anchor(r)
    # the patch is over his left eye: the camera's right head-on, the near eye walking left, the far one walking right
    if d > 0:
        # walking right his good eye is the near one, which the kit half hides under the sideburn: drawn
        # by hand a little forward of it, with the patch a dark sliver on the far side of the face
        Ell(e1 - 1.6, ey + 3.4, 1.8, 0.8).draw(draw, fill=mix(SKIN_TAN, (255, 128, 128), 0.32))
        ex = e1 + 1.5
        ms_eye(draw, ex, ey, 4.2, 5.6, (80, 60, 40), (1.0, 0.0), "sharp", None, SKIN_TAN, -1)
        ms_brow(draw, ex, ey - 4.3, 3.4, brow, 0.0, -1)
        cel(draw, Ell(e2 + 0.5, ey + 0.3, 1.8, 2.7), VOID, sh=None)
        stroke(draw, [(e2 + 0.3, ey - 1.6), (hx + 4.2, hy - 4.8)], 0.8, VOID)
        ms_mouth(draw, mx, my, "grin", SKIN_TAN, 0.8)
    elif not r.back:
        head_face(r, draw, SKIN_TAN, iris=(80, 60, 40), mood="sharp", mouth="grin", brows=True, brow_color=brow)
        ex = e2 if not d else e1
        cel(draw, Ell(ex, ey + 0.4, 2.8, 2.6), VOID, sh=None)
        stroke(draw, [(hx - rx + 0.6, hy - 3.6), (ex, ey), (hx + rx - 0.4, hy - 1.6)] if not d else
               [(ex - d * 3.0, ey - 2.4), (ex, ey), (hx - d * 3.0, hy - 3.0)], 0.8, VOID)
    rig_hair(r, draw, PIR_HAIR, PIR_HAIR_STYLE, hat=True, skin=SKIN_TAN)
    # tricorn: the brim turned up against the crown, gold braid along its edge, a white skull on the front
    cx0 = hx - d * 0.6
    by = hy - 6.0
    if d:
        crown = Poly(arc_pts(cx0 - d * 0.6, by - 4.4, 7.4, 8.0, 180, 360, 20))
        panel = Poly([(cx0 + d * 14.5, by + 0.8), (cx0 + d * 7.0, by + 1.8), (cx0 - d * 6.0, by + 1.8), (cx0 - d * 13.5, by - 1.6),
                      (cx0 - d * 9.4, by - 6.0), (cx0 - d * 3.2, by - 8.8), (cx0 + d * 3.0, by - 8.6), (cx0 + d * 8.6, by - 5.2)])
    elif r.back:
        crown = Poly(arc_pts(cx0, by - 4.4, 7.6, 8.0, 180, 360, 20))
        panel = Poly([(cx0 - 15.4, by - 3.8), (cx0 - 8.0, by - 0.4), (cx0, by + 0.4), (cx0 + 8.0, by - 0.4), (cx0 + 15.4, by - 3.8), (cx0 + 11.0, by - 7.6),
                      (cx0 + 4.0, by - 9.6), (cx0 - 4.0, by - 9.6), (cx0 - 11.0, by - 7.6)])
    else:
        crown = Poly(arc_pts(cx0, by - 4.4, 7.6, 8.0, 180, 360, 20))
        panel = Poly([(cx0 - 15.4, by - 3.0), (cx0 - 7.0, by - 1.8), (cx0, by + 1.4), (cx0 + 7.0, by - 1.8), (cx0 + 15.4, by - 3.0), (cx0 + 11.0, by - 7.4),
                      (cx0 + 4.0, by - 9.4), (cx0 - 4.0, by - 9.4), (cx0 - 11.0, by - 7.4)])
    cel(draw, crown, PIR_HAT, sh=(1.2, 0.6))
    cel(draw, panel, PIR_HAT, sh=(0.0, 1.0), line=False)

    def braid(layer):
        panel.draw(layer, fill=GOLD)
        panel.shift(0.0, 1.6).draw(layer, fill=CLEAR)
    clipped(draw, panel, braid)
    panel.draw(draw, fill=None, outline=ink(PIR_HAT), width=1.0)
    if not r.back:
        sx, sy = cx0 + d * 2.4, by - 3.9
        cel(draw, RRect(sx - 1.5, sy + 0.6, sx + 1.5, sy + 3.0, 0.6), (246, 244, 236), sh=None, lw=0.5)
        cel(draw, Ell(sx, sy, 2.6, 2.2), (246, 244, 236), sh=None, lw=0.5)
        for s in (-1, 1):
            Ell(sx + s * 1.0, sy + 0.2, 0.7, 0.75).draw(draw, fill=VOID)
    # the parrot on his right shoulder, out past his cheek
    if d:
        _pir_parrot(draw, cx - d * 7.2, r.sh_y - 0.8, d, frame)
    else:
        sx, sy = r.shoulder(ps)
        _pir_parrot(draw, sx + ps * 5.6, sy - 2.2, ps, frame)
    # a cutlass, held low and clear of his legs
    h = hand_at(r, hs)
    ang = 90 - (hs * 38 if not d else d * 60)
    blade(draw, h[0], h[1], ang, length=12.0, width=1.9, curve=-0.7, hilt=GOLD, grip=(60, 44, 40), guard=2.6, flip=hs if not d else d)
    for side in _sides(r):
        rig_hand(r, draw, side, SKIN_TAN)


# ===================================================================
# CHEF (107) -- a towering toque, a double-breasted jacket, a frying pan
# ===================================================================

CHEF_WHITE = (250, 250, 252)
CHEF_RED = (220, 56, 60)
CHEF_SHADE = (210, 216, 234)        # the whites' shadow side: a cool grey-blue
CHEF_APRON = (206, 50, 58)          # the waist apron, red like his neckerchief: white on white had no planes
CHEF_BUTTON = (96, 106, 146)
CHEF_PAN = (76, 74, 90)


def _chef_toque(r, draw):
    """The toque: a band that covers the hair, and a pleated crown that billows out wider than his head."""
    hy, d = r.head_cy, r.d
    x = r.hx - d * 1.1          # over the middle of the hair, which in profile lies behind the skull's
    bw = 12.9 if d else 12.2
    puff = [Ell(x - 6.4, hy - 16.6, 7.0, 6.2), Ell(x + 6.4, hy - 16.6, 7.0, 6.2), Ell(x, hy - 18.3, 7.8, 5.7),
            Poly([(x - 10.6, hy - 7.6), (x - 11.8, hy - 14.6), (x + 11.8, hy - 14.6), (x + 10.6, hy - 7.6)])]
    pleats = [(Poly([(x + k - 0.7, hy - 8.0), (x + k + 0.7, hy - 8.0), (x + k * 1.3, hy - 16.2)]), CHEF_SHADE) for k in (-5.4, 0.0, 5.4)]
    blob(draw, puff, CHEF_WHITE, sh=(1.6, 1.3), tone=CHEF_SHADE, regions=pleats)
    cel(draw, RRect(x - bw, hy - 8.7, x + bw, hy - 4.2, 1.4), CHEF_WHITE, sh=(0.0, 0.9), tone=CHEF_SHADE)


def _chef_pan(draw, x, y, ang):
    """A frying pan gripped at (x, y), its handle along ang, seen from above as the camera sees
    it from every side, a fried egg in it. (The egg stays put: flipped on the strides, it sat on the
    far rim and the empty pan read as a pot.)"""
    cel(draw, Limb(xform([(-1.4, 0.0), (5.6, 0.0)], x, y, ang), [1.0, 1.0], cap0=True), (56, 52, 62), sh=None)
    px, py = xform([(10.2, 0.0)], x, y, ang)[0]
    cel(draw, Ell(px, py + 1.2, 6.2, 4.0), shade(CHEF_PAN, 1.4), sh=None)
    cel(draw, Ell(px, py, 6.2, 4.0), lit(CHEF_PAN, 1.0), sh=None)
    cel(draw, Ell(px, py + 0.5, 5.0, 2.9), CHEF_PAN, sh=None, line=False)
    ey = py + 0.3
    blob(draw, [Ell(px - 0.7, ey, 2.9, 1.9), Ell(px + 1.6, ey + 0.3, 2.0, 1.5)], (253, 253, 250), sh=None, lw=0.5, line_color=(150, 152, 176))
    cel(draw, Ell(px + 0.1, ey - 0.2, 1.5, 1.15), (255, 190, 50), sh=None, lw=0.5, line_color=(214, 130, 30))
    Ell(px - 0.3, ey - 0.6, 0.45, 0.4).draw(draw, fill=(255, 250, 220))


def _chef_bow(draw, x, y, sides, ph, k=1.0):
    """The apron's strings tied behind him: a loop and a tail on each of `sides`."""
    soft = shade(CHEF_APRON, 2.0)
    for s in sides:
        cel(draw, Limb([(x + s * 0.6, y + 0.6), (x + s * 2.0, y + 4.8 + ph * s * 0.6)], [1.1, 0.8]), CHEF_APRON, sh=None, lw=0.6, line_color=soft)
        cel(draw, Poly([(x, y - 0.4), (x + s * 4.0 * k, y - 2.3 * k), (x + s * 4.4 * k, y + 0.2), (x + s * 3.6 * k, y + 1.8 * k), (x, y + 0.6)]),
            lit(CHEF_APRON, 0.4),
            sh=None, lw=0.6, line_color=soft)
    cel(draw, Ell(x, y, 1.2, 1.2), CHEF_APRON, sh=None, lw=0.6, line_color=soft)


def draw_chef(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=1.08, head=0.94)
    # the pan is carried level: the arm that holds it does not swing with the stride
    ra = rig(ox, oy, direction, frame, build=1.08, head=0.94, step=0)
    d = r.d
    hs = _hold_side(r)
    cx, base = r.cx, r.base_y
    sway = r.ph * 0.8
    reach, out = (0.6, 2.5) if d else (0.6, 1.0)
    hem = base - 5.6
    band = shade(CHEF_APRON, 1.1)
    if d:
        rig_arms(r, draw, CHEF_WHITE, SKIN, layer="far")
    if r.back:
        # from behind the apron's edges show either side of his legs
        for s in (-1, 1):
            cel(draw, Poly([(cx + s * 4.0, r.waist_y + 1.0), (cx + s * (r.hip_w + 0.5), r.waist_y + 1.0), (cx + s * (r.hip_w + 1.3) + sway, hem),
                            (cx + s * 4.0 + sway, hem)]), shade(CHEF_APRON, 0.8), sh=None)
    rig_legs(r, draw, (70, 70, 84), (40, 36, 44))
    rig_torso(r, draw, CHEF_WHITE)
    if not r.back:
        # the double-breasted jacket's two rows of buttons, and a red neckerchief
        for k in range(2):
            for s in ((-1, 1) if not d else (1,)):
                Ell(cx + s * 2.3 + d * 2.0, r.sh_y + 2.8 + k * 2.5, 0.85, 0.85).draw(draw, fill=CHEF_BUTTON)
        cel(draw, Poly([(cx - 3.8 + d * 1.4, r.sh_y - 2.4), (cx + 3.8 + d * 1.4, r.sh_y - 2.4), (cx + d * 1.4, r.sh_y + 1.9)]), CHEF_RED, sh=None, lw=0.6)
    # the waist apron and its strings
    wy = r.waist_y + 0.4
    if d:
        cel(draw, Poly([(cx - d * 1.6, wy), (cx + d * 5.0, wy), (cx + d * (6.0 + sway), hem), (cx - d * (2.6 - sway), hem)]), CHEF_APRON, sh=(0.8, 0.5))
        cel(draw, RRect(cx - 4.7, wy, cx + 4.9, wy + 1.9, 0.5), band, sh=None)
        _chef_bow(draw, cx - d * 5.0, wy + 0.9, (-d,), r.ph, 0.8)
    elif r.back:
        cel(draw, RRect(cx - r.hip_w - 0.5, wy, cx + r.hip_w + 0.5, wy + 1.9, 0.5), band, sh=None)
        _chef_bow(draw, cx, wy + 1.0, (-1, 1), r.ph)
    else:
        cel(draw, Poly([(cx - r.hip_w - 0.5, wy), (cx + r.hip_w + 0.5, wy), (cx + r.hip_w + 1.5 + sway, hem), (cx - r.hip_w - 1.5 + sway, hem)]),
            CHEF_APRON, sh=(1.0, 0.6), regions=[(RRect(cx - 14.0, wy, cx + 14.0, wy + 1.9, 0.1), band)])
    if d:
        rig_arms(ra, draw, CHEF_WHITE, SKIN, layer="near", hands=False, reach=reach, out=out)
    else:
        rig_arms(r, draw, CHEF_WHITE, SKIN, sides=(-hs,))
        rig_arms(ra, draw, CHEF_WHITE, SKIN, sides=(hs,), hands=False, reach=reach, out=out)
    # the neckerchief's knot, at the back of his neck
    if d or r.back:
        kx, ky = (cx, r.sh_y - 1.2) if r.back else (cx - d * 4.6, r.sh_y - 1.6)
        for s in ((-1, 1) if r.back else (-d,)):
            cel(draw, Poly([(kx, ky - 0.6), (kx + s * 3.2, ky + 2.6 + r.ph * s * 0.4), (kx + s * 0.6, ky + 2.2)]), CHEF_RED, sh=None, lw=0.6)
        cel(draw, Ell(kx, ky, 1.3, 1.2), CHEF_RED, sh=None, lw=0.6)
    head_skull(r, draw, SKIN)
    hx, hy = r.hx, r.head_cy
    if not r.back:
        head_face(r, draw, SKIN, iris=(90, 70, 60), mood="bright", mouth="smile", brows=True, brow_color=(90, 60, 50))
        # a curled moustache
        mx = hx + d * 6.0 if d else hx
        for s in ((-1, 1) if not d else (d, -d)):
            cel(draw, Limb([(mx, hy + 6.4), (mx + s * 2.4, hy + 6.8), (mx + s * 3.8, hy + 5.6)], [0.9, 0.9, 0.4]), (90, 60, 50), sh=None)
    rig_hair(r, draw, (120, 80, 56), "short", hat=True, skin=SKIN)
    _chef_toque(r, draw)
    # the frying pan and its egg: out to his side head-on and from behind, ahead of him in profile
    h = hand_at(ra, hs, reach, 0.0, out)
    ang = (-18 if d > 0 else -162) if d else (-35 if hs > 0 else -145)
    _chef_pan(draw, h[0], h[1], ang)
    rig_hand(ra, draw, hs, SKIN, reach=reach, out=out)


# ===================================================================
# MUSICIAN (108) -- a rocker: dyed spiky hair, studded jacket, a flying-V
# ===================================================================

MUS_JACKET = (48, 44, 58)
MUS_HAIR = (255, 110, 170)
MUS_GUITAR = (230, 46, 70)
MUS_NECK = (234, 200, 146)          # a maple neck: pale, so it shows where it crosses the black jacket
MUS_STRAP = (226, 84, 150)
MUS_STEEL = (220, 222, 232)


def _mus_guitar(draw, gx, gy, ang, flip=1.0):
    """The flying V, as long as he is from chin to sole: (gx, gy) is where its neck meets its body,
    the neck runs along ang, and the body trails the other way: an arrowhead with the V cut into its
    tail (two thin wings from the joint read as a pair of sticks once his hands were over them)."""
    def at(pts):
        return xform(pts, gx, gy, ang, 1.0, flip)

    cel(draw, Poly(at([(0.0, -1.1), (11.6, -0.9), (11.6, 0.9), (0.0, 1.1)])), MUS_NECK, sh=None, lw=0.7)
    cel(draw, Poly(at([(11.2, -1.3), (13.8, -2.7), (15.8, 0.0), (13.8, 2.7), (11.2, 1.3)])), MUS_GUITAR, sh=None, lw=0.7)
    body = Poly(at([(2.2, -1.6), (-10.4, -6.2), (-11.8, -3.4), (-7.4, 0.0), (-11.8, 3.4), (-10.4, 6.2), (2.2, 1.6)]))
    cel(draw, body, MUS_GUITAR, sh=(0.7, 0.7), hi=(0.5, 0.5), regions=[
        (Poly(at([(-6.6, -1.7), (2.4, -1.7), (2.4, 1.7), (-6.6, 1.7)])), (250, 250, 252)),
        (Poly(at([(-1.5, -1.7), (-0.1, -1.7), (-0.1, 1.7), (-1.5, 1.7)])), (52, 48, 62)),
        (Poly(at([(-5.0, -1.7), (-3.6, -1.7), (-3.6, 1.7), (-5.0, 1.7)])), (52, 48, 62))])
    stroke(draw, at([(-5.4, 0.0), (11.4, 0.0)]), 0.5, (150, 150, 166))


def _mus_note(draw, x, y):
    """An eighth note with its head on (x, y): one mass, the colour of his hair."""
    blob(draw, [Ell(x, y, 1.7, 1.3), Poly([(x + 0.7, y + 0.2), (x + 0.7, y - 5.2), (x + 1.7, y - 5.2), (x + 1.7, y + 0.2)]),
                Poly([(x + 1.2, y - 5.2), (x + 3.6, y - 3.4), (x + 3.4, y - 1.9), (x + 1.2, y - 3.6)])], MUS_HAIR, sh=None, lw=0.6)


def _mus_arm(r, draw, side, hand):
    """A jacket sleeve to a hand that is on the guitar, wherever the guitar is."""
    s = r.shoulder(side)
    e = ((s[0] + hand[0]) / 2.0 + (side * 1.2 if not r.d else -r.d * 0.6), (s[1] + hand[1]) / 2.0 + 1.2)
    cel(draw, Limb([s, e, hand], [2.15, 1.95, 1.75]), MUS_JACKET if r.near(side) else shade(MUS_JACKET, 0.6), sh=(0.8, 0.4))


def _mus_hand(r, draw, side, hand):
    cel(draw, Ell(hand[0], hand[1], 2.2, 2.2), SKIN if r.near(side) else shade(SKIN, 0.5), sh=(0.6, 0.6))


def draw_musician(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=0.98)
    d = r.d
    cx, base = r.cx, r.base_y
    strum = [0.0, 1.0, 0.0, 1.0][frame]
    # the guitar: played across his front head-on, neck up and ahead of him in profile, and slung on
    # his back, neck up past his shoulder, when he walks away
    if r.back:
        gx, gy, ang, flip = cx - 0.5, base - 17.5, -150.0, -1.0
    elif d:
        gx, gy, ang, flip = cx + d * 2.5, base - 15.0, (-24.0 if d > 0 else -156.0), float(d)
    else:
        gx, gy, ang, flip = cx + 1.0, base - 15.0, -20.0, 1.0
    fret = xform([(7.6, 0.2)], gx, gy, ang, 1.0, flip)[0]
    pick = xform([(-2.4, 0.6 + strum * 1.5)], gx, gy, ang, 1.0, flip)[0]
    if d:
        _mus_arm(r, draw, -d, fret)
    rig_legs(r, draw, (70, 76, 120), (40, 36, 46))
    rig_torso(r, draw, (240, 240, 244))
    # the leather jacket over a white tee
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
    rig_belt(r, draw, (30, 28, 36), buckle=MUS_STEEL)
    if r.back:
        rig_arms(r, draw, MUS_JACKET, SKIN)
        _mus_guitar(draw, gx, gy, ang, flip)
    else:
        # its strap, up over his shoulder
        top = (cx + 4.6, r.sh_y - 2.2) if not d else (cx - d * 1.0, r.sh_y - 2.4)
        cel(draw, Limb([xform([(1.6, -1.2)], gx, gy, ang, 1.0, flip)[0], top], [0.9, 0.9]), MUS_STRAP, sh=None, lw=0.6)
        if d:
            _mus_hand(r, draw, -d, fret)
        else:
            _mus_arm(r, draw, 1, fret)
        _mus_guitar(draw, gx, gy, ang, flip)
        _mus_arm(r, draw, d if d else -1, pick)
        _mus_hand(r, draw, d if d else -1, pick)
        if not d:
            _mus_hand(r, draw, 1, fret)
    # two steel studs on each shoulder
    for side in _sides(r):
        sx, sy = r.shoulder(side)
        for k in (-1.3, 1.3):
            cel(draw, Ell(sx + k + (side * 0.6 if not d else 0.0), sy - 1.0, 0.9, 0.9), MUS_STEEL, sh=None, lw=0.5)
    rig_head(r, draw, SKIN, hair=MUS_HAIR, style="spiky", eye_color=(90, 200, 230), expression="grin" if frame % 2 else "smirk", mood="sharp")
    if not r.back:
        # two notes climbing off the guitar: beside his head, behind it when he walks, never over his face
        for k in range(2):
            t = ((frame + k * 2) % 4) / 4.0
            nx = cx + (18.6 + 3.0 * t) * (1 if not d else -d) - (1.8 if d > 0 else 0.0)
            _mus_note(draw, nx, base - 23.5 - 16.0 * t)


# ===================================================================
# ASTRONOMER (109) -- a crescent-moon circlet, a starry robe, a telescope
# ===================================================================

AST_ROBE = (44, 54, 122)
AST_GOLD = (246, 206, 96)
AST_HAIR = (170, 150, 220)
AST_BRASS = (214, 170, 84)
AST_PLANET = (240, 150, 110)


def _ast_planet(draw, x, y):
    """A ringed planet: the far half of the ring behind the ball, the near half across it."""
    ring = (252, 228, 176)
    stroke(draw, rot_pts(arc_pts(x, y, 4.8, 1.6, 180, 360, 10), x, y, -18), 0.9, shade(ring, 1.4))
    cel(draw, Ell(x, y, 2.7, 2.7), AST_PLANET, sh=(0.6, 0.6), lw=0.7,
        regions=[(Poly(rot_pts([(x - 3.0, y - 0.9), (x + 3.0, y - 0.9), (x + 3.0, y - 0.1), (x - 3.0, y - 0.1)], x, y, -18)), lit(AST_PLANET, 1.2))])
    stroke(draw, rot_pts(arc_pts(x, y, 4.8, 1.6, 0, 180, 10), x, y, -18), 0.9, ring)


def _ast_orbit(r, frame):
    """Where the planet is this frame, and whether it is on the camera's side of her head. It circles
    the head at its widest, a quarter turn a frame, and starts on the side away from the telescope:
    never across the face."""
    if r.d:
        a0, rx = (135.0 if r.d > 0 else 45.0), 23.5
    else:
        a0, rx = (45.0 if r.back else 135.0), 19.5
    a = math.radians(a0 + frame * 90.0)
    return r.cx + math.cos(a) * rx, r.head_cy - 1.0 + math.sin(a) * 4.5, math.sin(a) > 0


def _ast_telescope(draw, x, y, ang, lens=True, glint=False):
    """A brass spyglass gripped at (x, y) and pointing along ang: three draw-tubes, each wider than
    the last, a dark ring at every joint, glass in the big end."""
    a = math.radians(ang)
    k = 1.0 if math.sin(a) - math.cos(a) < 0 else -1.0      # which long side is away from the light

    def seg(u0, u1, hw, col):
        body = Poly(xform([(u0, -hw), (u1, -hw), (u1, hw), (u0, hw)], x, y, ang))
        low = Poly(xform([(u0, k * 0.3), (u1, k * 0.3), (u1, k * hw), (u0, k * hw)], x, y, ang))
        cel(draw, body, col, sh=None, regions=[(low, shade(col, 1.0))], lw=0.8)

    seg(-4.8, 2.0, 1.3, AST_BRASS)
    seg(1.6, 7.6, 2.0, AST_BRASS)
    seg(7.2, 13.4, 2.7, AST_BRASS)
    for u, hw in ((1.8, 2.4), (7.4, 3.1), (13.0, 3.3)):
        seg(u - 0.6, u + 0.6, hw, shade(AST_BRASS, 1.8))
    stroke(draw, xform([(8.4, -k * 1.5), (11.8, -k * 1.5)], x, y, ang), 0.6, lit(AST_BRASS, 1.6))
    if lens:
        cel(draw, Poly(xform(arc_pts(13.9, 0.0, 1.3, 2.7, 0, 360, 16)[:-1], x, y, ang)), (176, 226, 255), sh=None, lw=0.7,
            line_color=shade(AST_BRASS, 2.6))
        g = xform([(14.2, -k * 0.9)], x, y, ang)[0]
        Ell(g[0], g[1], 0.7, 0.7).draw(draw, fill=(255, 255, 255))
        if glint:
            p = xform([(16.6, -k * 2.2)], x, y, ang)[0]
            sparkle(draw, p[0], p[1], 1.9, (255, 250, 200))


def draw_astronomer(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=0.96)
    d = r.d
    hs = _hold_side(r)
    px, py, planet_near = _ast_orbit(r, frame)
    if not planet_near:
        _ast_planet(draw, px, py)
    rig_hair(r, draw, AST_HAIR, "long", layer="back")
    if d:
        rig_arms(r, draw, AST_ROBE, SKIN, layer="far", cuff=AST_GOLD)
    rig_legs(r, draw, shade(AST_ROBE, 1.0), (60, 50, 70))
    # the hem is short enough to show her shoes, so the walk shows
    rig_robe(r, draw, AST_ROBE, trim=AST_GOLD, flare=3.4, hem=r.base_y - 4.2)
    cx = r.cx
    for (u, v, k) in ((-3.8, 1.8, 1.7), (3.8, 4.6, 1.3), (-2.8, 11.2, 1.5), (3.6, 12.8, 1.8)):
        if d and abs(u) > 3.7:
            continue
        star(draw, cx + u, r.sh_y + v, k, AST_GOLD)
    rig_belt(r, draw, AST_GOLD, buckle=(200, 220, 255))
    rig_arms(r, draw, AST_ROBE, SKIN, layer="near", cuff=AST_GOLD, hands=False, reach=0.4)
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    e1, e2, ey, _, _ = face_anchor(r)
    head_skull(r, draw, SKIN)
    if not r.back:
        head_face(r, draw, SKIN, iris=(110, 110, 210), mood="bright", mouth="smile", brows=False)
        if d:
            # round spectacles, under the hair in profile so the curtain of it covers their far edge
            layer = draw.sub()
            Ell(e1 + d * 0.3, ey, 2.9, 3.1).draw(layer, fill=None, outline=AST_GOLD, width=0.8)
            Ell(e2, ey, 2.1, 3.0).draw(layer, fill=None, outline=AST_GOLD, width=0.8)
            draw.merge(layer)
    rig_hair(r, draw, AST_HAIR, "long", skin=SKIN)
    if r.back:
        # from behind: the long hair falls in three locks, held by a gold star
        for s in (-1, 1):
            Poly([(hx + s * 4.6, hy + 6.0), (hx + s * 5.4, hy + 6.0), (hx + s * 4.4, hy + 19.4), (hx + s * 2.6, hy + 19.4)]).draw(
                draw, fill=shade(AST_HAIR))
        star(draw, hx, hy + 9.6, 3.0, AST_GOLD, line=ink(AST_GOLD))
    elif not d:
        layer = draw.sub()
        for ex in (e1, e2):
            Ell(ex, ey, 3.0, 2.9).draw(layer, fill=None, outline=AST_GOLD, width=0.8)
        draw.merge(layer)
        stroke(draw, [(e1 + 2.6, ey - 0.6), (e2 - 2.6, ey - 0.6)], 0.7, AST_GOLD)
    if not r.back:
        # a crescent moon circlet
        mx, my = hx + d * 3.0, hy - ry + 1.0
        layer = draw.sub()
        cel(layer, Ell(mx, my, 3.4, 3.4), AST_GOLD, sh=None)
        Ell(mx + 1.6, my - 1.0, 2.8, 2.8).draw(layer, fill=CLEAR)
        draw.merge(layer)
    # a brass telescope held up to the sky, the hand closed round it
    h = hand_at(r, hs, reach=0.4)
    ang = -90 + hs * 36 if not d else (-32 if d > 0 else -148)
    _ast_telescope(draw, h[0], h[1], ang, lens=not r.back, glint=frame % 2 == 0)
    for side in _sides(r):
        rig_hand(r, draw, side, SKIN, reach=0.4)
    if planet_near:
        _ast_planet(draw, px, py)


# ===================================================================
# RUNESMITH (110) -- rune tattoos, a rune hammer, a floating runestone
# ===================================================================

RUNE_CLOAK = (70, 92, 134)
RUNE_GLOW = (100, 232, 255)
RUNE_STONE = (112, 116, 138)
RUNE_HAIR = (210, 118, 66)


def _rune(draw, x, y, k, col, w=1.2):
    """His rune, k units tall: a stave, a bow on its right, a tick on its left."""
    h = k / 2.0
    stroke(draw, [(x, y - h), (x, y + h)], w, col)
    stroke(draw, [(x, y - h), (x + k * 0.34, y - h * 0.36), (x, y + h * 0.2)], w, col)
    stroke(draw, [(x - k * 0.32, y + h * 0.26), (x, y + h * 0.74)], w, col)


def _runestone(draw, x, y, frame, out):
    """A standing stone as tall as his head, afloat: taller than it is wide and dark enough for its
    rune to glow, so it is not a shield on his arm. `out` is the side away from him (-1 or +1)."""
    pts = [(x - 3.2, y - 6.6), (x + 1.6, y - 7.2), (x + 3.9, y - 3.8), (x + 3.5, y + 4.4), (x + 0.6, y + 6.6), (x - 3.4, y + 5.2), (x - 4.1, y - 1.0)]
    # a chip of it hanging underneath, turning as it bobs
    cx2, cy2 = x - out * 1.2, y + 9.6 + [0.0, 0.5, 0.0, -0.5][frame]
    cel(draw, Poly(rot_pts([(cx2 - 1.5, cy2 - 0.9), (cx2 + 1.2, cy2 - 1.2), (cx2 + 1.6, cy2 + 0.7), (cx2 - 0.6, cy2 + 1.3)], cx2, cy2, frame * 20.0)),
        RUNE_STONE, sh=(0.4, 0.4), lw=0.6)
    cel(draw, Poly(pts), RUNE_STONE, sh=(1.0, 0.9), hi=(0.5, 0.5))
    _rune(draw, x - 0.2, y - 0.3, 8.4, RUNE_GLOW if frame % 2 == 0 else lit(RUNE_GLOW, 0.6))
    if frame % 2 == 0:
        sparkle(draw, x + out * 3.4, y - 7.8, 1.7, RUNE_GLOW)


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
        for t in (0.34, 0.74):
            p = (s[0] + (w[0] - s[0]) * t, s[1] + (w[1] - s[1]) * t)
            stroke(draw, [(p[0] - 1.1, p[1] - 0.9), (p[0], p[1] + 0.5), (p[0] + 1.1, p[1] - 0.9)], 0.8, RUNE_GLOW)
    # a mantle over the shoulders, its edge worked in the same glow
    mantle = Poly([(cx - 8.4, r.sh_y - 2.6), (cx + 8.4, r.sh_y - 2.6), (cx + 6.0, r.sh_y + 2.6), (cx, r.sh_y + 4.0), (cx - 6.0, r.sh_y + 2.6)]) \
        if not d else Poly([(cx - 5.4, r.sh_y - 2.6), (cx + 5.8, r.sh_y - 2.6), (cx + 4.0, r.sh_y + 3.0), (cx - 4.4, r.sh_y + 3.0)])
    cel(draw, mantle, RUNE_CLOAK, sh=(0.6, 0.6), line=False)

    def trim(layer):
        mantle.draw(layer, fill=RUNE_GLOW)
        mantle.shift(0.0, -1.1).draw(layer, fill=CLEAR)
    clipped(draw, mantle, trim)
    mantle.draw(draw, fill=None, outline=ink(RUNE_CLOAK), width=1.0)
    rig_head(r, draw, SKIN_TAN, hair=RUNE_HAIR, style="swept", eye_color=(60, 200, 230), expression="set", mood="sharp")
    hx, hy, rx = r.hx, r.head_cy, r.head_rx
    # a leather headband with a glowing stone set in it
    cel(draw, RRect(hx - rx - 0.6, hy - 5.8, hx + rx + 0.6, hy - 3.4, 1.0), LEATHER, sh=(0.0, 0.5))
    if not r.back:
        gx = hx + d * 4.0
        cel(draw, Poly([(gx, hy - 6.6), (gx + 1.7, hy - 4.6), (gx, hy - 2.6), (gx - 1.7, hy - 4.6)]), RUNE_GLOW, sh=None, lw=0.6)
    rig_cape(r, draw, RUNE_CLOAK, layer="over")
    if r.back:
        # his rune worked large into the back of the cloak
        _rune(draw, cx + 0.2 + r.ph * 0.6, r.sh_y + 7.4, 10.0, RUNE_GLOW, 1.4)
    # the runestone floating at his off side, clear of his head
    os_ = -hs if not d else -d
    _runestone(draw, cx + os_ * (17.6 if not d else 18.2), r.sh_y - (5.0 if not d else 6.4) + bob2, frame, os_)
    # a rune hammer: a stone mallet banded with the glow
    h = hand_at(r, hs)
    ang = -90 + (hs * 30 if not d else d * 56)
    cel(draw, Limb(xform([(-2.4, 0.0), (10.6, 0.0)], h[0], h[1], ang), [0.95, 0.95]), WOOD, sh=None)
    cel(draw, Poly(xform([(8.6, -3.9), (13.8, -3.9), (13.8, 3.9), (8.6, 3.9)], h[0], h[1], ang)), RUNE_STONE, sh=None,
        regions=[(Poly(xform([(10.4, -4.0), (12.0, -4.0), (12.0, 4.0), (10.4, 4.0)], h[0], h[1], ang)), RUNE_GLOW)])
    for side in _sides(r):
        rig_hand(r, draw, side, SKIN_TAN)


# ===================================================================
# SHAPESHIFTER (111) -- caught halfway: a beast arm, one wolf ear, a tail
# ===================================================================

SS_CLOAK = (120, 70, 170)
SS_FUR = (126, 100, 84)
SS_FUR_PALE = (222, 204, 180)
SS_HAIR = (80, 60, 110)
SS_MAGIC = (220, 150, 255)


def _ss_tail(r, draw, wag, side):
    """A wolf's brush with a pale tip: curling out on `side` head-on and from behind, behind it in profile."""
    cx, d, y = r.cx, r.d, r.hip_y
    if d:
        pts = bez((cx - d * 3.0, y - 0.4), (cx - d * 15.5, y + 5.0 + wag * 1.6), (cx - d * 13.0, y - 7.6 + wag * 0.4), 8)
    else:
        pts = bez((cx + side * 2.0, y + 1.0), (cx + side * 15.6, y + 7.4 + wag * 1.6), (cx + side * 14.0, y - 5.0 + wag * 0.4), 8)
    tip = pts[-1]
    cel(draw, Limb(pts, [1.8, 2.3, 2.8, 3.2, 3.5, 3.5, 3.2, 2.5, 1.2]), SS_FUR, sh=(0.8, 0.8),
        regions=[(Ell(tip[0], tip[1] + 0.4, 4.4, 3.8), SS_FUR_PALE)])


def _ss_ear(draw, x, y, lean, inner=True):
    """A wolf's ear standing in the hair on (x, y), leaning `lean` (-1 to the left, +1 to the
    right): broad at the base, leaf-shaped, dark at the tip and with a tuft of hair over its root,
    so it is an ear growing out of the head and not a horn stuck on it."""
    ang = lean * 22.0
    outer = [(x - 4.4, y + 1.0), (x - 4.2, y - 3.8), (x - 0.8, y - 10.4), (x + 2.4, y - 6.4), (x + 4.4, y - 1.6), (x + 4.4, y + 1.0)]
    dark = [(x - 4.4, y - 11.5), (x + 4.4, y - 11.5), (x + 4.4, y - 7.8), (x - 4.4, y - 7.8)]
    cel(draw, Poly(rot_pts(outer, x, y, ang)), SS_FUR, sh=(0.7, 0.5), regions=[(Poly(rot_pts(dark, x, y, ang)), shade(SS_FUR, 2.4))])
    if inner:
        pink = [(x - 2.3, y - 0.6), (x - 2.2, y - 3.6), (x - 0.5, y - 7.0), (x + 1.3, y - 4.6), (x + 2.4, y - 0.6)]
        Poly(rot_pts(pink, x, y, ang)).draw(draw, fill=(240, 176, 186))
    tuft = rot_pts([(x - 5.4, y + 1.6), (x - 4.6, y - 1.2), (x - 2.6, y - 2.6), (x - 1.4, y - 0.4), (x + 0.6, y - 2.2), (x + 2.0, y - 0.2),
                    (x + 3.8, y - 1.6), (x + 5.4, y + 1.6)], x, y, ang * 0.5)
    Poly(tuft).draw(draw, fill=SS_HAIR)
    stroke(draw, tuft[1:-1], 0.9, ink(SS_HAIR))


def _ss_beast_arm(r, draw, side):
    """The arm that has already turned: twice as thick as the other, hanging lower, a paw with three claws."""
    near = r.near(side)
    fur = SS_FUR if near else shade(SS_FUR, 0.6)
    s, e, w, h = arm_pts(r, side, 0.0, -2.0, 1.2 if (near or not r.d) else 2.6)
    vx, vy = h[0] - e[0], h[1] - e[1]
    n = math.hypot(vx, vy) or 1.0
    vx, vy = vx / n, vy / n
    for k in (-1, 0, 1):
        bx, by = h[0] + vx * 2.4 - vy * k * 1.8, h[1] + vy * 2.4 + vx * k * 1.8
        tip = (bx + vx * 2.6 - vy * k * 0.6, by + vy * 2.6 + vx * k * 0.6)
        cel(draw, Poly([(bx - vy * 0.9, by + vx * 0.9), tip, (bx + vy * 0.9, by - vx * 0.9)]), TOOTH, sh=None, lw=0.5)
    k = 0.84 if r.d else 1.0      # in profile it lies across the body: leave some of the tunic showing
    blob(draw, [Limb([s, e, w], [2.8 * k, 3.3 * k, 3.4 * k]), Ell(s[0], s[1] - 0.2, 3.4 * k, 2.8 * k)], fur, sh=(0.8, 0.5))
    cel(draw, Ell(h[0], h[1], 3.7 * (0.92 if r.d else 1.0), 3.5 * (0.92 if r.d else 1.0)), fur, sh=(0.6, 0.6))


def _ss_sparkles(draw, r, frame, front):
    """The changing magic, on a ring round the waist: the far half of it goes behind the body."""
    for i in range(3):
        a = math.radians(frame * 90 + i * 120 + 30)
        if (math.sin(a) > 0.01) != front:
            continue
        sparkle(draw, r.cx + math.cos(a) * 15.5, r.waist_y + 1.0 + math.sin(a) * 4.5, 1.9 if i == 0 else 1.5, SS_MAGIC)


def draw_shapeshifter(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame)
    d = r.d
    # the beast is its left half: the camera's right head-on, the camera's left from behind, the
    # near side walking left and the far side walking right
    beast = 1 if r.front else -1
    wag = [0.0, 1.0, 0.0, -1.0][frame]
    cx = r.cx
    _ss_sparkles(draw, r, frame, False)
    if not r.back:
        _ss_tail(r, draw, wag, -beast)
    rig_cape(r, draw, SS_CLOAK, layer="under", length=r.hip_y + 4.0)

    def arm(side):
        if side == beast:
            _ss_beast_arm(r, draw, side)
        else:
            rig_arms(r, draw, SS_CLOAK, SKIN, sides=(side,))

    if d:
        arm(-d)
    # one leg in breeches and a boot, the other a wolf's
    for side in ((-d, d) if d else (-1, 1)):
        if side == beast:
            rig_legs(r, draw, SS_FUR, bare=True, sides=(side,), width=1.12)
            fx, fy = r.foot(side)
            if r.near(side) and not r.back:
                for k in ((-1, 0, 1) if not d else (0, 1)):
                    tx = fx + (k * 1.5 if not d else d * (3.0 - k * 1.3))
                    Poly([(tx - 0.6, fy + 0.2), (tx + 0.6, fy + 0.2), (tx + d * 0.7, fy + 1.6)]).draw(draw, fill=TOOTH)
        else:
            rig_legs(r, draw, (80, 60, 100), sides=(side,))
    rig_torso(r, draw, (90, 70, 120))
    rig_belt(r, draw, (60, 44, 60), buckle=SS_MAGIC)
    for side in ((d,) if d else (-1, 1)):
        arm(side)
    head_skull(r, draw, SKIN)
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    if not r.back:
        e1, e2, ey, mx, my = face_anchor(r)
        pairs = ((-1, e1), (1, e2)) if not d else ((-d, e1), (d, e2))
        for side, ex in pairs:
            near_eye = not d or side == -d
            is_beast = (side == beast) if not d else (near_eye == (d < 0))
            ms_eye(draw, ex, ey, 4.2 if near_eye else 3.0, 5.6, (250, 200, 50) if is_beast else (110, 80, 170), (float(d), 0.0),
                   "sharp" if is_beast else "bright", skin=SKIN, side=side)
        ms_mouth(draw, mx, my, "fangs", SKIN)
    rig_hair(r, draw, SS_HAIR, "wild", skin=SKIN)
    # one wolf's ear, standing out of the hair on the beast's side
    if d:
        _ss_ear(draw, hx - d * 3.4, hy - ry - 1.6, -d * 0.5)
    else:
        _ss_ear(draw, hx + beast * 8.4, hy - ry + 0.8, beast, inner=not r.back)
    rig_cape(r, draw, SS_CLOAK, layer="over", length=r.hip_y + 4.0)
    if r.back:
        _ss_tail(r, draw, wag, -beast)
    _ss_sparkles(draw, r, frame, True)


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
