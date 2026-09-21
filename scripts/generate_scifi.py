#!/usr/bin/env python3
"""Sci-Fi/Tech character sprite generators (IDs 57-71).

Fifteen characters from the future, in MapleStory's style: cute before they
are cold. The machines (android, mech, nanoswarm, sentinel) have faces made of
light; the people are told apart by their tech -- a cannon arm, headphones and
a holo-screen, a clock-face halo, orbiting spheres, a tesla coil, a railgun,
a bomb with a lit fuse, a leather flying cap -- and the two things that are
not quite people (voidwalker, photon, glitcher) by what they are made of.
"""

import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from sprite_base import (
    DOWN, UP, LEFT, RIGHT, GOLD, LEATHER, STEEL, WOOD, SKIN, SKIN_TAN, HAIR_STYLES,
    Ell, Limb, Poly, RRect, arc_pts, blade, blob, bolt, cel, cloud, crystal, flame, gem,
    generate_character, hand_at, head_face, head_skull, ink, lit, mix, ms_eye, ms_mouth, rig,
    rig_arms, rig_belt, rig_cape, rig_hair, rig_hand, rig_head, rig_hood, rig_legs, rig_robe,
    rig_torso, shade, sparkle, star, stroke, xform, arm_pts, face_anchor,
)


def _sides(r):
    return (-1, 1) if not r.d else (r.d,)


def _hold_side(r):
    return (1 if not r.back else -1) if not r.d else r.d


def _dir(direction):
    return {DOWN: 0, UP: 0, LEFT: -1, RIGHT: 1}[direction], direction == UP


def visor_eyes(draw, x, y, w, color, d=0, happy=False):
    """Two glowing eyes on a visor: bars, or ^ ^ when happy."""
    for s in ((-1, 1) if not d else (d, -d)):
        k = 1.0 if not d or s == d else 0.6
        ex = x + s * w * (1 if not d else 0.8)
        if happy:
            stroke(draw, [(ex - 1.6 * k, y + 0.6), (ex, y - 0.8), (ex + 1.6 * k, y + 0.6)], 0.9, color)
        else:
            cel(draw, RRect(ex - 1.5 * k, y - 1.3, ex + 1.5 * k, y + 1.3, 0.8), color, sh=None, line=False)
            Ell(ex - 0.4 * k, y - 0.4, 0.5, 0.5).draw(draw, fill=(255, 255, 255))


# ===================================================================
# CYBORG (57) -- half a metal face, a red cyber-eye, a cannon for an arm
# ===================================================================

CYB_SUIT = (58, 60, 72)
CYB_METAL = (176, 184, 200)
CYB_RED = (255, 60, 56)
CYB_ORANGE = (252, 150, 60)


def draw_cyborg(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=1.12)
    d = r.d
    arm_side = (1 if not r.back else -1) if not d else d
    if d:
        # in profile the flesh arm is the far one, behind the body
        rig_arms(r, draw, CYB_SUIT, SKIN, layer="far")
    rig_legs(r, draw, CYB_SUIT, CYB_METAL)
    rig_torso(r, draw, CYB_SUIT)
    cx = r.cx
    if not r.back:
        cel(draw, RRect(cx - 3.6 + d * 1.8, r.sh_y - 0.4, cx + 3.6 + d * 1.8, r.sh_y + 4.6, 1.2), CYB_METAL, sh=(0.6, 0.6))
        cel(draw, Ell(cx + d * 1.8, r.sh_y + 2.1, 1.4, 1.4), CYB_ORANGE if frame % 2 else lit(CYB_ORANGE, 1.2), sh=None)
    rig_belt(r, draw, (40, 40, 50), buckle=CYB_ORANGE)
    # the flesh arm
    other = -arm_side if not d else -d
    if not d:
        rig_arms(r, draw, CYB_SUIT, SKIN, sides=(other,))
    head_skull(r, draw, SKIN)
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    if not r.back:
        head_face(r, draw, SKIN, iris=(90, 110, 140), mood="sharp", mouth="set", brows=True, brow_color=(70, 60, 70))
    # the metal half of the head, over the face's cannon side
    ms = arm_side if not d else (1 if d > 0 else -1)
    if not r.back and (not d or d == ms):
        if d:
            plate = Poly([(hx + d * 0.2, hy - ry - 0.2), (hx + d * (rx + 0.4), hy - 3.0), (hx + d * (rx + 0.2), hy + 4.6),
                          (hx + d * 5.6, hy + 9.4), (hx + d * 0.6, hy + 7.4), (hx - d * 1.6, hy + 1.0)])
            ex = hx + d * 7.6
        else:
            plate = Poly([(hx, hy - ry - 0.4), (hx + ms * (rx + 0.6), hy - 4.0), (hx + ms * (rx + 0.4), hy + 5.0),
                          (hx + ms * 7.0, hy + 10.0), (hx + ms * 0.8, hy + 9.0), (hx - ms * 0.6, hy + 3.0)])
            ex = hx + ms * 4.7
        cel(draw, plate, CYB_METAL, sh=(0.8, 0.8), hi=(0.5, 0.5))
        Ell(ex, hy + 2.3, 2.6, 2.6).draw(draw, fill=(40, 30, 40))
        Ell(ex, hy + 2.3, 1.6, 1.6).draw(draw, fill=CYB_RED)
        Ell(ex - 0.5, hy + 1.8, 0.5, 0.5).draw(draw, fill=(255, 230, 230))
        for k in (-3.0, 3.4):
            Ell(hx + ms * 7.8 if not d else hx + d * 9.0, hy + k, 0.5, 0.5).draw(draw, fill=shade(CYB_METAL, 1.6))
    hair_col = (66, 60, 80)
    rig_hair(r, draw, hair_col, "spiky", skin=SKIN)
    if r.back:
        cel(draw, Poly([(hx + arm_side * 1.0, hy - ry), (hx + arm_side * (rx + 0.4), hy - 3.0), (hx + arm_side * (rx - 0.6), hy + 6.0),
                        (hx + arm_side * 1.0, hy + 8.0)]), CYB_METAL, sh=(0.8, 0.8))
    # the cannon arm
    sx, sy = r.shoulder(arm_side)
    cel(draw, Ell(sx + (arm_side * 1.2 if not d else 0), sy - 0.4, 4.4, 3.6), CYB_METAL, sh=(0.8, 0.8), hi=(0.5, 0.5))
    h = hand_at(r, arm_side, 0.5 if d else 0.2)
    bx, by = h[0], h[1] - 2.0
    ang = 90 if not d else (10 if d > 0 else 170)
    if not d:
        ang = 90 - arm_side * 12
    body = xform([(-6.0, -2.8), (4.0, -2.8), (5.0, -3.6), (8.4, -3.6), (8.4, 3.6), (5.0, 3.6), (4.0, 2.8), (-6.0, 2.8)], bx, by, ang)
    cel(draw, Poly(body), CYB_METAL, sh=None, regions=[(Poly(xform([(-6.0, 0.0), (8.4, 0.0), (8.4, 3.6), (-6.0, 3.6)], bx, by, ang)),
                                                         shade(CYB_METAL, 0.9))])
    muzzle = xform([(8.4, 0.0)], bx, by, ang)[0]
    cel(draw, Ell(muzzle[0], muzzle[1], 2.4, 2.4), shade(CYB_METAL, 1.4), sh=None)
    Ell(muzzle[0], muzzle[1], 1.4, 1.4).draw(draw, fill=CYB_ORANGE if frame % 2 == 0 else CYB_RED)
    for t in (-2.6, 0.0):
        p = xform([(t, -2.9)], bx, by, ang)[0]
        Ell(p[0], p[1], 0.6, 0.6).draw(draw, fill=CYB_ORANGE)


# ===================================================================
# HACKER (58) -- hoodie, headphones, a holo-screen of green code
# ===================================================================

HAK_HOOD = (54, 58, 72)
HAK_GREEN = (86, 255, 150)
HAK_PHONES = (220, 60, 90)


def draw_hacker(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=0.96)
    d = r.d
    if d:
        rig_arms(r, draw, HAK_HOOD, SKIN, layer="far", reach=0.6)
    rig_legs(r, draw, (70, 76, 100), (240, 240, 244))
    rig_torso(r, draw, HAK_HOOD, bottom=r.hip_y + 1.6)
    cx = r.cx
    if not r.back:
        cel(draw, RRect(cx - 3.6 + d * 1.6, r.waist_y - 1.0, cx + 3.6 + d * 1.6, r.waist_y + 2.6, 1.0), shade(HAK_HOOD, 0.7), sh=None)
        for s in (-1, 1):
            stroke(draw, [(cx + s * 1.6 + d * 1.6, r.sh_y - 1.6), (cx + s * 1.8 + d * 1.6, r.sh_y + 3.6)], 0.5, HAK_GREEN)
    rig_arms(r, draw, HAK_HOOD, SKIN, layer="near", reach=0.6)
    rig_head(r, draw, SKIN, hair=(86, 70, 110), style="swept", eye_color=(60, 190, 120), expression="smirk", hat=True)
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    # round glasses reflecting code
    if not r.back:
        e1, e2, ey, _, _ = face_anchor(r)
        for ex in ((e1, e2) if not d else (e1,)):
            layer = draw.sub()
            Ell(ex, ey, 3.0, 2.8).draw(layer, fill=None, outline=(40, 40, 50), width=0.7)
            draw.merge(layer)
            stroke(draw, [(ex - 1.8, ey - 1.0), (ex - 0.4, ey - 2.0)], 0.5, (230, 255, 240))
        if not d:
            stroke(draw, [(e1 + 3.0, ey - 0.4), (e2 - 3.0, ey - 0.4)], 0.6, (40, 40, 50))
    # hood up, pushed back, with headphones over it
    from sprite_base import hood_shape, hood_opening
    rig_hood(r, draw, HAK_HOOD, opening=1.12, drape=False)
    for s in ((-1, 1) if not d else (-d,)):
        px = hx + s * (rx + 0.6) if not d else hx - d * 2.6
        cel(draw, Ell(px, hy + 1.6, 2.6, 3.4), HAK_PHONES, sh=(0.5, 0.5))
        Ell(px, hy + 1.6, 1.0, 1.6).draw(draw, fill=HAK_GREEN)
    band = arc_pts(hx - d * 0.6, hy + 1.0, rx + 1.2, ry + 2.0, 196, 344, 18) if not d else \
        arc_pts(hx - d * 2.6, hy + 1.6, 3.0, ry + 2.6, 250, 290, 6)
    stroke(draw, band, 1.2, shade(HAK_PHONES, 1.2))
    # a floating holo-screen scrolling code
    if not r.back:
        sx = r.cx + (0.0 if not d else d * 9.0)
        sy = r.waist_y - 1.0
        w, h = (8.0, 4.6) if not d else (3.2, 5.0)
        cel(draw, RRect(sx - w, sy - h, sx + w, sy + h, 0.8), (26, 70, 50), sh=None, line_color=HAK_GREEN, lw=0.6)
        for i in range(3):
            y = sy - h + 1.6 + i * 2.4
            ln = [5.0, 3.6, 6.0, 4.2][(frame + i) % 4] * (w / 8.0)
            stroke(draw, [(sx - w + 1.4, y), (sx - w + 1.4 + ln, y)], 0.6, HAK_GREEN)


# ===================================================================
# MECH PILOT (59) -- a pilot's head in the cockpit of a chunky mech
# ===================================================================

MECH_Y = (242, 196, 62)
MECH_G = (104, 108, 122)
MECH_DARK = (60, 62, 74)
MECH_GLOW = (110, 220, 255)


def draw_mechpilot(draw, ox, oy, direction, frame):
    d, back = _dir(direction)
    bob = [0, -1, 0, -1][frame]
    ph = [0, 1, 0, -1][frame]
    base = oy + 54 + bob
    cx = ox + 32
    # legs: hydraulic, with big feet
    for side in ((-1, 1) if not d else (-d, d)):
        near = (not d) or side == d
        fwd = ph * side if not d else (ph if near else -ph)
        lx = cx + (side * 6.0 if not d else d * fwd * 3.0)
        ly = base - (1.2 if fwd < 0 and not d else 0)
        col = MECH_G if near else shade(MECH_G, 0.6)
        cel(draw, Limb([(lx, base - 13.0), (lx, ly - 3.0)], [2.2, 2.0]), col, sh=(0.6, 0.0))
        foot = RRect(lx - 4.0 + d * 1.0, ly - 3.4, lx + 4.0 + d * 1.4, ly + 0.6, 1.2)
        cel(draw, foot, MECH_Y if near else shade(MECH_Y, 0.6), sh=(0.0, 0.8))
    # the hull
    w = 11.4 if not d else 9.0
    hull = RRect(cx - w, base - 28.0, cx + w, base - 11.0, 3.2)
    cel(draw, hull, MECH_Y, sh=(1.8, 1.4), hi=(1.0, 1.0))
    if not back:
        # hazard stripes and a light
        for k in range(3):
            x = cx - 5.0 + k * 3.6 + d * 2.0
            cel(draw, Poly([(x, base - 14.6), (x + 1.8, base - 14.6), (x + 0.6, base - 12.0), (x - 1.2, base - 12.0)]), MECH_DARK, sh=None, line=False)
        Ell(cx + d * 5.0 + (5.0 if not d else 0), base - 19.0, 1.3, 1.3).draw(draw, fill=MECH_GLOW)
    else:
        for k in (-4.0, 4.0):
            cel(draw, RRect(cx + k - 2.2, base - 26.0, cx + k + 2.2, base - 16.0, 1.0), MECH_G, sh=(0.4, 0.4))
            flame(draw, cx + k, base - 13.0 + 3.6, 3.0, 4.0 + [0, 1, 0, 1][frame], ((120, 200, 255), (190, 240, 255), (255, 255, 255)), flip=-1)
    # the cockpit: an open ring with the pilot's head in it
    hx, hy = cx + d * 2.0, base - 32.0
    cel(draw, Ell(hx, hy + 2.0, 10.6, 8.4), MECH_G, sh=(1.0, 1.0))
    cel(draw, Ell(hx, hy + 2.0, 8.8, 6.8), MECH_DARK, sh=None, line=False)
    r = rig(ox, oy, direction, frame, head=0.82)
    r.head_cy = hy - 1.0
    r.hx = hx
    head_skull(r, draw, SKIN, ears=False)
    if not back:
        head_face(r, draw, SKIN, iris=(80, 120, 170), mood="bright", mouth="grin", brows=False)
    # flight helmet and goggles
    hxx, hyy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    dome = arc_pts(hxx - d * 0.4, hyy - 0.6, rx + 1.0, ry + 0.8, 182, 358, 22)
    cel(draw, Poly(dome + [(hxx + rx + 1.0, hyy - 1.4), (hxx - rx - 1.0, hyy - 1.4)]), (238, 240, 246), sh=(1.2, 0.8))
    if not back:
        for s in ((-1, 1) if not d else (d,)):
            gx = hxx + s * 3.4 + d * 1.6
            cel(draw, Ell(gx, hyy - 4.0, 2.2, 1.9), MECH_DARK, sh=None)
            Ell(gx, hyy - 4.0, 1.4, 1.2).draw(draw, fill=MECH_GLOW)
    # the cockpit rim in front of his chin
    cel(draw, Poly(arc_pts(hx, hy + 2.0, 10.6, 8.4, 20, 160, 14) + arc_pts(hx, hy + 2.0, 8.4, 5.6, 160, 20, 14)), MECH_Y, sh=(0.0, 0.8))
    # arms: a minigun and a claw
    for side in ((-1, 1) if not d else (-d, d)):
        near = (not d) or side == d
        sx = cx + (side * (w + 1.4) if not d else side * 1.0)
        col = MECH_G if near else shade(MECH_G, 0.6)
        cel(draw, Ell(sx, base - 25.0, 4.4, 4.0), MECH_Y if near else shade(MECH_Y, 0.6), sh=(0.8, 0.8))
        fx = sx + (side * 1.4 if not d else d * 3.0)
        fy = base - 12.0
        cel(draw, Limb([(sx, base - 22.0), (fx, fy - 2.0)], [2.2, 2.0]), col, sh=(0.6, 0.0))
        gun_side = 1 if not d else d
        if (not d and side == gun_side) or (d and side == d):
            for k in (-1.3, 0.0, 1.3):
                cel(draw, Limb([(fx + k, fy - 2.0), (fx + k * 1.1 + (0 if not d else d * 4.0), fy + 6.0 - (0 if not d else 4.0))],
                               [0.7, 0.7]), MECH_DARK, sh=None, lw=0.5)
            cel(draw, RRect(fx - 2.8, fy - 3.6, fx + 2.8, fy + 0.2, 1.0), col, sh=None)
        else:
            cel(draw, Poly([(fx - 3.0, fy - 2.4), (fx + 3.0, fy - 2.4), (fx + 2.4, fy + 2.4), (fx + 0.6, fy + 0.4), (fx - 0.6, fy + 0.4),
                            (fx - 2.4, fy + 2.4)]), col, sh=None)


# ===================================================================
# ANDROID (60) -- white plating, a visor that smiles, jet thrusters
# ===================================================================

AND_WHITE = (242, 246, 252)
AND_CYAN = (80, 222, 255)
AND_JOINT = (70, 76, 96)


def draw_android(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=0.96)
    d = r.d
    cx = r.cx
    # jets on the back
    if r.back or d:
        jx = cx - (d * 4.4 if d else 0.0)
        for k in ((-3.0, 3.0) if not d else (0.0,)):
            cel(draw, RRect(jx + k - 1.8, r.sh_y - 2.4, jx + k + 1.8, r.waist_y + 1.6, 1.0), (200, 208, 224), sh=(0.4, 0.4))
            flame(draw, jx + k, r.waist_y + 1.8 + 5.0, 2.8, 5.0 + [0, 1.4, 0, 1.4][frame], ((110, 200, 255), (180, 236, 255), (255, 255, 255)), flip=-1)
    if d:
        rig_arms(r, draw, AND_WHITE, AND_WHITE, layer="far")
    rig_legs(r, draw, AND_JOINT, AND_WHITE)
    for side in ((-1, 1) if not d else (-d, d)):
        hx0, hy0 = r.hip(side)
        fx, fy = r.foot(side)
        cel(draw, Limb([(hx0 * 0.4 + fx * 0.6, hy0 * 0.4 + fy * 0.6 - 1.0), (fx, fy - 2.6)], [2.4, 2.4]),
            AND_WHITE if r.near(side) else shade(AND_WHITE, 0.6), sh=(0.4, 0.0))
    rig_torso(r, draw, AND_WHITE)
    if not r.back:
        cel(draw, Ell(cx + d * 1.8, r.sh_y + 2.4, 2.0, 2.0), AND_JOINT, sh=None)
        Ell(cx + d * 1.8, r.sh_y + 2.4, 1.2, 1.2).draw(draw, fill=AND_CYAN if frame % 2 == 0 else lit(AND_CYAN, 1.3))
        stroke(draw, [(cx - 4.4 + d, r.waist_y), (cx + 4.4 + d, r.waist_y)], 0.6, AND_CYAN)
    rig_arms(r, draw, AND_WHITE, AND_WHITE, layer="near", cuff=AND_CYAN)
    # the head: a smooth dome with a visor band that makes its face
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    cel(draw, Ell(hx, hy, rx, ry), AND_WHITE, sh=(1.4, 1.2), hi=(0.8, 0.8))
    for s in ((-1, 1) if not d else (-d,)):
        ex = hx + s * (rx - 0.2) if not d else hx - d * 3.2
        cel(draw, Ell(ex, hy + 1.0, 2.0, 3.2), AND_JOINT, sh=None)
        Ell(ex, hy + 1.0, 0.8, 1.6).draw(draw, fill=AND_CYAN)
    if not r.back:
        vx = hx + d * 3.0
        w = rx - 1.6 if not d else rx * 0.66
        cel(draw, RRect(vx - w, hy - 1.8, vx + w, hy + 4.6, 2.6), (30, 36, 56), sh=None)
        visor_eyes(draw, vx, hy + 1.4, 4.0, AND_CYAN, d, happy=frame in (1, 3))
    # antenna
    ax = hx - d * 2.0 + (4.0 if not d else 0)
    cel(draw, Limb([(ax, hy - ry + 0.6), (ax + 1.0, hy - ry - 3.8)], [0.6, 0.5]), AND_JOINT, sh=None)
    cel(draw, Ell(ax + 1.0, hy - ry - 4.4, 1.2, 1.2), AND_CYAN, sh=None, lw=0.6)


# ===================================================================
# CHRONOMANCER (61) -- a clock-face halo, gear-trimmed robe, hourglass
# ===================================================================

CHR_ROBE = (54, 64, 138)
CHR_BRASS = (232, 184, 84)
CHR_FACE = (246, 240, 222)
CHR_HAIR = (220, 222, 236)


def _clock(draw, x, y, rad, frame):
    cel(draw, Ell(x, y, rad, rad), CHR_BRASS, sh=(0.8, 0.8))
    cel(draw, Ell(x, y, rad - 1.4, rad - 1.4), CHR_FACE, sh=None, line=False)
    for i in range(12):
        a = math.radians(i * 30)
        k = 0.78 if i % 3 else 0.7
        stroke(draw, [(x + math.cos(a) * (rad - 1.9), y + math.sin(a) * (rad - 1.9)),
                      (x + math.cos(a) * (rad - 1.9) * k, y + math.sin(a) * (rad - 1.9) * k)], 0.45 if i % 3 else 0.7, (80, 70, 60))
    a = math.radians(frame * 90 - 90)
    stroke(draw, [(x, y), (x + math.cos(a) * rad * 0.6, y + math.sin(a) * rad * 0.6)], 0.7, (60, 50, 50))
    stroke(draw, [(x, y), (x + math.cos(a * 0.25 + 1.2) * rad * 0.4, y + math.sin(a * 0.25 + 1.2) * rad * 0.4)], 0.9, (60, 50, 50))
    Ell(x, y, 0.6, 0.6).draw(draw, fill=CHR_BRASS)


def draw_chronomancer(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame)
    d = r.d
    hs = _hold_side(r)
    out = 1.6 if not d else 0.4
    hx, hy = r.hx, r.head_cy
    # the clock, rising behind the head like a sun
    if not d:
        _clock(draw, hx, hy - 7.6, 11.6, frame)
    else:
        cel(draw, Ell(hx - d * 5.0, hy - 7.0, 4.6, 11.4), CHR_BRASS, sh=(0.6, 0.6))
        cel(draw, Ell(hx - d * 5.2, hy - 7.0, 3.0, 9.8), CHR_FACE, sh=None, line=False)
        for k in range(5):
            y = hy - 14.0 + k * 3.4
            stroke(draw, [(hx - d * 5.2 - 1.2, y), (hx - d * 5.2 + 1.2, y)], 0.5, (80, 70, 60))
    if d:
        rig_arms(r, draw, CHR_ROBE, SKIN, layer="far", cuff=CHR_BRASS)
    rig_legs(r, draw, shade(CHR_ROBE, 1.0), (60, 50, 70))
    rig_robe(r, draw, CHR_ROBE, trim=CHR_BRASS, flare=3.2)
    cx = r.cx
    if not r.back:
        # a gear on the chest
        gx, gy = cx + d * 1.8, r.sh_y + 2.6
        pts = []
        for i in range(16):
            a = math.radians(i * 22.5)
            k = 2.8 if i % 2 == 0 else 2.1
            pts.append((gx + math.cos(a) * k, gy + math.sin(a) * k))
        cel(draw, Poly(pts), CHR_BRASS, sh=None, lw=0.6)
        Ell(gx, gy, 0.9, 0.9).draw(draw, fill=CHR_ROBE)
    rig_belt(r, draw, CHR_BRASS, buckle=CHR_FACE)
    rig_arms(r, draw, CHR_ROBE, SKIN, layer="near", cuff=CHR_BRASS, hands=False)
    rig_head(r, draw, SKIN, hair=CHR_HAIR, style="swept", eye_color=(210, 160, 60), expression="set", mood="calm")
    # a monocle
    if not r.back:
        e1, e2, ey, _, _ = face_anchor(r)
        ex = e2 if not d else e1
        layer = draw.sub()
        Ell(ex, ey, 3.2, 3.0).draw(layer, fill=None, outline=CHR_BRASS, width=0.8)
        draw.merge(layer)
        stroke(draw, [(ex + 2.6, ey + 2.0), (ex + 3.6, ey + 7.0)], 0.4, CHR_BRASS)
    if not r.back:
        h = hand_at(r, hs, 0.0, 0.0, out)
        x = h[0] + 0.2
        top = r.head_cy - 1.0
        cel(draw, Limb([(x, r.base_y - 1.2), (x, top + 3.0)], [0.9, 0.8]), CHR_BRASS, sh=None)
        # the hourglass on top
        cel(draw, RRect(x - 3.0, top - 5.8, x + 3.0, top - 4.6, 0.4), CHR_BRASS, sh=None)
        cel(draw, RRect(x - 3.0, top + 2.0, x + 3.0, top + 3.2, 0.4), CHR_BRASS, sh=None)
        cel(draw, Poly([(x - 2.4, top - 4.6), (x + 2.4, top - 4.6), (x + 0.4, top - 1.4), (x + 2.4, top + 2.0), (x - 2.4, top + 2.0),
                        (x - 0.4, top - 1.4)]), (220, 240, 255), sh=None, line_color=shade((220, 240, 255), 1.8))
        sand = [0.8, 0.6, 0.4, 0.6][frame]
        Poly([(x - 2.0 * sand, top - 4.0 + (1 - sand) * 2.4), (x + 2.0 * sand, top - 4.0 + (1 - sand) * 2.4), (x, top - 1.6)]).draw(draw, fill=(250, 214, 110))
        Poly([(x - 1.9, top + 1.6), (x + 1.9, top + 1.6), (x, top + 1.6 - (1.2 - sand) * 2.0)]).draw(draw, fill=(250, 214, 110))
    for side in _sides(r):
        rig_hand(r, draw, side, SKIN, out=out if side == hs else 0.0)


# ===================================================================
# GRAVITON (62) -- anti-gravity hair, a glowing-lined suit, orbiting spheres
# ===================================================================

GRV_SUIT = (64, 44, 106)
GRV_GLOW = (190, 130, 255)
GRV_HAIR = (240, 236, 252)


def draw_graviton(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, bob=[-1, -2, -2, -1][frame])
    d = r.d
    orbit = []
    for i in range(3):
        a = math.radians(frame * 30 + i * 120)
        orbit.append((math.sin(a), r.cx + math.cos(a) * 15.0, r.waist_y - 3.0 + math.sin(a) * 4.0, i))

    def sphere(x, y, i):
        col = (GRV_GLOW, (120, 200, 255), (255, 150, 220))[i]
        cel(draw, Ell(x, y, 2.0, 2.0), col, sh=(0.6, 0.6), hi=(0.4, 0.4))

    for (z, x, y, i) in orbit:
        if z < 0:
            sphere(x, y, i)
    if d:
        rig_arms(r, draw, GRV_SUIT, GRV_SUIT, layer="far")
    rig_legs(r, draw, GRV_SUIT, shade(GRV_SUIT, 1.4))
    rig_torso(r, draw, GRV_SUIT)
    cx = r.cx
    if not r.back:
        stroke(draw, [(cx - 3.6 + d, r.sh_y - 1.0), (cx + d * 1.6, r.waist_y), (cx + 3.6 + d, r.sh_y - 1.0)], 0.7, GRV_GLOW)
        cel(draw, Ell(cx + d * 1.6, r.sh_y + 3.4, 1.8, 1.8), GRV_GLOW, sh=None)
        Ell(cx + d * 1.6, r.sh_y + 3.4, 0.8, 0.8).draw(draw, fill=(255, 255, 255))
    rig_belt(r, draw, shade(GRV_SUIT, 1.6), buckle=GRV_GLOW)
    rig_arms(r, draw, GRV_SUIT, GRV_SUIT, layer="near", cuff=GRV_GLOW, reach=0.3)
    # hair floating straight up off the head, weightless
    rig_head(r, draw, (236, 226, 244), hair=GRV_HAIR,
             style=dict(vol=1.8, fringe=((-0.7, 3.4), (-0.25, 4.2), (0.2, 4.0), (0.62, 3.2)), sweep=0.0, side=2.0, back=6.0,
                        spikes=(6, 6.4)), eye_color=(170, 110, 240), expression="set", mood="calm", blush=False)
    for (z, x, y, i) in orbit:
        if z >= 0:
            sphere(x, y, i)
    # a gravity ring under the feet
    layer = draw.sub()
    Ell(r.cx, r.base_y + 1.4, 11.0, 2.6).draw(layer, fill=None, outline=GRV_GLOW, width=0.8)
    draw.merge(layer)


# ===================================================================
# TESLA (63) -- wild white hair, goggles, lab coat, a coil on his back
# ===================================================================

TES_COAT = (244, 246, 250)
TES_COPPER = (222, 140, 82)
TES_SPARK = (130, 214, 255)


def draw_tesla(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame)
    d = r.d
    cx = r.cx
    # the coil pack on his back: two copper towers standing up past his
    # shoulders, an arc jumping between their tips
    px = cx - (d * 4.6 if d else 0.0)
    cel(draw, RRect(px - 4.4, r.sh_y - 3.4, px + 4.4, r.waist_y + 1.0, 1.2) if not d else
        RRect(px - 2.6, r.sh_y - 3.4, px + 2.6, r.waist_y + 1.0, 1.2), (110, 110, 126), sh=(0.6, 0.6))
    towers = (-16.4, 16.4) if not d else (-d * 11.0,)
    tips = []
    for u in towers:
        tx = cx + u if not d else cx + u
        base_x = px + (4.0 if u > 0 else -4.0) if not d else px
        cel(draw, Limb([(base_x, r.sh_y - 1.0), (tx, r.sh_y - 4.0), (tx, r.head_cy - 9.0)], [1.2, 1.4, 1.1]), TES_COPPER, sh=(0.5, 0.0))
        for k in range(5):
            y = r.sh_y - 5.0 - k * 2.4
            stroke(draw, [(tx - 1.9, y), (tx + 1.9, y - 0.6)], 0.5, shade(TES_COPPER, 1.4))
        cel(draw, Ell(tx, r.head_cy - 10.4, 2.4, 2.4), (210, 214, 226), sh=(0.6, 0.6), hi=(0.4, 0.4))
        tips.append((tx, r.head_cy - 10.4))
    if len(tips) == 2:
        (x0, y0), (x1, y1) = tips
        mid = (x0 + x1) / 2.0
        jag = [-2.4, 2.0, -1.6, 2.4][frame]
        if frame % 2 == 0:
            bolt(draw, [(x0 + 2.2, y0), (mid - 5.0, y0 - 3.0 + jag), (mid, y0 - 1.0 - jag), (mid + 5.0, y0 - 3.4 + jag), (x1 - 2.2, y1)],
                 1.1, TES_SPARK)
    else:
        (x0, y0) = tips[0]
        bolt(draw, [(x0 - d * 1.8, y0 - 1.0), (x0 - d * 4.6, y0 - 3.6), (x0 - d * 3.4, y0 - 4.6), (x0 - d * 6.6, y0 - 7.0)], 1.0, TES_SPARK)
    if d:
        rig_arms(r, draw, TES_COAT, (70, 70, 80), layer="far")
    rig_legs(r, draw, (70, 72, 88), (90, 70, 56))
    rig_robe(r, draw, TES_COAT, flare=2.4, split=True, hem=r.hip_y + 4.0)
    if not r.back:
        cel(draw, Poly([(cx - 2.2 + d * 1.8, r.sh_y - 2.2), (cx + 2.2 + d * 1.8, r.sh_y - 2.2), (cx + 1.4 + d * 1.8, r.waist_y + 1.0),
                        (cx - 1.4 + d * 1.8, r.waist_y + 1.0)]), (90, 120, 170), sh=None)
        cel(draw, RRect(cx - 5.4 + d, r.sh_y + 2.4, cx - 2.4 + d, r.sh_y + 5.0, 0.4), shade(TES_COAT, 0.5), sh=None)
        stroke(draw, [(cx - 4.6 + d, r.sh_y + 1.4), (cx - 4.6 + d, r.sh_y + 3.4)], 0.5, (80, 140, 220))
    rig_arms(r, draw, TES_COAT, (70, 70, 80), layer="near")
    rig_head(r, draw, SKIN, hair=(248, 248, 252), style="wild", eye_color=(80, 150, 220), expression="grin", hat=True)
    hx, hy, rx = r.hx, r.head_cy, r.head_rx
    # goggles up on the forehead
    if not r.back:
        stroke(draw, [(hx - rx - 0.4, hy - 4.6), (hx + rx + 0.4, hy - 4.6)] if not d else [(hx - d * 3.0, hy - 4.6), (hx + d * (rx + 0.4), hy - 4.8)],
               1.0, (70, 60, 60))
        for k in ((-3.0, 3.0) if not d else (d * 4.0,)):
            gx = hx + k + d * 1.4
            cel(draw, Ell(gx, hy - 5.0, 2.6, 2.2), TES_COPPER, sh=None)
            cel(draw, Ell(gx, hy - 5.0, 1.6, 1.3), TES_SPARK, sh=None, line=False)
            Ell(gx - 0.5, hy - 5.5, 0.5, 0.5).draw(draw, fill=(255, 255, 255))


# ===================================================================
# NANOSWARM (64) -- a little hex-tiled bot in a cloud of nanites
# ===================================================================

NANO_BODY = (52, 188, 150)
NANO_DARK = (30, 70, 72)
NANO_GLOW = (160, 255, 214)


def _hexagon(draw, x, y, rad, color, line=True):
    pts = [(x + rad * math.cos(math.radians(a)), y + rad * math.sin(math.radians(a))) for a in range(30, 390, 60)]
    cel(draw, Poly(pts), color, sh=None, line=line, lw=0.5)


def draw_nanoswarm(draw, ox, oy, direction, frame):
    d, back = _dir(direction)
    bob = [0, -1, -2, -1][frame]
    base = oy + 54 + bob
    cx = ox + 32
    swarm = []
    for i in range(10):
        a = math.radians(frame * 36 + i * 36)
        rad = 13.0 + (i % 3) * 2.4
        swarm.append((math.sin(a), cx + math.cos(a) * rad, base - 20.0 + math.sin(a * 2.0) * 7.0, 0.9 + (i % 2) * 0.5))
    for (z, x, y, k) in swarm:
        if z < 0:
            _hexagon(draw, x, y, k, shade(NANO_BODY, 0.6))
    # a floating core body, no legs: nanites stream below it
    for i in range(3):
        _hexagon(draw, cx + (i - 1) * 3.0 - d * 1.0, base - 4.0 + (i % 2) * 1.6 - frame * 0.3, 1.2, NANO_BODY)
    body = Poly([(cx - 8.0, base - 20.0), (cx + 8.0, base - 20.0), (cx + 9.0, base - 14.0), (cx + 5.0, base - 8.0), (cx - 5.0, base - 8.0),
                 (cx - 9.0, base - 14.0)]) if not d else \
        Poly([(cx - 6.4, base - 20.0), (cx + 6.4, base - 20.0), (cx + 7.4, base - 14.0), (cx + 4.0, base - 8.0), (cx - 4.0, base - 8.0),
              (cx - 7.4, base - 14.0)])
    cel(draw, body, NANO_BODY, sh=(1.2, 1.0), hi=(0.6, 0.6))
    for (u, v) in ((-3.0, -15.0), (3.0, -15.0), (0.0, -11.4)):
        _hexagon(draw, cx + u + d * 1.0, base + v, 1.6, lit(NANO_BODY, 0.6), line=False)
    # arms made of nanites
    for side in ((-1, 1) if not d else (d,)):
        for j in range(3):
            x = cx + (side * (10.4 + j * 2.2) if not d else d * (7.0 + j * 2.0))
            y = base - 17.0 + j * 2.0 + [0, 1, 0, -1][frame] * (1 if side > 0 else -1) * 0.5
            _hexagon(draw, x, y, 1.6 - j * 0.2, NANO_BODY)
    # head: a rounded hex with a big visor eye
    hx, hy = cx + d * 2.0, base - 30.0
    head = Poly([(hx + 10.4 * math.cos(math.radians(a)), hy + 9.6 * math.sin(math.radians(a))) for a in range(0, 360, 60)])
    cel(draw, head, NANO_BODY, sh=(1.2, 1.0), hi=(0.8, 0.8))
    if not back:
        vx = hx + d * 3.0
        cel(draw, RRect(vx - 6.4 if not d else vx - 4.4, hy - 2.6, vx + 6.4 if not d else vx + 4.4, hy + 3.4, 2.4), NANO_DARK, sh=None)
        visor_eyes(draw, vx, hy + 0.4, 3.2, NANO_GLOW, d, happy=frame % 2 == 1)
    cel(draw, Limb([(hx - d * 2.0, hy - 9.0), (hx - d * 3.0, hy - 13.0)], [0.6, 0.5]), NANO_DARK, sh=None)
    _hexagon(draw, hx - d * 3.0, hy - 13.6, 1.6, NANO_GLOW)
    for (z, x, y, k) in swarm:
        if z >= 0:
            _hexagon(draw, x, y, k, NANO_BODY)


# ===================================================================
# VOIDWALKER (65) -- a starfield in a dark body, a void lance
# ===================================================================

VOID_BODY = (36, 24, 58)
VOID_EDGE = (110, 60, 170)
VOID_GLOW = (236, 90, 255)


def draw_voidwalker(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, bob=[-1, -2, -2, -1][frame], step=0)
    d = r.d
    hs = _hold_side(r)
    cx = r.cx
    wave = [0.0, 1.0, 0.0, -1.0][frame]
    # a cloak of void, frayed into the dark below
    top = r.sh_y - 2.4
    w = r.sh_w
    if d:
        cloak = Poly([(cx - d * 3.4, top), (cx + d * 3.0, top), (cx + d * 5.4, r.hip_y), (cx + d * 3.0 + wave, r.base_y - 1.0),
                      (cx - d * 2.0, r.base_y - 3.0), (cx - d * 7.0 + wave, r.base_y - 0.6), (cx - d * 6.0, r.hip_y)])
    else:
        cloak = Poly([(cx - w + 1.8, top), (cx + w - 1.8, top), (cx + w, r.sh_y + 0.6), (cx + w + 4.6, r.base_y - 1.0 + wave),
                      (cx + 3.0, r.base_y - 4.0), (cx, r.base_y - 0.6), (cx - 3.0, r.base_y - 4.0), (cx - w - 4.6, r.base_y - 1.0 - wave),
                      (cx - w, r.sh_y + 0.6)])
    cel(draw, cloak, VOID_BODY, sh=(1.6, 1.0), line_color=VOID_EDGE)
    # stars inside it
    for i, (u, v) in enumerate(((-4.0, 2.0), (3.0, 5.0), (-1.0, 9.0), (5.0, 11.0), (-6.0, 12.0), (1.4, 14.4))):
        if d and abs(u) > 4:
            continue
        rad = 0.5 if (i + frame) % 3 else 0.9
        Ell(cx + u, r.sh_y + v, rad, rad).draw(draw, fill=(255, 255, 255) if (i + frame) % 2 else (200, 180, 255))
    if d:
        rig_arms(r, draw, VOID_BODY, VOID_EDGE, layer="far", hands=False)
    rig_arms(r, draw, VOID_BODY, VOID_EDGE, layer="near", hands=False, reach=0.2)
    head_skull(r, draw, VOID_BODY, ears=False)
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    for i, (u, v) in enumerate(((-6.0, -5.0), (5.0, -6.0), (-2.0, -8.4), (7.0, 3.0))):
        Ell(hx + u, hy + v, 0.5, 0.5).draw(draw, fill=(255, 255, 255))
    if not r.back:
        e1, e2, ey, _, _ = face_anchor(r)
        for side, ex in (((-1, e1), (1, e2)) if not d else ((-d, e1), (d, e2))):
            k = 1.0 if not d or side == -d else 0.6
            Poly([(ex - side * 2.4 * k, ey + 0.8), (ex - side * 0.6 * k, ey - 1.4), (ex + side * 2.2 * k, ey - 1.6),
                  (ex + side * 1.6 * k, ey + 1.4), (ex - side * 0.8 * k, ey + 2.0)]).draw(draw, fill=VOID_GLOW)
            Ell(ex, ey, 0.7 * k, 0.6).draw(draw, fill=(255, 240, 255))
    # a crown of void flame
    flame(draw, hx - d * 1.4, hy - ry + 4.0, 18.0, 13.0, (VOID_BODY, VOID_EDGE, VOID_GLOW), sway=wave - d * 2.4)
    # the void lance
    h = hand_at(r, hs, 0.2)
    x = h[0]
    from sprite_base import spear
    spear(draw, x, r.base_y - 1.0, x + (0 if not d else d * 1.0), r.head_cy - 4.0, shaft=VOID_EDGE, head=VOID_GLOW, band=None,
          head_len=7.0, head_w=2.6)
    for side in _sides(r):
        rig_hand(r, draw, side, VOID_EDGE, reach=0.2)


# ===================================================================
# PHOTON (66) -- a being of light: radiant hair, a prism, rainbow glints
# ===================================================================

PHO_BODY = (255, 246, 196)
PHO_GOLD = (255, 214, 96)
PRISM = ((255, 110, 120), (255, 200, 90), (120, 230, 140), (110, 180, 255), (190, 130, 255))


def draw_photon(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, bob=[-1, -2, -2, -1][frame])
    d = r.d
    hx, hy = r.hx, r.head_cy
    # a sunburst of light behind
    rays = []
    for i in range(12):
        a = math.radians(i * 30 + frame * 7.5)
        k = 17.0 if i % 2 == 0 else 13.4
        rays.append((hx + math.cos(a) * k, hy + math.sin(a) * k * 0.95))
        a2 = math.radians(i * 30 + 15 + frame * 7.5)
        rays.append((hx + math.cos(a2) * 9.0, hy + math.sin(a2) * 9.0))
    from sprite_base import stroke as _st
    Poly(rays).draw(draw, fill=(255, 250, 214))
    if d:
        rig_arms(r, draw, PHO_BODY, PHO_BODY, layer="far")
    rig_legs(r, draw, PHO_BODY, PHO_GOLD, bare=True)
    rig_robe(r, draw, PHO_BODY, trim=PHO_GOLD, flare=3.0, hem=r.hip_y + 4.0, dark=(250, 222, 150))
    rig_arms(r, draw, PHO_BODY, PHO_BODY, layer="near", cuff=PHO_GOLD, reach=0.3)
    rig_head(r, draw, (255, 250, 232), hair=PHO_GOLD, style="spiky", eye_color=(250, 170, 40), expression="smile", blush=True)
    # a prism floating overhead, and rainbow glints
    px, py = hx + (0 if not d else -d * 2.0), hy - r.head_ry - 5.6 + [0, -0.8, -1.2, -0.8][frame]
    cel(draw, Poly([(px, py - 3.4), (px + 3.0, py + 2.2), (px - 3.0, py + 2.2)]), (236, 250, 255), sh=None,
        regions=[(Poly([(px, py - 3.4), (px + 3.0, py + 2.2), (px, py + 2.2)]), (200, 230, 255))])
    for i, col in enumerate(PRISM):
        a = math.radians(frame * 72 + i * 72)
        sparkle(draw, r.cx + math.cos(a) * 14.0, r.waist_y - 4.0 + math.sin(a) * 6.0, 1.5 if i % 2 else 1.9, col)


# ===================================================================
# RAILGUNNER (67) -- red visor, tactical gear, a huge coil rifle
# ===================================================================

RG_SUIT = (72, 92, 84)
RG_ARMOR = (116, 132, 124)
RG_VISOR = (255, 70, 70)
RG_COIL = (90, 210, 255)


def _railgun(draw, x, y, ang, flip=1.0, charge=0):
    body = [(-6.0, -2.0), (6.0, -2.0), (6.0, 2.0), (-6.0, 2.4)]
    cel(draw, Poly(xform(body, x, y, ang, 1.0, flip)), (70, 74, 90), sh=None)
    stock = [(-10.4, -1.6), (-6.0, -2.0), (-6.0, 2.4), (-9.6, 3.2)]
    cel(draw, Poly(xform(stock, x, y, ang, 1.0, flip)), (50, 52, 64), sh=None)
    for rail in (-1.3, 1.3):
        cel(draw, Limb(xform([(5.6, rail), (19.0, rail)], x, y, ang, 1.0, flip), [0.7, 0.7]), (150, 156, 176), sh=None, lw=0.5)
    for k in range(4):
        p = xform([(8.0 + k * 3.0, 0.0)], x, y, ang, 1.0, flip)[0]
        cel(draw, Ell(p[0], p[1], 1.2, 2.2), RG_COIL if (k + charge) % 2 == 0 else shade(RG_COIL, 0.8), sh=None, lw=0.5)
    scope = [(-2.0, -2.0), (3.0, -2.0), (3.0, -4.0), (-2.0, -4.0)]
    cel(draw, Poly(xform(scope, x, y, ang, 1.0, flip)), (50, 52, 64), sh=None)
    lens = xform([(3.2, -3.0)], x, y, ang, 1.0, flip)[0]
    Ell(lens[0], lens[1], 0.7, 0.7).draw(draw, fill=RG_VISOR)


def draw_railgunner(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=1.04)
    d = r.d
    if r.back:
        _railgun(draw, r.cx + 1.0, r.sh_y + 3.0, -60.0, charge=frame)
    if d:
        rig_arms(r, draw, RG_SUIT, (60, 60, 70), layer="far", reach=0.5)
    rig_legs(r, draw, RG_SUIT, (60, 60, 68))
    rig_torso(r, draw, RG_ARMOR)
    cx = r.cx
    if not r.back:
        for k in (-1, 1):
            cel(draw, RRect(cx + k * 3.0 + d - 1.4, r.sh_y + 1.0, cx + k * 3.0 + d + 1.4, r.sh_y + 4.0, 0.5), shade(RG_ARMOR, 0.7), sh=None)
    rig_belt(r, draw, (60, 62, 72), buckle=RG_COIL)
    rig_arms(r, draw, RG_SUIT, (60, 60, 70), layer="near", reach=0.5, hands=False)
    head_skull(r, draw, SKIN)
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    if not r.back:
        head_face(r, draw, SKIN, iris=(80, 80, 90), mood="sharp", mouth="set", brows=False)
    rig_hair(r, draw, (80, 70, 60), "crop", hat=True, skin=SKIN)
    # helmet with a red visor band over the eyes
    dome = arc_pts(hx - d * 0.4, hy - 0.8, rx + 1.3, ry + 0.8, 182, 358, 26)
    cel(draw, Poly(dome + [(hx + rx + 1.4, hy + 1.0), (hx - rx - 1.4, hy + 1.0)]), RG_ARMOR, sh=(1.4, 1.0), hi=(0.8, 0.8))
    if not r.back:
        vx = hx + d * 2.6
        w = rx - 0.6 if not d else rx * 0.72
        cel(draw, RRect(vx - w, hy - 1.2, vx + w, hy + 3.4, 1.6), RG_VISOR, sh=None, line_color=shade(RG_ARMOR, 1.6))
        stroke(draw, [(vx - w + 1.4, hy - 0.2), (vx - w + 4.4, hy - 0.2)], 0.6, (255, 200, 200))
    cel(draw, Limb([(hx - d * 5.0 + (6.0 if not d else 0), hy - ry + 1.0), (hx - d * 5.4 + (6.4 if not d else 0), hy - ry - 4.0)], [0.5, 0.4]),
        (60, 60, 70), sh=None)
    if not r.back:
        hr = hand_at(r, 1 if not d else d, 0.5)
        if d:
            _railgun(draw, hr[0], hr[1] - 1.0, 0.0 if d > 0 else 180.0, flip=1.0 if d > 0 else -1.0, charge=frame)
        else:
            _railgun(draw, hr[0] - 2.0, hr[1] - 1.0, -145.0, flip=-1.0, charge=frame)
    for side in _sides(r):
        rig_hand(r, draw, side, (60, 60, 70), reach=0.5)


# ===================================================================
# BOMBARDIER (68) -- goggles, a bandolier of grenades, a lit bomb
# ===================================================================

BOMB_JACKET = (150, 110, 70)
BOMB_OLIVE = (112, 124, 74)
BOMB_BLACK = (52, 50, 62)


def _bomb(draw, x, y, rad, frame):
    cel(draw, Ell(x, y, rad, rad), BOMB_BLACK, sh=(0.9, 0.9), hi=(0.5, 0.5))
    Ell(x - rad * 0.35, y - rad * 0.35, rad * 0.24, rad * 0.2).draw(draw, fill=(150, 150, 170))
    cel(draw, RRect(x - 1.2, y - rad - 1.4, x + 1.2, y - rad + 0.4, 0.4), (150, 150, 160), sh=None)
    fuse = [(x, y - rad - 1.2), (x + 1.4, y - rad - 3.0), (x + 2.6, y - rad - 3.4)]
    stroke(draw, fuse, 0.6, (230, 210, 160))
    sparkle(draw, fuse[-1][0] + 0.6, fuse[-1][1] - 0.4, 2.2 if frame % 2 == 0 else 1.6, (255, 200, 80), core=(255, 250, 220))


def draw_bombardier(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=1.1)
    d = r.d
    hs = _hold_side(r)
    if d:
        rig_arms(r, draw, BOMB_JACKET, (80, 70, 60), layer="far")
    rig_legs(r, draw, BOMB_OLIVE, (80, 60, 46))
    rig_torso(r, draw, BOMB_JACKET)
    cx = r.cx
    # bandolier of grenades
    if not r.back:
        s0 = (cx - r.sh_w + 1.0 + d, r.sh_y - 1.4)
        s1 = (cx + r.hip_w - 0.6 + d, r.waist_y + 0.6)
        stroke(draw, [s0, s1], 1.3, (90, 70, 50))
        for t in (0.2, 0.45, 0.7):
            gx, gy = s0[0] + (s1[0] - s0[0]) * t, s0[1] + (s1[1] - s0[1]) * t
            cel(draw, Ell(gx, gy, 1.4, 1.6), BOMB_OLIVE, sh=None, lw=0.5)
            stroke(draw, [(gx - 0.6, gy - 1.4), (gx + 0.6, gy - 1.4)], 0.5, (180, 180, 190))
    rig_belt(r, draw, (90, 70, 50), buckle=(200, 200, 210))
    rig_arms(r, draw, BOMB_JACKET, (80, 70, 60), layer="near", hands=False)
    head_skull(r, draw, SKIN)
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    if not r.back:
        head_face(r, draw, SKIN, iris=(120, 90, 60), mood="bright", mouth="grin", brows=True, brow_color=(90, 60, 40))
        # soot smudges
        for (u, v) in ((-6.4, 5.4), (5.6, 6.6)) if not d else ((d * 5.0, 6.6),):
            blob(draw, [Ell(hx + u, hy + v, 1.6, 0.9), Ell(hx + u + 0.8, hy + v + 0.5, 0.9, 0.6)], (150, 120, 110), sh=None, line=False)
    rig_hair(r, draw, (200, 120, 60), "wild", hat=True, skin=SKIN)
    # leather cap with ear flaps and goggles
    dome = arc_pts(hx - d * 0.4, hy - 0.8, rx + 1.2, ry + 0.8, 182, 358, 26)
    cel(draw, Poly(dome + [(hx + rx + 1.3, hy - 2.0), (hx - rx - 1.3, hy - 2.0)]), BOMB_JACKET, sh=(1.4, 1.0), hi=(0.6, 0.6))
    for s in ((-1, 1) if not d else (-d,)):
        fx = hx + s * (rx + 0.4) if not d else hx - d * 3.0
        cel(draw, RRect(fx - 2.0, hy - 3.0, fx + 2.0, hy + 4.4, 1.6), BOMB_JACKET, sh=(0.5, 0.5))
    if not r.back:
        stroke(draw, [(hx - rx - 0.6, hy - 4.0), (hx + rx + 0.6, hy - 4.0)] if not d else [(hx - d * 2.0, hy - 4.2), (hx + d * (rx + 0.6), hy - 4.4)],
               1.1, (60, 50, 50))
        for k in ((-3.2, 3.2) if not d else (d * 4.4,)):
            gx = hx + k + d * 1.4
            cel(draw, Ell(gx, hy - 4.4, 2.6, 2.2), (200, 190, 170), sh=None)
            cel(draw, Ell(gx, hy - 4.4, 1.6, 1.3), (230, 160, 70), sh=None, line=False)
    for side in _sides(r):
        h = rig_hand(r, draw, side, (80, 70, 60), reach=0.35 if side == hs else 0.0)
        if side == hs and not r.back:
            _bomb(draw, h[0] + (1.4 if not d else d * 1.8), h[1] - 3.6, 3.6, frame)


# ===================================================================
# SENTINEL (69) -- bulky armour, a T-visor, a hex energy shield
# ===================================================================

SEN_ARMOR = (60, 82, 142)
SEN_STEEL = (196, 206, 226)
SEN_GLOW = (100, 210, 255)


def _hex_shield(draw, x, y, rad, frame):
    pts = [(x + rad * math.cos(math.radians(a)), y + rad * 1.08 * math.sin(math.radians(a))) for a in range(0, 360, 60)]
    layer = draw.sub()
    cel(layer, Poly(pts), (160, 226, 255), sh=None, line_color=SEN_GLOW, lw=0.9)
    for (u, v) in ((0.0, 0.0), (0.0, -0.55), (0.0, 0.55), (-0.48, -0.28), (0.48, -0.28), (-0.48, 0.28), (0.48, 0.28)):
        hx, hy = x + u * rad, y + v * rad
        hp = [(hx + rad * 0.26 * math.cos(math.radians(a)), hy + rad * 0.26 * math.sin(math.radians(a))) for a in range(0, 360, 60)]
        Poly(hp).draw(layer, fill=None, outline=lit(SEN_GLOW, 0.6), width=0.4)
    draw.merge(layer)
    if frame % 2 == 0:
        sparkle(draw, x - rad * 0.4, y - rad * 0.5, 1.6, (255, 255, 255))


def draw_sentinel(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=1.24)
    d = r.d
    shield_side = (-1 if not r.back else 1) if not d else d
    if d:
        rig_arms(r, draw, SEN_ARMOR, SEN_STEEL, layer="far")
    rig_legs(r, draw, SEN_ARMOR, SEN_STEEL, width=1.1)
    rig_torso(r, draw, SEN_ARMOR)
    cx = r.cx
    if not r.back:
        cel(draw, RRect(cx - 4.0 + d * 1.6, r.sh_y - 0.6, cx + 4.0 + d * 1.6, r.sh_y + 5.0, 1.2), SEN_STEEL, sh=(0.6, 0.6))
        cel(draw, Poly([(cx + d * 1.6, r.sh_y + 0.4), (cx + 2.4 + d * 1.6, r.sh_y + 2.2), (cx + d * 1.6, r.sh_y + 4.0),
                        (cx - 2.4 + d * 1.6, r.sh_y + 2.2)]), SEN_GLOW, sh=None, line=False)
    rig_belt(r, draw, shade(SEN_ARMOR, 1.4), buckle=SEN_GLOW)
    rig_arms(r, draw, SEN_ARMOR, SEN_STEEL, layer="near", hands=False)
    for side in _sides(r):
        sx, sy = r.shoulder(side)
        cel(draw, RRect(sx + (side * 1.4 if not d else 0) - 4.4, sy - 3.4, sx + (side * 1.4 if not d else 0) + 4.4, sy + 1.2, 1.8), SEN_STEEL,
            sh=(0.8, 0.8), hi=(0.5, 0.5))
        stroke(draw, [(sx + (side * 1.4 if not d else 0) - 3.0, sy - 1.2), (sx + (side * 1.4 if not d else 0) + 3.0, sy - 1.2)], 0.5, SEN_GLOW)
    head_skull(r, draw, SEN_STEEL, ears=False)
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    cel(draw, Ell(hx - d * 0.4, hy, rx + 0.8, ry + 0.8), SEN_STEEL, sh=(1.6, 1.2), hi=(0.9, 0.9))
    cel(draw, Poly([(hx - 3.0 - d * 1.0, hy - ry - 0.4), (hx + 3.0 - d * 1.0, hy - ry - 0.4), (hx + 2.4 - d * 1.0, hy - ry + 5.0),
                    (hx - 2.4 - d * 1.0, hy - ry + 5.0)]), SEN_ARMOR, sh=None)
    if not r.back:
        vx = hx + d * 3.0
        w = rx - 2.0 if not d else rx * 0.62
        cel(draw, RRect(vx - w, hy - 0.2, vx + w, hy + 2.6, 1.0), (24, 30, 50), sh=None)
        cel(draw, RRect(vx - 1.2, hy + 2.0, vx + 1.2, hy + 7.4, 0.6), (24, 30, 50), sh=None)
        visor_eyes(draw, vx, hy + 1.2, 3.8, SEN_GLOW, d)
    for side in _sides(r):
        rig_hand(r, draw, side, SEN_STEEL)
    h = hand_at(r, shield_side)
    if r.back:
        _hex_shield(draw, h[0] + shield_side * 1.0, h[1] - 3.6, 6.8, frame)
    elif d:
        _hex_shield(draw, h[0] + d * 3.0, h[1] - 4.0, 7.4, frame)
    else:
        _hex_shield(draw, h[0] + shield_side * 2.4, h[1] - 3.6, 7.4, frame)


# ===================================================================
# PILOT (70) -- leather flying cap and goggles, a flowing white scarf
# ===================================================================

PIL_JACKET = (156, 104, 62)
PIL_FUR = (240, 226, 196)
PIL_SCARF = (250, 250, 252)


def draw_pilot(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame)
    d = r.d
    wave = [0.0, 1.0, 0.0, -1.0][frame]
    # the scarf streaming out behind
    s = -1 if not d else -d
    y0 = r.sh_y - 1.8
    bx = r.cx + (s * 3.0 if not d else -d * 2.4)
    if not r.back:
        cel(draw, Limb([(bx, y0), (bx + s * 6.0, y0 - 1.0 + wave), (bx + s * 11.4, y0 + 0.6 - wave), (bx + s * 15.0, y0 - 1.0)],
                       [1.6, 1.5, 1.3, 0.5]), PIL_SCARF, sh=(0.0, 0.8))
    if d:
        rig_arms(r, draw, PIL_JACKET, (110, 80, 60), layer="far")
    rig_legs(r, draw, (130, 120, 96), (80, 60, 46))
    rig_torso(r, draw, PIL_JACKET)
    cx = r.cx
    if not r.back:
        stroke(draw, [(cx + d * 1.6, r.sh_y - 1.0), (cx + d * 1.6, r.hip_y)], 0.6, shade(PIL_JACKET, 1.4))
        # gold wings badge
        wx = cx - 3.4 + d * 1.0
        for k in (-1, 1):
            Poly([(wx, r.sh_y + 2.6), (wx + k * 2.6, r.sh_y + 1.8), (wx + k * 2.0, r.sh_y + 3.0)]).draw(draw, fill=GOLD)
        Ell(wx, r.sh_y + 2.6, 0.7, 0.7).draw(draw, fill=GOLD)
    rig_belt(r, draw, (90, 64, 44), buckle=GOLD)
    rig_arms(r, draw, PIL_JACKET, (110, 80, 60), layer="near")
    # fur collar
    blob(draw, [Ell(cx - 4.2, r.sh_y - 1.8, 3.6, 2.2), Ell(cx + 4.2, r.sh_y - 1.8, 3.6, 2.2)] if not d else [Ell(cx, r.sh_y - 1.8, 4.6, 2.2)],
         PIL_FUR, sh=(0.4, 0.6), tone=shade(PIL_FUR, 0.8))
    cel(draw, RRect(cx - 5.6, y0 - 1.0, cx + 5.6, y0 + 1.6, 1.2) if not d else RRect(cx - 4.0, y0 - 1.0, cx + 4.4, y0 + 1.6, 1.2), PIL_SCARF,
        sh=(0.0, 0.6))
    head_skull(r, draw, SKIN)
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    if not r.back:
        head_face(r, draw, SKIN, iris=(90, 130, 180), mood="bright", mouth="smile", brows=True, brow_color=(110, 70, 40))
    rig_hair(r, draw, (170, 104, 56), "short", hat=True, skin=SKIN)
    # leather flying cap with ear flaps, goggles on the brow
    dome = arc_pts(hx - d * 0.4, hy - 0.8, rx + 1.2, ry + 0.8, 182, 358, 26)
    cel(draw, Poly(dome + [(hx + rx + 1.3, hy - 1.6), (hx - rx - 1.3, hy - 1.6)]), PIL_JACKET, sh=(1.4, 1.0), hi=(0.6, 0.6))
    stroke(draw, [(hx - d * 0.4, hy - ry - 0.4), (hx - d * 0.4 + (0 if not d else -d * 3.0), hy - 2.0)], 0.5, shade(PIL_JACKET, 1.4))
    for s2 in ((-1, 1) if not d else (-d,)):
        fx = hx + s2 * (rx + 0.2) if not d else hx - d * 3.0
        cel(draw, RRect(fx - 2.0, hy - 3.0, fx + 2.0, hy + 5.4, 1.6), PIL_JACKET, sh=(0.5, 0.5))
        cel(draw, Ell(fx, hy + 5.6, 1.4, 0.9), PIL_FUR, sh=None, lw=0.5)
    if not r.back:
        stroke(draw, [(hx - rx - 0.6, hy - 4.2), (hx + rx + 0.6, hy - 4.2)] if not d else [(hx - d * 2.0, hy - 4.4), (hx + d * (rx + 0.6), hy - 4.6)],
               1.1, (70, 60, 56))
        for k in ((-3.2, 3.2) if not d else (d * 4.4,)):
            gx = hx + k + d * 1.4
            cel(draw, Ell(gx, hy - 4.6, 2.7, 2.3), (214, 200, 150), sh=None)
            cel(draw, Ell(gx, hy - 4.6, 1.7, 1.4), (140, 210, 240), sh=None, line=False)
            Ell(gx - 0.6, hy - 5.1, 0.5, 0.5).draw(draw, fill=(255, 255, 255))
    if r.back:
        cel(draw, Limb([(bx, y0), (bx + 3.6, y0 + 6.0 + wave), (bx + 2.6, y0 + 12.0)], [1.6, 1.4, 0.5]), PIL_SCARF, sh=(0.0, 0.8))


# ===================================================================
# GLITCHER (71) -- chromatic ghosting, a missing chunk, an X for an eye
# ===================================================================

GLI_HOOD = (138, 92, 222)
GLI_CYAN = (70, 240, 255)
GLI_MAG = (255, 60, 200)


def draw_glitcher(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=0.96)
    d = r.d
    jit = [(1.6, 0.0), (-1.2, 0.4), (2.0, -0.4), (-1.8, 0.0)][frame]

    def body(dr, rr, tint=None):
        c = (lambda col: tint) if tint else (lambda col: col)
        rig_legs(rr, dr, c((70, 60, 110)), c((240, 240, 250)))
        rig_torso(rr, dr, c(GLI_HOOD))
        rig_arms(rr, dr, c(GLI_HOOD), c(SKIN), layer="near")
        head_skull(rr, dr, c(SKIN))
        if tint:
            from sprite_base import hood_shape
            cel(dr, hood_shape(rr, drape=True), c(GLI_HOOD), sh=None)

    # the colour-separated ghosts, offset to either side
    for tint, (dx, dy) in ((GLI_CYAN, (-jit[0] - 1.0, jit[1])), (GLI_MAG, (jit[0] + 1.0, -jit[1]))):
        layer = draw.sub()
        rr = rig(ox + dx, oy + dy, direction, frame, build=0.96)
        body(layer, rr, tint)
        a = layer._img.getchannel("A").point(lambda v: v * 150 // 255)
        layer._img.putalpha(a)
        draw.merge(layer)
    if d:
        rig_arms(r, draw, GLI_HOOD, SKIN, layer="far")
    rig_legs(r, draw, (70, 60, 110), (240, 240, 250))
    rig_torso(r, draw, GLI_HOOD)
    cx = r.cx
    if not r.back:
        for k in range(3):
            y = r.sh_y + 1.0 + k * 2.4
            stroke(draw, [(cx - 3.0 + d, y), (cx + [3.0, 1.0, 2.2][k] + d, y)], 0.6, GLI_CYAN if k % 2 else GLI_MAG)
    rig_arms(r, draw, GLI_HOOD, SKIN, layer="near")
    rig_head(r, draw, SKIN, hair=(60, 50, 90), style="short", eye_color=(120, 60, 200), expression="smirk", blush=False)
    if not r.back:
        e1, e2, ey, _, _ = face_anchor(r)
        ex = e2 if not d else e1
        cel(draw, Ell(ex, ey, 2.8, 3.0), SKIN, sh=None, line=False)
        stroke(draw, [(ex - 1.8, ey - 1.8), (ex + 1.8, ey + 1.8)], 0.9, GLI_MAG)
        stroke(draw, [(ex + 1.8, ey - 1.8), (ex - 1.8, ey + 1.8)], 0.9, GLI_MAG)
    rig_hood(r, draw, GLI_HOOD, opening=1.1)
    # glitch blocks: slices of the sprite displaced sideways
    for i, (u, v, w, h) in enumerate(((-9.0, -20.0, 7.0, 2.0), (4.0, -10.0, 9.0, 1.6), (-4.0, -4.0, 5.0, 1.4))):
        k = (frame + i) % 4
        if k == 3:
            continue
        x = cx + u + (k - 1) * 2.4
        y = r.base_y + v
        cel(draw, RRect(x, y, x + w, y + h, 0.2), (GLI_CYAN, GLI_MAG, GLI_HOOD)[(i + frame) % 3], sh=None, line=False)
    if frame % 2 == 1:
        cel(draw, RRect(cx + 9.0, r.head_cy - 4.0, cx + 13.0, r.head_cy - 1.4, 0.2), GLI_CYAN, sh=None, line=False)
        cel(draw, RRect(cx - 14.0, r.sh_y + 2.0, cx - 10.0, r.sh_y + 3.4, 0.2), GLI_MAG, sh=None, line=False)


SCIFI_DRAW_FUNCTIONS = {
    'cyborg': draw_cyborg,
    'hacker': draw_hacker,
    'mechpilot': draw_mechpilot,
    'android': draw_android,
    'chronomancer': draw_chronomancer,
    'graviton': draw_graviton,
    'tesla': draw_tesla,
    'nanoswarm': draw_nanoswarm,
    'voidwalker': draw_voidwalker,
    'photon': draw_photon,
    'railgunner': draw_railgunner,
    'bombardier': draw_bombardier,
    'sentinel': draw_sentinel,
    'pilot': draw_pilot,
    'glitcher': draw_glitcher,
}


def main():
    for name, draw_func in SCIFI_DRAW_FUNCTIONS.items():
        generate_character(name, draw_func=draw_func)
    print(f"\nGenerated {len(SCIFI_DRAW_FUNCTIONS)} sci-fi character sprites.")


if __name__ == "__main__":
    main()
