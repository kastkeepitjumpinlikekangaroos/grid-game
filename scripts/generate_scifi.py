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


def _cyb_eye(draw, ex, ey, k=1.0):
    """The cyber-eye: a red lens in a dark socket. `k` narrows it on the far side of the face."""
    Ell(ex, ey, 2.6 * k, 2.6).draw(draw, fill=(40, 30, 40))
    Ell(ex, ey, 1.6 * k, 1.6).draw(draw, fill=CYB_RED)
    Ell(ex - 0.5 * k, ey - 0.5, 0.5, 0.5).draw(draw, fill=(255, 230, 230))


def _cyb_cannon(r, draw, side, frame, near=True):
    """The cannon that is his left arm: a shoulder plate, the barrel where the forearm would be,
    a lit slot along it and a lit muzzle. Mirrored rather than turned, so its shaded half stays
    underneath (or on the right when it hangs) whichever way he faces."""
    d = r.d
    metal = CYB_METAL if near else shade(CYB_METAL, 0.7)
    sx, sy = r.shoulder(side)
    cel(draw, Ell(sx + (side * 1.2 if not d else 0), sy - 0.4, 4.4, 3.6), metal, sh=(0.8, 0.8), hi=(0.5, 0.5) if near else None)
    h = hand_at(r, side, 0.5 if d else 0.2)
    bx, by = h[0], h[1] - 2.0
    ang = (90 - side * 12) if not d else (10 if d > 0 else 170)
    flip = 1.0 if d > 0 else -1.0

    def at(pts):
        return xform(pts, bx, by, ang, 1.0, flip)

    body = [(-6.0, -2.8), (4.0, -2.8), (5.0, -3.6), (8.4, -3.6), (8.4, 3.6), (5.0, 3.6), (4.0, 2.8), (-6.0, 2.8)]
    cel(draw, Poly(at(body)), metal, sh=None,
        regions=[(Poly(at([(-6.0, 0.4), (8.4, 0.4), (8.4, 3.6), (-6.0, 3.6)])), shade(metal, 0.9)),
                 (Poly(at([(1.6, -4.0), (3.0, -4.0), (3.0, 4.0), (1.6, 4.0)])), shade(metal, 2.2)),
                 (Poly(at([(-4.6, -1.9), (0.2, -1.9), (0.2, -0.7), (-4.6, -0.7)])), CYB_ORANGE)])
    mx, my = at([(8.4, 0.0)])[0]
    cel(draw, Ell(mx, my, 2.4, 2.4), shade(metal, 1.4), sh=None)
    Ell(mx, my, 1.4, 1.4).draw(draw, fill=CYB_ORANGE if frame % 2 == 0 else CYB_RED)


def draw_cyborg(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=1.12)
    d = r.d
    cx = r.cx
    # His left side is the machine: the cannon arm and the metal half of his head. Head-on that
    # is the screen's right and from behind its left; walking left it is the side the camera
    # sees, walking right the far one (his flesh arm and his own eye are nearest then).
    cs = (1 if not r.back else -1) if not d else -1
    if d > 0:
        _cyb_cannon(r, draw, cs, frame, near=False)
    elif d < 0:
        rig_arms(r, draw, CYB_SUIT, SKIN, layer="far")
    rig_legs(r, draw, CYB_SUIT, CYB_METAL)
    rig_torso(r, draw, CYB_SUIT)
    # the power core: a plate on the chest, and its back plate between the shoulders
    px = cx + d * 1.8
    cel(draw, RRect(px - 3.6, r.sh_y - 0.4, px + 3.6, r.sh_y + 4.6, 1.2), CYB_METAL, sh=(0.6, 0.6))
    cel(draw, Ell(px, r.sh_y + 2.1, 1.4, 1.4), CYB_ORANGE if frame % 2 else lit(CYB_ORANGE, 1.2), sh=None)
    rig_belt(r, draw, (40, 40, 50), buckle=CYB_ORANGE)
    # the flesh arm
    if not d:
        rig_arms(r, draw, CYB_SUIT, SKIN, sides=(-cs,))
    elif d > 0:
        rig_arms(r, draw, CYB_SUIT, SKIN, layer="near")
    head_skull(r, draw, SKIN)
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    if not r.back:
        head_face(r, draw, SKIN, iris=(90, 110, 140), mood="sharp", mouth="set" if not d else None, brows=True,
                  brow_color=(70, 60, 70), blush=d >= 0)
        if not d:
            # head-on: the metal half of the face, on his left
            plate = Poly([(hx, hy - ry - 0.4), (hx + cs * (rx + 0.6), hy - 4.0), (hx + cs * (rx + 0.4), hy + 5.0),
                          (hx + cs * 7.0, hy + 10.0), (hx + cs * 0.8, hy + 9.0), (hx - cs * 0.6, hy + 3.0)])
            cel(draw, plate, CYB_METAL, sh=(0.8, 0.8), hi=(0.5, 0.5))
            _cyb_eye(draw, hx + cs * 4.7, hy + 2.3)
        elif d < 0:
            # walking left: the metal cheek is the near one; his own eye and mouth are on the
            # strip of face beyond it
            plate = Poly([(hx + d * 0.2, hy - ry - 0.2), (hx + d * 5.8, hy - ry * 0.72), (hx + d * 5.2, hy + 2.0),
                          (hx + d * 6.0, hy + 8.9), (hx + d * 0.6, hy + 7.6), (hx - d * 1.8, hy + 1.0)])
            cel(draw, plate, CYB_METAL, sh=(0.8, 0.8), hi=(0.5, 0.5))
            _cyb_eye(draw, hx + d * 1.6, hy + 2.3)
            ms_mouth(draw, hx + d * 8.8, hy + 7.3, "set", SKIN, 0.7)
            # a port where that ear was
            cel(draw, Ell(hx - d * 3.0, hy + 2.6, 2.1, 2.6), shade(CYB_METAL, 0.8), sh=None)
            Ell(hx - d * 3.0, hy + 2.6, 0.8, 0.8).draw(draw, fill=CYB_ORANGE)
        else:
            # walking right: his own face is nearest; the metal is the far strip, with its eye
            plate = Poly([(hx + d * 5.4, hy - ry * 0.75), (hx + d * (rx + 0.5), hy - 3.0), (hx + d * (rx + 0.3), hy + 4.6),
                          (hx + d * 8.2, hy + 9.7), (hx + d * 5.8, hy + 8.9), (hx + d * 5.0, hy + 2.0)])
            cel(draw, plate, CYB_METAL, sh=(0.8, 0.8), hi=(0.5, 0.5))
            _cyb_eye(draw, hx + d * 8.2, hy + 2.3, 0.72)
            ms_mouth(draw, hx + d * 3.4, hy + 7.3, "set", SKIN, 0.7)
    rig_hair(r, draw, (66, 60, 80), "spiky", skin=SKIN)
    if not d and not r.back:
        # the ear on the metal side is a port
        cel(draw, Ell(hx + cs * (rx + 0.2), hy + 2.2, 1.8, 2.5), shade(CYB_METAL, 0.8), sh=None)
        Ell(hx + cs * (rx + 0.2), hy + 2.2, 0.7, 0.7).draw(draw, fill=CYB_ORANGE)
    if r.back:
        # from behind: a plate wrapped round the left rear of his skull, following the head's
        # outline, the hair standing over it
        outer = [(hx - cs * (rx + 2.0) * math.cos(math.radians(a)), hy - 0.6 + (ry + 2.0) * math.sin(math.radians(a)))
                 for a in range(52, -26, -6)]
        plate = Poly(outer + [(hx + cs * 2.6, hy - 6.6), (hx + cs * 0.8, hy - 1.0), (hx + cs * 2.6, hy + 4.0),
                              (hx + cs * 1.6, hy + 8.6)])
        cel(draw, plate, CYB_METAL, sh=(0.8, 0.8), hi=(0.5, 0.5))
        stroke(draw, [(hx + cs * 2.4, hy + 4.4), (hx + cs * 11.6, hy + 5.4)], 0.6, shade(CYB_METAL, 1.8))
        cel(draw, RRect(hx + cs * 8.4 - 1.8, hy + 0.2, hx + cs * 8.4 + 1.8, hy + 1.8, 0.6), CYB_ORANGE, sh=None, lw=0.6)
    if d <= 0:
        _cyb_cannon(r, draw, cs, frame, near=True)


# ===================================================================
# HACKER (58) -- hoodie, headphones, a holo-screen of green code
# ===================================================================

HAK_HOOD = (54, 58, 72)
HAK_GREEN = (86, 255, 150)
HAK_PHONES = (220, 60, 90)
HAK_LEGS = (70, 76, 100)
HAK_HAIR = (86, 70, 110)
HAK_SCREEN = (26, 70, 50)
HAK_REACH = 0.6


def _hak_screen(draw, sx, sy, w, h, frame):
    """The holo-screen: a pane of dark green glass with code scrolling up it."""
    cel(draw, RRect(sx - w, sy - h, sx + w, sy + h, 0.9), HAK_SCREEN, sh=None, line_color=HAK_GREEN, lw=0.7)
    rows = int((2.0 * h - 2.6) / 2.4) + 1
    for i in range(rows):
        y = sy - h + 1.7 + i * 2.4
        ln = [0.62, 0.42, 0.76, 0.5][(frame + i) % 4] * (2.0 * w - 2.8)
        stroke(draw, [(sx - w + 1.5, y), (sx - w + 1.5 + ln, y)], 0.7, HAK_GREEN)


def _hak_sleeve_stripe(r, draw, side, out):
    """A green stripe down a sleeve: what lets a dark arm be seen against a dark hoodie."""
    s, e, w, _ = arm_pts(r, side, HAK_REACH, 0.0, out)
    k = side * 0.9 if not r.d else 0.0
    stroke(draw, [(s[0] + k, s[1] + 1.6), (e[0] + k, e[1]), (w[0] + k * 0.6, w[1] - 0.6)], 0.9, HAK_GREEN)


def draw_hacker(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=0.96)
    # the arms hold the screen and do not swing with the stride
    ra = rig(ox, oy, direction, frame, build=0.96, step=0)
    d = r.d
    out = 2.4 if d else 0.0
    cx = r.cx
    if d:
        rig_arms(ra, draw, HAK_HOOD, SKIN, layer="far", reach=HAK_REACH, out=out, hands=False)
    elif r.back:
        # from behind the arms go forward round the screen: the body hides the hands
        rig_arms(ra, draw, HAK_HOOD, SKIN, reach=HAK_REACH, hands=False)
    rig_legs(r, draw, HAK_LEGS, (240, 240, 244))
    rig_torso(r, draw, HAK_HOOD, bottom=r.hip_y + 1.6)
    if r.back:
        # the print on the hoodie's back: a prompt
        y = r.sh_y + 3.8
        stroke(draw, [(cx - 3.6, y), (cx - 1.0, y + 1.8), (cx - 3.6, y + 3.6)], 1.1, HAK_GREEN)
        stroke(draw, [(cx + 0.6, y + 3.6), (cx + 3.8, y + 3.6)], 1.1, HAK_GREEN)
    else:
        cel(draw, RRect(cx - 3.6 + d * 1.6, r.waist_y - 1.0, cx + 3.6 + d * 1.6, r.waist_y + 2.6, 1.0), shade(HAK_HOOD, 0.7), sh=None)
    if not r.back:
        rig_arms(ra, draw, HAK_HOOD, SKIN, layer="near", reach=HAK_REACH, out=out, hands=False)
        if d:
            _hak_sleeve_stripe(ra, draw, d, out)
    rig_head(r, draw, SKIN, hair=HAK_HAIR, style="swept", eye_color=(60, 190, 120), expression="smirk", hat=True)
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    # round glasses reflecting code
    if not r.back:
        e1, e2, ey, _, _ = face_anchor(r)
        for ex in ((e1, e2) if not d else (e1,)):
            layer = draw.sub()
            Ell(ex, ey, 3.0, 2.8).draw(layer, fill=None, outline=(40, 40, 50), width=0.8)
            draw.merge(layer)
            stroke(draw, [(ex - 1.8, ey - 1.0), (ex - 0.4, ey - 2.0)], 0.5, (230, 255, 240))
        if not d:
            stroke(draw, [(e1 + 3.0, ey - 0.4), (e2 - 3.0, ey - 0.4)], 0.7, (40, 40, 50))
    # hood up, its cloth carried down onto the shoulders (a hood without that closes its outline
    # with a line straight across the face), a little closer round the face in profile
    rig_hood(r, draw, HAK_HOOD, opening=1.12 if not d else 1.0, drape=True)
    # headphones over the hood: head-on and from behind a band from cup to cup, the cups seen
    # edge on; in profile the near cup face on, well back from the eye, its band going up over
    # the crown
    band_col = shade(HAK_PHONES, 0.6)
    if not d:
        cel(draw, Limb(arc_pts(hx, hy + 0.8, rx + 1.9, ry + 2.5, 200, 340, 18), 0.9, cap0=True), band_col, sh=None)
        for s in (-1, 1):
            px = hx + s * (rx + 1.0)
            cel(draw, Ell(px, hy + 1.8, 2.9, 3.8), HAK_PHONES, sh=(0.6, 0.6), hi=(0.4, 0.4))
            Ell(px + s * 0.3, hy + 1.8, 0.9, 1.7).draw(draw, fill=HAK_GREEN)
    else:
        px, py = hx - d * 7.4, hy + 1.8
        cel(draw, Limb([(px, py - 2.0), (px + d * 0.8, hy - 7.0), (px + d * 3.4, hy - ry - 1.6)], 0.9), band_col, sh=None)
        cel(draw, Ell(px, py, 3.4, 3.9), HAK_PHONES, sh=(1.0, 1.0), hi=(0.6, 0.6))
        Ell(px - d * 1.0, py + 1.7, 0.8, 0.8).draw(draw, fill=HAK_GREEN)
    # the holo-screen he works at, held in both hands
    if not r.back:
        if d:
            _hak_screen(draw, cx + d * 8.8, r.waist_y - 0.6, 3.2, 5.0, frame)
        else:
            _hak_screen(draw, cx, r.waist_y + 0.8, 7.0, 4.0, frame)
        for s in _sides(r):
            rig_hand(ra, draw, s, SKIN, reach=HAK_REACH, out=out)


# ===================================================================
# MECH PILOT (59) -- a pilot's head in the cockpit of a chunky mech
# ===================================================================

MECH_Y = (242, 196, 62)
MECH_G = (104, 108, 122)
MECH_DARK = (60, 62, 74)
MECH_GLOW = (110, 220, 255)
MECH_HELM = (238, 240, 246)
MECH_JET = ((120, 200, 255), (190, 240, 255), (255, 255, 255))


def _mech_jet(draw, x, y, w, h):
    """A jump jet's flame hanging from its nozzle at (x, y): the kit's flame stood on its head
    (`flame` only ever rises)."""
    from sprite_base import flame_pts
    outer, mid, core = MECH_JET

    def pts(ww, hh):
        return [(px, 2.0 * y - py) for (px, py) in flame_pts(x, y, ww, hh)]

    cel(draw, Poly(pts(w, h)), outer, sh=None)
    Poly(pts(w * 0.6, h * 0.62)).draw(draw, fill=mid)
    Poly(pts(w * 0.3, h * 0.32)).draw(draw, fill=core)


def _mech_leg(draw, hx0, hy0, fx, fy, d, near, knee=0.0):
    """A hydraulic leg: thigh, knee, shin, and a big foot with its sole on fy. Head-on the knee
    is a dark joint; in profile the legs are too short under the hull for one to read."""
    col = MECH_G if near else shade(MECH_G, 0.6)
    kx, ky = (hx0 + fx) / 2.0 + knee, (hy0 + fy - 3.0) / 2.0
    cel(draw, Limb([(hx0, hy0), (kx, ky), (fx, fy - 3.0)], [2.5, 2.3, 2.1]), col, sh=(0.6, 0.0))
    if not d:
        cel(draw, Ell(kx, ky, 1.6, 1.6), MECH_DARK, sh=None, lw=0.6)
    foot = RRect(fx - 4.0 + d * 0.8, fy - 3.8, fx + 4.0 + d * 1.6, fy + 0.2, 1.2)
    cel(draw, foot, MECH_Y if near else shade(MECH_Y, 0.6), sh=(0.0, 0.8))


def _mech_arm_side(draw, cx, base, d, near, is_gun):
    """One arm in profile, held forward of the hull: the minigun level, or the claw open."""
    # the far arm sits a little back; the far gun is carried high, so its barrels show past the
    # hull's nose over the near claw
    k, dy = (0.0, 0.0) if near else ((-0.4, -4.0) if is_gun else (-1.2, -1.2))
    col = MECH_G if near else shade(MECH_G, 0.6)
    dark = MECH_DARK if near else shade(MECH_DARK, 0.5)

    def at(u, v):
        return (cx + d * (u + k), base + v + dy)

    sx, sy = at(0.5, -22.0)
    if is_gun:
        cel(draw, Limb([(sx, sy), at(3.6, -18.0)], [2.2, 2.0]), col, sh=(0.6, 0.0))
        for v in (-18.9, -17.5, -16.1):
            cel(draw, Limb([at(8.0, v), at(15.4, v)], [0.7, 0.7]), dark, sh=None, lw=0.5)
        x0, x1 = sorted((at(2.0, 0)[0], at(8.8, 0)[0]))
        cel(draw, RRect(x0, base - 20.2 + dy, x1, base - 14.8 + dy, 1.0), col, sh=(0.0, 0.8))
        x0, x1 = sorted((at(12.2, 0)[0], at(13.6, 0)[0]))
        cel(draw, RRect(x0, base - 19.8 + dy, x1, base - 15.2 + dy, 0.4), col, sh=None, lw=0.6)
    else:
        wx, wy = at(8.6, -16.6)
        cel(draw, Limb([(sx, sy), at(4.0, -17.4), (wx, wy)], [2.2, 2.0, 1.8]), col, sh=(0.6, 0.0))
        for s in (-1, 1):
            cel(draw, Limb([(wx, wy + s * 0.8), at(11.6, -16.6 + s * 3.2), at(14.4, -16.6 + s * 1.0)], [1.1, 1.0, 0.5]), col,
                sh=None)
        cel(draw, Ell(wx, wy, 1.8, 1.8), dark, sh=None, lw=0.6)
    if near:
        cel(draw, Ell(sx, sy, 3.6, 3.4), MECH_Y, sh=(0.8, 0.8), hi=(0.5, 0.5))
        cel(draw, Ell(sx, sy, 1.3, 1.3), MECH_G, sh=None, lw=0.6)


def draw_mechpilot(draw, ox, oy, direction, frame):
    d, back = _dir(direction)
    bob = [0, -1, 0, -1][frame]
    ph = [0, 1, 0, -1][frame]
    flick = [0.0, 1.0, 0.0, 1.0][frame]
    base = oy + 54 + bob
    cx = ox + 32
    # the pilot, for the head's layout
    r = rig(ox, oy, direction, frame, head=0.82)
    hx, hy = cx + d * 1.5, base - 32.0
    r.head_cy = hy - 1.0
    r.hx = hx
    hxx, hyy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry

    def rim():
        # the cockpit's sill, in front of the chin
        cel(draw, Poly(arc_pts(hx, hy + 2.0, 10.6, 8.4, 20, 160, 14) + arc_pts(hx, hy + 2.0, 8.4, 5.6, 160, 20, 14)), MECH_Y,
            sh=(0.0, 0.8))

    if d:
        # ---- profile: a deep hull on two legs, its jump jet behind, both arms held forward ----
        # the minigun is the mech's left arm: the near one when it faces left
        _mech_arm_side(draw, cx, base, d, False, d > 0)
        x0, x1 = sorted((cx - d * 14.2, cx - d * 9.6))
        cel(draw, RRect(x0, base - 25.5, x1, base - 15.0, 1.2), MECH_G, sh=(0.6, 0.6))
        cel(draw, Poly([(cx - d * 13.6, base - 15.6), (cx - d * 10.4, base - 15.6), (cx - d * 9.8, base - 12.6),
                        (cx - d * 14.2, base - 12.6)]), MECH_DARK, sh=None)
        _mech_jet(draw, cx - d * 12.0, base - 12.4, 3.2, 3.8 + flick * 1.2)
        for near in (False, True):
            fwd = ph if near else -ph
            fu = (1.6 if near else -1.6) if not ph else fwd * 4.6
            _mech_leg(draw, cx + d * (0.8 if near else -0.8), base - 13.0, cx + d * fu, base - (1.6 if fwd < 0 else 0.0), d, near,
                      knee=d * 1.4)
        # the seat's headrest behind him
        x0, x1 = sorted((cx - d * 13.0, cx - d * 6.5))
        cel(draw, RRect(x0, base - 35.0, x1, base - 25.0, 1.8), MECH_Y, sh=(0.6, 0.6))
        head_skull(r, draw, SKIN, ears=False)
        head_face(r, draw, SKIN, iris=(80, 120, 170), mood="bright", mouth="grin", brows=False)
        # the helmet: over the crown and down the back of the head to the nape
        cu, cv, hu, hv = -0.4, -0.6, rx + 1.0, ry + 0.8
        local = [(cu + hu * math.cos(math.radians(a)), cv - hv * math.sin(math.radians(a))) for a in range(5, 216, 7)]
        local += [(-5.4, 6.6), (-2.2, 5.6), (-1.0, 1.2), (2.4, -1.4)]
        cel(draw, Poly([(hxx + d * u, hyy + v) for (u, v) in local]), MECH_HELM, sh=(1.2, 0.8))
        stroke(draw, [(hxx + d * 3.4, hyy - 4.4), (hxx - d * 4.0, hyy - 4.0), (hxx - d * (rx + 0.4), hyy - 2.6)], 1.1, MECH_DARK)
        gx = hxx + d * 5.0
        cel(draw, Ell(gx, hyy - 4.0, 2.4, 2.1), MECH_DARK, sh=None)
        Ell(gx, hyy - 4.0, 1.5, 1.3).draw(draw, fill=MECH_GLOW)
        # the hull's near wall, over his chin
        x0, x1 = sorted((cx - d * 11.0, cx + d * 10.5))
        cel(draw, RRect(x0, base - 27.0, x1, base - 11.0, 3.6), MECH_Y, sh=(1.8, 1.4), hi=(1.0, 1.0))
        for k in range(3):
            x = cx + d * (3.0 + k * 3.0)
            cel(draw, Poly([(x, base - 14.4), (x + 1.8, base - 14.4), (x + 0.6, base - 11.8), (x - 1.2, base - 11.8)]), MECH_DARK,
                sh=None, line=False)
        for k in range(3):
            y = base - 24.0 + k * 2.0
            stroke(draw, [(cx - d * 8.4, y), (cx - d * 4.6, y)], 0.9, shade(MECH_Y, 2.4))
        cel(draw, Ell(cx + d * 8.2, base - 23.4, 1.6, 1.6), MECH_GLOW, sh=None, line_color=MECH_DARK, lw=0.6)
        _mech_arm_side(draw, cx, base, d, True, d < 0)
        return

    # ---- head-on and from behind ----
    for side in (-1, 1):
        fwd = ph * side
        lift = 2.2 if fwd < 0 else (-0.3 if fwd > 0 else 0.0)
        _mech_leg(draw, cx + side * 5.6, base - 13.0, cx + side * 6.2, base - lift, 0, True)
    w = 11.4
    cel(draw, RRect(cx - w, base - 28.0, cx + w, base - 11.0, 3.2), MECH_Y, sh=(1.8, 1.4), hi=(1.0, 1.0))
    if not back:
        # hazard stripes and a lamp
        for k in range(3):
            x = cx - 5.0 + k * 3.6
            cel(draw, Poly([(x, base - 14.6), (x + 1.8, base - 14.6), (x + 0.6, base - 12.0), (x - 1.2, base - 12.0)]), MECH_DARK,
                sh=None, line=False)
        cel(draw, Ell(cx + 5.2, base - 19.0, 1.8, 1.8), MECH_GLOW, sh=None, line_color=MECH_DARK, lw=0.6)
        # the cockpit: an open ring with the pilot's head in it
        cel(draw, Ell(hx, hy + 2.0, 10.6, 8.4), MECH_G, sh=(1.0, 1.0))
        cel(draw, Ell(hx, hy + 2.0, 8.8, 6.8), MECH_DARK, sh=None, line=False)
        head_skull(r, draw, SKIN, ears=False)
        head_face(r, draw, SKIN, iris=(80, 120, 170), mood="bright", mouth="grin", brows=False)
        # flight helmet and goggles
        dome = arc_pts(hxx, hyy - 0.6, rx + 1.0, ry + 0.8, 182, 358, 22)
        cel(draw, Poly(dome + [(hxx + rx + 1.0, hyy - 1.4), (hxx - rx - 1.0, hyy - 1.4)]), MECH_HELM, sh=(1.2, 0.8),
            regions=[(RRect(hxx - 1.4, hyy - ry - 2.0, hxx + 1.4, hyy, 0.2), MECH_Y)])
        for s in (-1, 1):
            gx = hxx + s * 3.4
            cel(draw, Ell(gx, hyy - 4.0, 2.2, 1.9), MECH_DARK, sh=None)
            Ell(gx, hyy - 4.0, 1.4, 1.2).draw(draw, fill=MECH_GLOW)
        rim()
    else:
        # from behind: the helmet's back, its stripe and the goggles' strap, over the seat's headrest
        cel(draw, Ell(hxx, hyy - 0.2, rx + 1.0, ry + 1.0), MECH_HELM, sh=(1.2, 1.0),
            regions=[(RRect(hxx - 1.4, hyy - ry - 2.0, hxx + 1.4, hyy + ry, 0.2), MECH_Y),
                     (RRect(hxx - rx - 2.0, hyy - 5.0, hxx + rx + 2.0, hyy - 3.2, 0.2), MECH_DARK)])
        cel(draw, RRect(cx - 6.6, base - 35.5, cx + 6.6, base - 24.0, 2.2), MECH_Y, sh=(0.8, 0.8))
        cel(draw, RRect(cx - 4.0, base - 33.4, cx + 4.0, base - 28.6, 1.4), shade(MECH_Y, 1.3), sh=None, lw=0.6)
        rim()
        # the engine on its back, and the two jump jets under it
        cel(draw, RRect(cx - 5.8, base - 23.8, cx + 5.8, base - 14.6, 1.6), MECH_G, sh=(0.8, 0.8))
        for k in range(2):
            y = base - 21.6 + k * 2.2
            stroke(draw, [(cx - 3.4, y), (cx + 3.4, y)], 0.9, MECH_DARK)
        for k in (-3.2, 3.2):
            cel(draw, Poly([(cx + k - 1.8, base - 16.4), (cx + k + 1.8, base - 16.4), (cx + k + 2.4, base - 12.8),
                            (cx + k - 2.4, base - 12.8)]), MECH_DARK, sh=None)
            _mech_jet(draw, cx + k, base - 12.6, 3.4, 3.8 + flick * 1.2)
    # arms: the minigun is the mech's left (the screen's right head-on, its left from behind)
    gun_side = -1 if back else 1
    for side in (-1, 1):
        sx = cx + side * (w + 1.4)
        cel(draw, Ell(sx, base - 25.0, 4.4, 4.0), MECH_Y, sh=(0.8, 0.8))
        fx, fy = sx + side * 1.4, base - 12.0
        cel(draw, Limb([(sx, base - 22.0), (fx, fy - 2.0)], [2.2, 2.0]), MECH_G, sh=(0.6, 0.0))
        if side == gun_side:
            for k in (-1.3, 0.0, 1.3):
                cel(draw, Limb([(fx + k, fy - 2.0), (fx + k * 1.1, fy + 6.0)], [0.7, 0.7]), MECH_DARK, sh=None, lw=0.5)
            cel(draw, RRect(fx - 2.8, fy - 3.6, fx + 2.8, fy + 0.2, 1.0), MECH_G, sh=None)
        else:
            for k in (-1, 1):
                cel(draw, Limb([(fx + k * 1.5, fy - 0.8), (fx + k * 3.0, fy + 1.6), (fx + k * 1.0, fy + 4.0)], [1.1, 1.0, 0.5]),
                    MECH_G, sh=None)
            cel(draw, RRect(fx - 2.6, fy - 3.4, fx + 2.6, fy - 0.2, 0.9), MECH_G, sh=None)


# ===================================================================
# ANDROID (60) -- white plating, a visor that smiles, jet thrusters
# ===================================================================

AND_WHITE = (242, 246, 252)
AND_CYAN = (80, 222, 255)
AND_JOINT = (70, 76, 96)
AND_POD = (150, 160, 186)
AND_JET = ((110, 200, 255), (180, 236, 255), (255, 255, 255))


def _and_jet(draw, x, y, w, h):
    """A thruster's flame hanging from its nozzle at (x, y) (the kit's `flame` only rises)."""
    from sprite_base import flame_pts
    outer, mid, core = AND_JET

    def pts(ww, hh):
        return [(px, 2.0 * y - py) for (px, py) in flame_pts(x, y, ww, hh)]

    cel(draw, Poly(pts(w, h)), outer, sh=None)
    Poly(pts(w * 0.6, h * 0.62)).draw(draw, fill=mid)
    Poly(pts(w * 0.3, h * 0.32)).draw(draw, fill=core)


def _and_face(draw, x, y, w, d):
    """The visor's face: two lit eyes and a small smile, the same in every frame. In profile the
    narrow eye is the one on the far side of the face, toward where it looks (the shared
    `visor_eyes` narrows the other one, which turns the head away from the camera)."""
    for s in ((-1, 1) if not d else (d, -d)):
        k = 1.0 if not d or s == -d else 0.62
        ex = x + s * w
        cel(draw, RRect(ex - 1.5 * k, y - 1.3, ex + 1.5 * k, y + 1.3, 0.8), AND_CYAN, sh=None, line=False)
        Ell(ex - 0.4 * k, y - 0.4, 0.5, 0.5).draw(draw, fill=(255, 255, 255))
    mx = x + d * 1.4
    stroke(draw, [(mx - 1.5, y + 2.5), (mx, y + 3.2), (mx + 1.5, y + 2.5)], 0.7, AND_CYAN)


def draw_android(draw, ox, oy, direction, frame):
    from sprite_base import clipped, torso_shape
    r = rig(ox, oy, direction, frame, build=0.96)
    d = r.d
    cx = r.cx
    flick = [0.0, 1.2, 0.0, 1.2][frame]

    def jets():
        # two thruster pods on its back: behind the body in profile, over it from behind
        for k in ((-3.3, 3.3) if not d else (0.0,)):
            jx = cx + k - d * 6.3
            cel(draw, RRect(jx - 2.2, r.sh_y - 2.0, jx + 2.2, r.waist_y + 1.2, 1.2), AND_POD, sh=(0.5, 0.5))
            cel(draw, Poly([(jx - 1.5, r.waist_y + 0.8), (jx + 1.5, r.waist_y + 0.8), (jx + 2.0, r.waist_y + 2.6),
                            (jx - 2.0, r.waist_y + 2.6)]), AND_JOINT, sh=None)
            _and_jet(draw, jx, r.waist_y + 2.8, 3.0, 4.0 + flick)

    def arms(layer):
        # a dark frame under white shells: the upper arm is joint, the forearm and hand plating
        rig_arms(r, draw, AND_JOINT, AND_WHITE, layer=layer, hands=False)
        for side in (-1, 1):
            near = r.near(side)
            if (layer == "far" and (not d or near)) or (layer == "near" and d and not near):
                continue
            _, e, w, h = arm_pts(r, side)
            col = AND_WHITE if near else shade(AND_WHITE, 0.6)
            cel(draw, Limb([e, w], [2.5, 2.3]), col, sh=(0.6, 0.3))
            cel(draw, Ell(w[0], w[1] + 0.3, 2.2, 0.9), AND_CYAN, sh=None, lw=0.5)
            cel(draw, Ell(h[0], h[1] + 0.4, 2.2, 2.2), col, sh=(0.6, 0.6))

    if d:
        jets()
        arms("far")
    rig_legs(r, draw, AND_JOINT, AND_WHITE)
    for side in ((-1, 1) if not d else (-d, d)):
        hx0, hy0 = r.hip(side)
        fx, fy = r.foot(side)
        cel(draw, Limb([(hx0 * 0.4 + fx * 0.6, hy0 * 0.4 + fy * 0.6 - 1.0), (fx, fy - 2.6)], [2.4, 2.4]),
            AND_WHITE if r.near(side) else shade(AND_WHITE, 0.6), sh=(0.4, 0.0))
    rig_torso(r, draw, AND_WHITE)
    # a dark waist between the chest and hip shells, its light running round it
    shell = torso_shape(r)
    clipped(draw, shell, lambda l: RRect(cx - 12.0, r.waist_y - 0.2, cx + 12.0, r.waist_y + 2.4, 0.1).draw(l, fill=AND_JOINT))
    clipped(draw, shell, lambda l: stroke(l, [(cx - 12.0, r.waist_y + 1.1), (cx + 12.0, r.waist_y + 1.1)], 0.7, AND_CYAN))
    shell.draw(draw, fill=None, outline=ink(AND_WHITE), width=1.0)
    if not r.back:
        cel(draw, Ell(cx + d * 1.8, r.sh_y + 2.4, 2.2, 2.2), AND_JOINT, sh=None)
        Ell(cx + d * 1.8, r.sh_y + 2.4, 1.4, 1.4).draw(draw, fill=AND_CYAN if frame % 2 == 0 else lit(AND_CYAN, 1.3))
    else:
        jets()
    arms("near")
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
        cel(draw, RRect(vx - w, hy - 2.2, vx + w, hy + 4.8, 2.6), (30, 36, 56), sh=None)
        if d:
            _and_face(draw, hx + d * 4.1, hy + 0.8, 2.9, d)
        else:
            _and_face(draw, vx, hy + 0.8, 4.0, 0)
    else:
        # the back of the dome: one upright seam with a light in it (anything level with the
        # eyes and wider than it is tall reads as a second face)
        cel(draw, RRect(hx - 1.3, hy - ry + 3.4, hx + 1.3, hy + ry - 2.6, 1.2), (30, 36, 56), sh=None)
        stroke(draw, [(hx, hy - ry + 5.2), (hx, hy + ry - 4.6)], 0.7, AND_CYAN)
    # antenna, on its left: the screen's right head-on, its left from behind
    ax = hx - d * 2.0 + ((-4.0 if r.back else 4.0) if not d else 0)
    lean = -1.0 if r.back else 1.0
    cel(draw, Limb([(ax, hy - ry + 0.6), (ax + lean, hy - ry - 3.8)], [0.6, 0.5]), AND_JOINT, sh=None)
    cel(draw, Ell(ax + lean, hy - ry - 4.4, 1.2, 1.2), AND_CYAN, sh=None, lw=0.6)


# ===================================================================
# CHRONOMANCER (61) -- a clock-face halo, gear-trimmed robe, hourglass
# ===================================================================

CHR_ROBE = (54, 64, 138)
CHR_BRASS = (232, 184, 84)
CHR_FACE = (246, 240, 222)
CHR_HAIR = (220, 222, 236)
CHR_INK = (70, 58, 60)
CHR_GLASS = (214, 236, 250)
CHR_SAND = (250, 214, 110)


def _clock(draw, x, y, rad, frame, squash=1.0):
    """The halo: a brass-rimmed dial at ten past ten, the minute hand ticking as he walks.
    `squash` narrows it for the profile, where it is seen at an angle."""
    rx = rad * squash
    fx = max(rx - 1.5, rx * 0.62)
    cel(draw, Ell(x, y, rx, rad), CHR_BRASS, sh=(0.8, 0.8))
    cel(draw, Ell(x, y, fx, rad - 1.5), CHR_FACE, sh=None, line=False)
    k = fx / (rad - 1.5)
    rr = rad - 2.1
    for i in range(12):
        a = math.radians(i * 30)
        t = 0.8 if i % 3 else 0.7
        stroke(draw, [(x + math.cos(a) * rr * k, y + math.sin(a) * rr), (x + math.cos(a) * rr * t * k, y + math.sin(a) * rr * t)],
               0.6 if i % 3 else 0.9, CHR_INK)
    tick = [0.0, 7.0, 0.0, -7.0][frame]
    for ang, ln, wd in ((-150.0, 0.44, 1.2), (-30.0 + tick, 0.66, 0.9)):
        a = math.radians(ang)
        stroke(draw, [(x, y), (x + math.cos(a) * rad * ln * k, y + math.sin(a) * rad * ln)], wd, CHR_INK)
    Ell(x, y, 0.9, 0.9).draw(draw, fill=CHR_BRASS)


def _chr_gear(draw, gx, gy, rad):
    pts = []
    for i in range(16):
        a = math.radians(i * 22.5)
        k = rad if i % 2 == 0 else rad * 0.74
        pts.append((gx + math.cos(a) * k, gy + math.sin(a) * k))
    cel(draw, Poly(pts), CHR_BRASS, sh=None, lw=0.6)
    Ell(gx, gy, rad * 0.34, rad * 0.34).draw(draw, fill=ink(CHR_BRASS))


def _chr_staff(draw, bx, by, tx, ty):
    """A brass staff from the ground at (bx, by) to (tx, ty), an hourglass set on its end and
    leaning as it leans."""
    cel(draw, Limb([(bx, by), (tx, ty)], [1.2, 1.1]), CHR_BRASS, sh=None, lw=0.7)
    ang = math.degrees(math.atan2(ty - by, tx - bx))

    def at(pts):
        return xform(pts, tx, ty, ang)

    cel(draw, Poly(at([(1.0, -3.0), (4.5, -0.5), (8.0, -3.0), (8.0, 3.0), (4.5, 0.5), (1.0, 3.0)])), CHR_GLASS, sh=None,
        line_color=shade(CHR_GLASS, 2.0))
    # what sand is left above, the heap below, and the thread between
    Poly(at([(4.7, -0.45), (6.4, -1.7), (6.4, 1.7), (4.7, 0.45)])).draw(draw, fill=CHR_SAND)
    Poly(at([(1.2, -2.5), (3.2, 0.0), (1.2, 2.5)])).draw(draw, fill=CHR_SAND)
    stroke(draw, at([(1.4, 0.0), (4.8, 0.0)]), 0.5, CHR_SAND)
    stroke(draw, at([(5.4, -1.5), (7.4, -2.5)]), 0.5, (255, 255, 255))
    for u in (-0.4, 7.8):
        cel(draw, Poly(at([(u, -3.9), (u + 1.9, -3.9), (u + 1.9, 3.9), (u, 3.9)])), CHR_BRASS, sh=None, lw=0.6)
    kx, ky = at([(10.9, 0.0)])[0]
    cel(draw, Ell(kx, ky, 1.4, 1.4), CHR_BRASS, sh=None, lw=0.6)


def draw_chronomancer(draw, ox, oy, direction, frame):
    from sprite_base import clipped
    r = rig(ox, oy, direction, frame)
    # the staff is planted: the arm that holds it does not swing with the stride
    ra = rig(ox, oy, direction, frame, step=0)
    d = r.d
    hs = _hold_side(r)
    hx, hy = r.hx, r.head_cy
    cx = r.cx
    # the clock rises behind the head like a sun: behind it head-on, seen at an angle in profile,
    # and in front of it from behind (drawn last, further down)
    clock_y = hy - 11.4
    if d:
        _clock(draw, hx - d * 9.5, clock_y, 11.6, frame, squash=0.43)
    elif not r.back:
        _clock(draw, hx, clock_y, 11.6, frame)
    if d:
        rig_arms(r, draw, CHR_ROBE, SKIN, layer="far", cuff=CHR_BRASS)
    rig_legs(r, draw, shade(CHR_ROBE, 1.0), (60, 50, 70))
    robe = rig_robe(r, draw, CHR_ROBE, flare=3.2)
    # the hem's brass band, wide enough to be brass (the kit's 1.6 is all ink)
    hem = r.base_y - 2.6
    clipped(draw, robe, lambda l: RRect(cx - 20.0, hem - 2.9, cx + 20.0, hem + 1.0, 0.1).draw(l, fill=CHR_BRASS))
    clipped(draw, robe, lambda l: stroke(l, [(cx - 20.0, hem - 2.9), (cx + 20.0, hem - 2.9)], 0.6, ink(CHR_BRASS)))
    robe.draw(draw, fill=None, outline=ink(CHR_ROBE), width=1.0)
    # a sash worn high, clear of the hands, with a gear for its clasp
    sy = r.waist_y - 2.4
    hw = 4.65 if d else r.hip_w + 0.3
    cel(draw, RRect(cx - hw, sy, cx + hw, sy + 2.8, 0.6), CHR_BRASS, sh=(0.0, 0.7))
    if not r.back:
        _chr_gear(draw, cx + d * 3.2, sy + 1.4, 2.9 if not d else 2.5)
    if d:
        rig_arms(ra, draw, CHR_ROBE, SKIN, layer="near", cuff=CHR_BRASS, hands=False, reach=0.8, out=4.6)
    else:
        rig_arms(ra, draw, CHR_ROBE, SKIN, sides=(hs,), cuff=CHR_BRASS, hands=False, out=3.0)
        rig_arms(r, draw, CHR_ROBE, SKIN, sides=(-hs,), cuff=CHR_BRASS)
    rig_head(r, draw, SKIN, hair=CHR_HAIR, style="swept", eye_color=(210, 160, 60), expression="set", mood="calm")
    # a monocle
    if not r.back:
        e1, e2, ey, _, _ = face_anchor(r)
        ex = e2 if not d else e1
        layer = draw.sub()
        Ell(ex, ey, 3.2, 3.0).draw(layer, fill=None, outline=CHR_BRASS, width=0.8)
        draw.merge(layer)
        stroke(draw, [(ex + 2.6, ey + 2.0), (ex + 3.6, ey + 7.0)], 0.4, CHR_BRASS)
    else:
        _clock(draw, hx, clock_y, 11.6, frame)
    # the staff: out to his side head-on and from behind, planted ahead of him in profile, leaning
    # so that the hourglass stands clear of his face
    by = r.base_y - 1.0
    if d:
        _chr_staff(draw, cx + d * 3.0, by, cx + d * 16.8, hy - 0.4)
        rig_hand(ra, draw, d, SKIN, reach=0.8, out=4.6)
    else:
        _chr_staff(draw, cx + hs * 8.5, by, cx + hs * 16.6, hy + 2.6)
        rig_hand(ra, draw, hs, SKIN, out=3.0)


# ===================================================================
# GRAVITON (62) -- anti-gravity hair, a glowing-lined suit, orbiting spheres
# ===================================================================

# White hair over a lavender face (the old skin was the hair's own white: one pale ball), a dark
# suit under a lighter yoke with the glow lines drawn wide enough to be there, and gloves and boots
# in the yoke's violet so that hands and feet are not lost in the suit.
GRV_SUIT = (64, 44, 106)
GRV_TRIM = (112, 84, 178)
GRV_GLOW = (190, 130, 255)
GRV_HAIR = (240, 236, 252)
GRV_SKIN = (200, 178, 230)
GRV_EYE = (118, 60, 218)
GRV_RING = (112, 66, 200)
GRV_VOL = 1.8
GRV_STYLE = dict(vol=GRV_VOL, fringe=((-0.7, 3.4), (-0.25, 4.2), (0.2, 4.0), (0.62, 3.2)), sweep=0.0, side=2.0, back=6.0)
# The locks that float up off the crown: where on the crown each is rooted (degrees, 270 the top),
# how far it rises, how it leans.
_GRV_LOCKS = ((230, 5.4, -1.8), (256, 7.4, -0.6), (283, 6.6, 0.7), (309, 4.8, 1.9))


def _grv_locks(r, frame):
    """Hair with no weight to it: four fat locks drifting up off the crown like weed under water,
    each one a slow wave with a blunt tip (straight and pointed they were spikes, and thin they
    were all outline), the wave running up them as he walks; walking sideways, they trail."""
    hx, hy = r.hx - r.d * 0.9, r.head_cy - GRV_VOL * 0.35
    orx, ory = r.head_rx + GRV_VOL, r.head_ry + GRV_VOL
    k = r.d if r.d else 1
    wave = frame * math.pi / 2.0
    locks = []
    for i, (ang, h, lean) in enumerate(_GRV_LOCKS):
        a = math.radians(ang)
        x0, y0 = hx + k * math.cos(a) * (orx - 2.4), hy + math.sin(a) * (ory - 2.4)
        top = hy + math.sin(a) * ory - h
        pts = []
        for j in range(7):
            t = j / 6.0
            pts.append((x0 + k * lean * t + math.sin(wave + i * 1.9 + t * 3.2) * 1.4 * t - r.d * h * 0.3 * t * t, y0 + (top - y0) * t))
        locks.append(Limb(pts, [3.6, 3.5, 3.3, 3.0, 2.6, 2.2, 1.8]))
    return locks


def _grv_join(r, draw, locks):
    """Paint out the crown's line where a lock crosses it, so that hair and locks are one mass
    (the locks are drawn before the head, and the kit's hair is lined all the way round): a band
    along the crown, kept a line's width inside the locks so that their own line stays."""
    from PIL import ImageFilter
    hx, hy = r.hx - r.d * 0.9, r.head_cy - GRV_VOL * 0.35
    orx, ory = r.head_rx + GRV_VOL, r.head_ry + GRV_VOL
    mask = draw.sub()
    for s in locks:
        s.draw(mask, fill=(255, 255, 255, 255))
    k = int(round(0.8 * mask._s))
    mask._img.putalpha(mask._img.getchannel("A").filter(ImageFilter.MinFilter(2 * k + 1)))
    layer = draw.sub()
    Poly(arc_pts(hx, hy, orx + 1.0, ory + 1.0, 200, 340, 24) + arc_pts(hx, hy, orx - 1.2, ory - 1.2, 340, 200, 24)).draw(layer, fill=GRV_HAIR)
    draw.merge(layer, clip=mask)


def draw_graviton(draw, ox, oy, direction, frame):
    from sprite_base import clipped, torso_shape
    r = rig(ox, oy, direction, frame, bob=[-1, -2, -2, -1][frame])
    d = r.d
    cx = r.cx
    # the gravity ring: on the ground, where it stays while he bobs over it; the boots hide the back
    # of it and the front of it lies clear below them
    gy = oy + 53.4
    rr = 11.2 + [0.0, 0.9, 0.3, 0.9][frame]
    layer = draw.sub()
    Ell(cx, gy, rr, 2.4).draw(layer, fill=None, outline=GRV_RING, width=1.7)
    Ell(cx, gy, rr, 2.4).draw(layer, fill=None, outline=GRV_GLOW, width=0.8)
    draw.merge(layer)
    # three spheres in orbit about his waist, all one violet (three colours a third of a turn apart
    # jumped back a place every cycle), the near ones bigger
    orbit = []
    for i in range(3):
        a = math.radians(20 + frame * 30 + i * 120)
        orbit.append((math.sin(a), cx + math.cos(a) * 16.0, r.waist_y - 1.0 + math.sin(a) * 4.2))

    def sphere(x, y, z):
        k = 2.9 + 0.5 * z
        cel(draw, Ell(x, y, k, k), GRV_GLOW if z >= 0 else shade(GRV_GLOW, 0.8), sh=(0.7, 0.7), hi=(0.5, 0.5))
        Ell(x - k * 0.28, y - k * 0.3, k * 0.34, k * 0.34).draw(draw, fill=(255, 255, 255))

    for (z, x, y) in orbit:
        if z < 0:
            sphere(x, y, z)
    if d:
        rig_arms(r, draw, GRV_SUIT, GRV_TRIM, layer="far", cuff=GRV_GLOW)
    rig_legs(r, draw, GRV_SUIT, GRV_TRIM)
    rig_torso(r, draw, GRV_SUIT)
    # a lighter yoke across the shoulders, cut to a V; the glow runs along its edge to the core on
    # his chest (from behind, to a ring between the shoulder blades)
    shell = torso_shape(r)
    top = r.sh_y - 2.4
    vx, vy = cx + d * 1.6, r.sh_y + 4.4
    yoke = Poly([(cx - 9.0, top - 1.0), (cx + 9.0, top - 1.0), (cx + 9.0, r.sh_y + 0.8), (vx, vy), (cx - 9.0, r.sh_y + 0.8)])
    clipped(draw, shell, lambda l: yoke.draw(l, fill=GRV_TRIM))
    clipped(draw, shell, lambda l: stroke(l, [(cx - 9.0, r.sh_y + 0.8), (vx, vy), (cx + 9.0, r.sh_y + 0.8)], 1.3, GRV_GLOW))
    shell.draw(draw, fill=None, outline=ink(GRV_SUIT), width=1.0)
    if r.back:
        ring = draw.sub()
        Ell(vx, vy + 0.6, 2.7, 2.7).draw(ring, fill=None, outline=GRV_GLOW, width=1.1)
        draw.merge(ring)
        Ell(vx, vy + 0.6, 0.8, 0.8).draw(draw, fill=(255, 255, 255))
    else:
        cel(draw, Ell(vx, vy, 2.3, 2.3), GRV_GLOW, sh=None)
        Ell(vx, vy, 1.0, 1.0).draw(draw, fill=(255, 255, 255))
    rig_belt(r, draw, shade(GRV_SUIT, 1.6), buckle=GRV_GLOW)
    rig_arms(r, draw, GRV_SUIT, GRV_TRIM, layer="near", cuff=GRV_GLOW, reach=0.3)
    locks = _grv_locks(r, frame)
    blob(draw, locks, GRV_HAIR, sh=(0.9, 0.6))
    rig_head(r, draw, GRV_SKIN, hair=GRV_HAIR, style=GRV_STYLE, eye_color=GRV_EYE, expression="set", mood="bright", blush=False)
    _grv_join(r, draw, locks)
    for (z, x, y) in orbit:
        if z >= 0:
            sphere(x, y, z)


# ===================================================================
# TESLA (63) -- wild white hair, goggles, lab coat, a coil on his back
# ===================================================================

TES_COAT = (244, 246, 250)
TES_COPPER = (222, 140, 82)
TES_SPARK = (130, 214, 255)
TES_STEEL = (210, 214, 226)
TES_PACK = (110, 110, 126)


def _tes_coil(draw, x, y0, y1):
    """One tower of the coil: copper windings from y0 up to y1 on a dark foot, a steel ball on
    top. Returns the ball's centre."""
    cel(draw, RRect(x - 2.6, y0 - 0.8, x + 2.6, y0 + 1.2, 0.6), TES_PACK, sh=None)
    turns = []
    y = y1 + 0.2
    while y < y0:
        turns.append((Poly([(x - 2.4, y + 0.9), (x + 2.4, y), (x + 2.4, y + 1.3), (x - 2.4, y + 2.2)]), shade(TES_COPPER, 1.6)))
        y += 2.6
    cel(draw, RRect(x - 1.8, y1, x + 1.8, y0, 0.6), TES_COPPER, sh=None, regions=turns)
    by = y1 - 1.9
    cel(draw, Ell(x, by, 2.7, 2.7), TES_STEEL, sh=(0.7, 0.7), hi=(0.5, 0.5))
    return (x, by)


def _tes_arc(draw, pts):
    """An arc of current: one width along its length (the kit's `bolt` tapers to a point)."""
    cel(draw, Limb(pts, 0.85, cap0=True), TES_SPARK, sh=None, line_color=shade(TES_SPARK, 2.2), lw=0.6)
    stroke(draw, pts, 0.5, (255, 255, 255))


def draw_tesla(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame)
    d = r.d
    cx = r.cx
    hy = r.head_cy
    jag = [-1.6, 0.0, 1.6, 0.0][frame]

    def apparatus():
        # the coil pack: two copper towers on a yoke, standing up past his head with an arc
        # jumping between their tips. Head-on it is behind him; from behind it is the whole
        # picture; in profile the towers stand behind his head, one hiding the other.
        y0, y1 = r.sh_y - 2.5, hy - 13.0
        if d:
            tx = cx - d * 16.2
            cel(draw, Limb([(cx - d * 8.0, r.sh_y - 1.4), (tx, y0 + 0.4)], [1.3, 1.3]), TES_PACK, sh=None)
            (bx, by) = _tes_coil(draw, tx, y0, y1)
            px = cx - d * 6.8
            cel(draw, RRect(px - 3.0, r.sh_y - 3.0, px + 3.0, r.waist_y + 2.0, 1.2), TES_PACK, sh=(0.6, 0.6))
            cel(draw, RRect(px - 3.0, r.sh_y + 0.6, px + 3.0, r.sh_y + 3.0, 0.3), TES_COPPER, sh=None, lw=0.6)
            if frame % 2 == 0:
                _tes_arc(draw, [(bx + d * 2.2, by - 1.4), (bx + d * 4.4, by - 5.2 + jag), (bx + d * 7.4, by - 3.6 - jag),
                                (bx + d * 10.4, by - 7.0)])
            else:
                sparkle(draw, bx - d * 0.6, by - 4.2, 1.8, TES_SPARK)
                sparkle(draw, bx + d * 3.4, by - 1.6, 1.3, TES_SPARK)
            return
        tips = []
        for s in (-1, 1):
            tx = cx + s * 16.0
            cel(draw, Limb([(cx + s * 4.0, r.sh_y - 1.0), (tx, y0 + 0.4)], [1.3, 1.3]), TES_PACK, sh=None)
            tips.append(_tes_coil(draw, tx, y0, y1))
        if r.back:
            cel(draw, RRect(cx - 5.4, r.sh_y - 2.6, cx + 5.4, r.waist_y + 2.2, 1.4), TES_PACK, sh=(0.8, 0.8))
            cel(draw, RRect(cx - 5.4, r.sh_y + 0.8, cx + 5.4, r.sh_y + 3.4, 0.3), TES_COPPER, sh=None, lw=0.6)
            cel(draw, Ell(cx, r.waist_y - 0.2, 1.6, 1.6), TES_SPARK, sh=None, line_color=ink(TES_PACK), lw=0.6)
        (x0, yb), (x1, _) = tips
        if frame % 2 == 0:
            peak = yb - 6.0
            _tes_arc(draw, [(x0 + 2.2, yb - 1.4), (x0 + 6.0, peak + 2.0 + jag), (cx - 4.0, peak - jag), (cx + 3.0, peak + 1.4 + jag),
                            (x1 - 6.0, peak + 0.6 - jag), (x1 - 2.2, yb - 1.4)])
        else:
            k = 1 if frame == 1 else -1
            sparkle(draw, x0 + 1.0 * k, yb - 4.2, 1.8, TES_SPARK)
            sparkle(draw, x1 + 1.6 * k, yb - 3.8, 1.5, TES_SPARK)

    if not r.back:
        apparatus()
    if d:
        rig_arms(r, draw, TES_COAT, (70, 70, 80), layer="far")
    rig_legs(r, draw, (70, 72, 88), (90, 70, 56))
    rig_robe(r, draw, TES_COAT, flare=2.4, split=True, hem=r.hip_y + 4.0)
    if not r.back:
        cel(draw, Poly([(cx - 2.2 + d * 1.8, r.sh_y - 2.2), (cx + 2.2 + d * 1.8, r.sh_y - 2.2), (cx + 1.4 + d * 1.8, r.waist_y + 1.0),
                        (cx - 1.4 + d * 1.8, r.waist_y + 1.0)]), (90, 120, 170), sh=None)
        if not d:
            # the pack's straps
            for s in (-1, 1):
                cel(draw, Limb([(cx + s * 4.0, r.sh_y - 1.8), (cx + s * 4.8, r.waist_y + 1.6)], [0.75, 0.75], cap=False),
                    (136, 92, 64), sh=None, lw=0.5)
    rig_arms(r, draw, TES_COAT, (70, 70, 80), layer="near")
    rig_head(r, draw, SKIN, hair=(248, 248, 252), style="wild", eye_color=(80, 150, 220), expression="grin", hat=True)
    hx, rx = r.hx, r.head_rx
    # goggles up on the forehead
    if not r.back:
        stroke(draw, [(hx - rx - 0.4, hy - 4.6), (hx + rx + 0.4, hy - 4.6)] if not d else [(hx - d * 3.0, hy - 4.6), (hx + d * (rx + 0.4), hy - 4.8)],
               1.0, (70, 60, 60))
        for k in ((-3.0, 3.0) if not d else (d * 4.0,)):
            gx = hx + k + d * 1.4
            cel(draw, Ell(gx, hy - 5.0, 2.6, 2.2), TES_COPPER, sh=None)
            cel(draw, Ell(gx, hy - 5.0, 1.6, 1.3), TES_SPARK, sh=None, line=False)
            Ell(gx - 0.5, hy - 5.5, 0.5, 0.5).draw(draw, fill=(255, 255, 255))
    else:
        apparatus()


# ===================================================================
# NANOSWARM (64) -- a little hex-tiled bot in a cloud of nanites
# ===================================================================

# Three greens and a line: the head's bright shell, a deeper one for the body, and the glow of
# the eyes, the cells and half the swarm. Every part is lined in navy rather than in the shell's
# own dark teal, which is the space maps' deck to a shade: the bot had no edge there.
NANO_BODY = (52, 188, 150)
NANO_MID = (32, 140, 134)
NANO_DARK = (22, 46, 66)
NANO_GLOW = (160, 255, 214)
NANO_LINE = (20, 42, 84)

# The cloud: where each loose nanite hangs about the bot (from its middle, on the ground), its
# size, which green it is and where on its little round it starts. Six, each big enough to be a
# hexagon at game size (the old ten were crumbs), and none of them before the face.
_NANO_CLOUD = ((-15.4, -34.0, 2.8, 2, 0.0), (-18.4, -24.0, 1.9, 0, 2.2), (16.0, -27.0, 2.6, 0, 4.0), (13.6, -38.0, 1.9, 1, 1.0),
               (-14.6, -6.4, 2.1, 0, 3.2), (15.8, -5.6, 2.5, 2, 5.2))


def _hexagon(draw, x, y, rad, color, line=True, rot=30.0):
    pts = [(x + rad * math.cos(math.radians(a + rot)), y + rad * math.sin(math.radians(a + rot))) for a in range(0, 360, 60)]
    cel(draw, Poly(pts), color, sh=(0.5, 0.5) if rad >= 1.9 else None, line=line, line_color=NANO_LINE, lw=0.6)


def _nano_cells(draw, x, y, rad, color):
    """Three cells of the honeycomb, one over two: the bot's emblem, lit on its chest and dark on
    the back of its head."""
    for (u, v) in ((0.0, -0.75), (-0.866, 0.75), (0.866, 0.75)):
        _hexagon(draw, x + u * rad, y + v * rad, rad, color)


def draw_nanoswarm(draw, ox, oy, direction, frame):
    d, back = _dir(direction)
    bob = [0, -1, -2, -1][frame]
    ground = oy + 54
    base = ground + bob
    cx = ox + 32
    wave = [0, 1, 0, -1][frame]
    tones = (NANO_BODY, NANO_MID, NANO_GLOW)
    # the cloud: each nanite goes round a little circle of its own, once a cycle; all of them keep
    # the one way up, as tiles of the same honeycomb (tumbling, a hexagon this small is a ball)
    for (u, v, k, tone, at) in _NANO_CLOUD:
        a = at + frame * math.pi / 2.0
        _hexagon(draw, cx + u + math.cos(a) * 1.8, ground + v + math.sin(a) * 1.8, k, tones[tone])
    # no legs: the core floats, and under it the body comes apart into nanites, a row of three that
    # rides with it and two on the ground that do not, so the stream stretches as it bobs
    sw = 3.7 if d else 4.4
    for i, (u, v, k, y0) in enumerate(((-1.0, -5.3, 2.1, base), (1.0, -5.3, 2.1, base), (0.0, -4.9, 2.3, base), (-0.5, -1.9, 1.6, ground), (0.5, -1.7, 1.5, ground))):
        _hexagon(draw, cx + u * sw - d * 0.8 + ((i + frame) % 2 - 0.5) * 0.7, y0 + v, k, tones[(i + frame) % 3])
    bw = 6.4 if d else 8.0
    body = Poly([(cx - bw, base - 20.0), (cx + bw, base - 20.0), (cx + bw + 1.0, base - 14.0), (cx + bw * 0.62, base - 8.0), (cx - bw * 0.62, base - 8.0),
                 (cx - bw - 1.0, base - 14.0)])
    cel(draw, body, NANO_MID, sh=(1.2, 1.0), hi=(0.6, 0.6), line_color=NANO_LINE)
    if back:
        _hexagon(draw, cx, base - 14.4, 2.3, NANO_GLOW)
    else:
        _nano_cells(draw, cx + d * 1.6, base - 14.3, 2.6, NANO_GLOW)
    # arms made of nanites: three to a chain, the hand smallest
    for side in ((-1, 1) if not d else (d,)):
        for j, (k, tone) in enumerate(((2.4, 0), (2.0, 0), (1.7, 2))):
            x = cx + (side * (10.8 + j * 3.0) if not d else d * (7.8 + j * 2.9))
            y = base - 17.2 + j * 2.6 + wave * (1 if side > 0 else -1) * (0.4 + j * 0.7)
            _hexagon(draw, x, y, k, tones[tone])
    # head: a rounded hex with a big visor eye
    hx, hy = cx + d * 2.0, base - 30.0
    cel(draw, Limb([(hx - d * 2.0, hy - 8.6), (hx - d * 3.0, hy - 13.0)], [0.9, 0.75]), NANO_DARK, sh=None, line_color=NANO_LINE, lw=0.6)
    _hexagon(draw, hx - d * 3.0, hy - 13.8, 2.1, NANO_GLOW)
    head = Poly([(hx + 10.4 * math.cos(math.radians(a)), hy + 9.6 * math.sin(math.radians(a))) for a in range(0, 360, 60)])
    cel(draw, head, NANO_BODY, sh=(1.2, 1.0), hi=(0.8, 0.8), line_color=NANO_LINE)
    if back:
        _nano_cells(draw, hx, hy + 0.2, 2.7, NANO_MID)
        return
    vx = hx + d * 3.0
    cel(draw, RRect(vx - 6.4 if not d else vx - 4.4, hy - 2.6, vx + 6.4 if not d else vx + 4.4, hy + 3.4, 2.4), NANO_DARK, sh=None, line_color=NANO_LINE)
    visor_eyes(draw, vx, hy + 0.4, 3.2, NANO_GLOW, d, happy=frame % 2 == 1)


# ===================================================================
# VOIDWALKER (65) -- a starfield in a dark body, a void lance
# ===================================================================

# A dark body is what it is: the engine shows these darks as darks. What parts it into planes
# is the violet line every part is edged in (the cloak always was; now the head and arms too),
# a nebula band a step lighter across the cloak, and stars big enough to be there at 77px.
VOID_BODY = (36, 24, 58)
VOID_DEEP = (24, 15, 42)
VOID_NEB = (70, 38, 118)
VOID_EDGE = (110, 60, 170)
VOID_RIM = VOID_EDGE
VOID_GLOW = (236, 90, 255)
VOID_HOT = (255, 228, 255)


def _void_star(draw, x, y, big, on):
    """One star: a four-point twinkle when `big`, a dot otherwise; `on` is its bright frame."""
    if big:
        sparkle(draw, x, y, 2.6 if on else 2.0, (255, 255, 255))
    else:
        rad = 1.0 if on else 0.8
        Ell(x, y, rad, rad).draw(draw, fill=(255, 255, 255) if on else (212, 196, 255))


def _void_arm(r, draw, side, reach=0.0, lift=0.0, out=0.0):
    """An arm as dark as the cloak, lined in the rim of light (rig_arms would ink it in a
    near-black and lose it against the body). Returns where the hand goes."""
    s, e, w, h = arm_pts(r, side, reach, lift, out)
    cel(draw, Limb([s, e, w], [2.2, 2.0, 1.8]), VOID_BODY if r.near(side) else VOID_DEEP, sh=(0.8, 0.4), tone=VOID_DEEP,
        line_color=VOID_RIM)
    return h


def draw_voidwalker(draw, ox, oy, direction, frame):
    from sprite_base import spear
    r = rig(ox, oy, direction, frame, bob=[-1, -2, -2, -1][frame], step=0)
    d = r.d
    hs = _hold_side(r)
    cx, base = r.cx, r.base_y
    wave = [0.0, 1.0, 0.0, -1.0][frame]
    tw = frame % 2 == 0
    top = r.sh_y - 2.4
    w = r.sh_w
    # a cloak of void, frayed into the dark below; walking, it trails behind
    if d:
        cloak = Poly([(cx - d * 3.4, top), (cx + d * 3.0, top), (cx + d * 5.4, r.hip_y - 1.0),
                      (cx + d * (4.4 + wave * 0.5), base - 1.2), (cx + d * 1.2, base - 4.4),
                      (cx - d * (2.4 - wave * 0.8), base - 0.6), (cx - d * 5.4, base - 4.6),
                      (cx - d * (10.0 - wave * 1.2), base - 1.6), (cx - d * 7.6, r.hip_y + 0.6),
                      (cx - d * 5.4, r.sh_y + 1.6)])
        neb = Limb([(cx - d * 8.5, base - 4.5), (cx - d * 4.5, base - 8.0), (cx - d * 0.5, base - 11.5), (cx + d * 3.0, base - 15.0),
                    (cx + d * 6.0, base - 19.0)], [0.2, 2.0, 2.8, 2.0, 0.2], cap=False)
        stars = ((-3.6, 6.5, True), (-5.8, 14.2, False), (3.0, 14.0, False))
    else:
        cloak = Poly([(cx - w + 1.8, top), (cx + w - 1.8, top), (cx + w, r.sh_y + 0.6),
                      (cx + w + 4.8, base - 1.0 + wave * 0.6), (cx + 7.6, base - 4.6), (cx + 4.4 + wave * 0.6, base - 1.4),
                      (cx + 2.0, base - 5.0), (cx + wave * 0.5, base - 0.4), (cx - 2.0, base - 5.0),
                      (cx - 4.4 + wave * 0.6, base - 1.4), (cx - 7.6, base - 4.6), (cx - w - 4.8, base - 1.0 - wave * 0.6),
                      (cx - w, r.sh_y + 0.6)])
        k = -1 if r.back else 1      # the same band, seen from the other side
        neb = Limb([(cx - k * 10.0, base - 4.0), (cx - k * 5.5, base - 7.5), (cx - k * 0.5, base - 11.0), (cx + k * 4.0, base - 15.0),
                    (cx + k * 8.0, base - 20.0)], [0.2, 2.2, 3.2, 2.2, 0.2], cap=False)
        stars = ((-3.4 * k, 5.6, True), (5.2 * k, 12.6, True), (3.6 * k, 3.2, False), (-6.4 * k, 13.4, False), (0.6 * k, 13.6, False))
    if d:
        _void_arm(r, draw, -d)
    cel(draw, cloak, VOID_BODY, sh=(1.8, 1.0), tone=VOID_DEEP, regions=[(neb, VOID_NEB)], line_color=VOID_RIM)
    for i, (u, v, big) in enumerate(stars):
        _void_star(draw, cx + (u * d if d else u), r.sh_y + v, big, tw == (i % 2 == 0))
    # the arms: one at rest, one out to the lance
    if d:
        grip = _void_arm(r, draw, d, 0.9)
    else:
        _void_arm(r, draw, -hs)
        grip = _void_arm(r, draw, hs, 0.0, 3.0, 5.4)
    # the head: a dark world with a rim of light
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    cel(draw, Ell(hx, hy, rx, ry), VOID_BODY, sh=(1.4, 1.2), tone=VOID_DEEP, line_color=VOID_RIM)
    if r.back:
        hstars = ((-4.6, 0.6, True), (6.0, 5.6, True), (5.6, -3.6, False), (-7.4, 6.4, False), (0.4, 8.2, False))
    elif d:
        hstars = ((-6.6 * d, -1.6, True), (-8.4 * d, 4.8, False), (-2.4 * d, 7.8, False))
    else:
        hstars = ((8.2, -3.2, True), (-8.6, -2.2, False), (-6.6, 7.0, False), (3.2, 8.4, False))
    for i, (u, v, big) in enumerate(hstars):
        _void_star(draw, hx + u, hy + v, big, tw == (i % 2 == 1))
    if not r.back:
        e1, e2, ey, _, _ = face_anchor(r)
        for side, ex in (((-1, e1), (1, e2)) if not d else ((-d, e1), (d, e2))):
            k = 1.0 if not d or side == -d else 0.62
            Poly([(ex - side * 3.0 * k, ey + 1.0), (ex - side * 0.8 * k, ey - 1.9), (ex + side * 2.9 * k, ey - 2.3),
                  (ex + side * 2.1 * k, ey + 1.8), (ex - side * 1.0 * k, ey + 2.6)]).draw(draw, fill=VOID_GLOW)
            Ell(ex + side * 0.2 * k, ey + 0.1, 1.1 * k, 0.9).draw(draw, fill=VOID_HOT)
    # a crown of void flame: violet, burning to a white-hot core
    flame(draw, hx - d * 1.4, hy - ry + 4.0, 19.0, 15.0, (VOID_EDGE, VOID_GLOW, VOID_HOT), sway=wave * 1.2 - d * 2.4)
    # the void lance: upright at arm's length head-on, levelled under the chin when walking,
    # so it never crosses the face
    if d:
        ux, uy = d * math.cos(math.radians(40.0)), -math.sin(math.radians(40.0))
        x0, y0 = grip[0] - ux * 11.0, grip[1] - uy * 11.0
        x1, y1 = grip[0] + ux * 14.0, grip[1] + uy * 14.0
    else:
        lean = hs * 0.09
        y0, y1 = base - 0.8, r.head_cy - 9.5
        x0, x1 = grip[0] + lean * (grip[1] - y0), grip[0] + lean * (grip[1] - y1)
    spear(draw, x0, y0, x1, y1, shaft=VOID_EDGE, head=VOID_GLOW, band=VOID_HOT, head_len=8.0, head_w=3.0)
    ln = math.hypot(x1 - x0, y1 - y0)
    ax, ay = (x1 - x0) / ln, (y1 - y0) / ln
    stroke(draw, [(x1 + ax * 1.8, y1 + ay * 1.8), (x1 + ax * 5.4, y1 + ay * 5.4)], 1.0, VOID_HOT)
    if d:
        rig_hand(r, draw, d, VOID_RIM, reach=0.9)
    else:
        rig_hand(r, draw, hs, VOID_RIM, lift=3.0, out=5.4)
        rig_hand(r, draw, -hs, VOID_RIM)


# ===================================================================
# PHOTON (66) -- a being of light: radiant hair, a prism, rainbow glints
# ===================================================================

PHO_BODY = (255, 246, 196)
PHO_GOLD = (255, 214, 96)
PHO_RAY = (255, 238, 150)
PHO_RAY_EDGE = (244, 160, 44)
PHO_GLASS = (226, 248, 255)
PRISM = ((255, 110, 120), (255, 200, 90), (120, 230, 140), (110, 180, 255), (190, 130, 255))
# hair as radiant as it was, a little lower on top: the prism floats over it
PHO_HAIR = dict(vol=1.8, fringe=((-0.74, 4.2), (-0.3, 5.3), (0.12, 4.6), (0.54, 5.3), (0.88, 3.4)),
                sweep=0.6, side=2.4, back=6.0, spikes=(7, 2.4))


def _pho_halo(r, draw, frame):
    """The sunburst behind the head: twelve rays that clear the hair, so it is a sun and not
    stray thorns. Walking, the ones that would cross the face are left off and it streams
    behind him; the gap at the top is where the prism floats."""
    d = r.d
    ox, oy = r.hx - d * 2.6, r.head_cy - 0.5
    pts = []
    for i in range(12):
        a = 15.0 + i * 30.0
        fwd = math.cos(math.radians(a)) * d
        tip = 21.6 if (i + frame) % 2 == 0 else 18.4
        if d and fwd > 0.55:
            tip = 11.5
        for (da, rad) in ((-15.0, 11.0), (0.0, tip)):
            t = math.radians(a + da)
            pts.append((ox + rad * math.cos(t), oy + rad * 0.97 * math.sin(t)))
    # light, so not inked like a solid thing: a pale ray with a warm edge
    cel(draw, Poly(pts), PHO_RAY, sh=None, line_color=PHO_RAY_EDGE, lw=0.8)


def _pho_prism(draw, x, y):
    """A glass prism: pale faces, a hard glint, a line the colour of sky and not of slate."""
    tri = Poly([(x, y - 3.4), (x + 3.3, y + 2.6), (x - 3.3, y + 2.6)])
    cel(draw, tri, PHO_GLASS, sh=None, line_color=(64, 142, 226), lw=0.9,
        regions=[(Poly([(x, y - 3.4), (x + 3.3, y + 2.6), (x + 0.3, y + 2.6)]), (168, 216, 255))])
    stroke(draw, [(x - 0.5, y - 1.5), (x - 1.6, y + 1.2)], 0.7, (255, 255, 255))


def draw_photon(draw, ox, oy, direction, frame):
    from sprite_base import clipped
    r = rig(ox, oy, direction, frame, bob=[-1, -2, -2, -1][frame])
    d = r.d
    cx = r.cx
    hx, hy = r.hx, r.head_cy
    _pho_halo(r, draw, frame)
    if d:
        rig_arms(r, draw, PHO_BODY, (255, 250, 232), layer="far")
    rig_legs(r, draw, PHO_BODY, PHO_GOLD)
    hem = r.hip_y + 4.0
    robe = rig_robe(r, draw, PHO_BODY, flare=3.0, hem=hem, dark=(250, 222, 150))
    # a gold yoke at the neck, and the hem a band of the spectrum, red on his right round to
    # violet on his left, so the same colours stay on the same side of him as he turns
    if r.back:
        band = PRISM[::-1]
    elif d:
        band = (PRISM[2], PRISM[1], PRISM[0], PRISM[1], PRISM[2]) if d > 0 else (PRISM[2], PRISM[3], PRISM[4], PRISM[3], PRISM[2])
    else:
        band = PRISM
    xs = [p[0] for p in robe.pts]
    x0, x1 = min(xs), max(xs)

    def trims(l):
        cel(l, Ell(r.hx, r.sh_y - 2.6, r.sh_w + 3.0, 3.6), PHO_GOLD, sh=None, lw=0.7)
        for i, col in enumerate(band):
            RRect(x0 + (x1 - x0) * i / 5.0, hem - 2.9, x0 + (x1 - x0) * (i + 1) / 5.0 + 0.2, hem + 1, 0.1).draw(l, fill=col)
        stroke(l, [(x0, hem - 2.9), (x1, hem - 2.9)], 0.6, ink(PHO_GOLD))
    clipped(draw, robe, trims)
    robe.draw(draw, fill=None, outline=ink(PHO_BODY), width=1.0)
    rig_arms(r, draw, PHO_BODY, (255, 250, 232), layer="near", reach=0.3)
    for side in ((-1, 1) if not d else (d,)):
        w = arm_pts(r, side, 0.3)[2]
        cel(draw, Ell(w[0], w[1] - 0.6, 2.5, 1.5), PHO_GOLD, sh=(0.0, 0.5), lw=0.7)
    rig_head(r, draw, (255, 250, 232), hair=PHO_GOLD, style=PHO_HAIR, eye_color=(250, 170, 40), expression="smile", blush=True)
    # the prism floating overhead
    _pho_prism(draw, hx + (0 if not d else -d * 2.0), hy - r.head_ry - 7.4 + [0, -0.4, -0.8, -0.4][frame])
    # rainbow glints: each keeps its colour and its place beside him, and twinkles
    spots = ((-13.4, -4.6), (13.2, -6.6), (11.6, 4.6))
    for i, ((u, v), col) in enumerate(zip(spots, (PRISM[0], PRISM[2], PRISM[3]))):
        big = (i + frame) % 2 == 0
        sparkle(draw, cx + u, r.waist_y + v + (0.5 if big else -0.5), 2.6 if big else 1.8, col)


# ===================================================================
# RAILGUNNER (67) -- red visor, tactical gear, a huge coil rifle
# ===================================================================

RG_SUIT = (72, 92, 84)
RG_ARMOR = (116, 132, 124)
RG_TRIM = (58, 70, 70)
RG_VISOR = (255, 70, 70)
RG_COIL = (90, 210, 255)
RG_GUN = (88, 94, 116)
RG_GUN_DARK = (52, 56, 70)
RG_GLOVE = (60, 60, 70)


def _railgun(draw, x, y, ang, flip=1.0, charge=0):
    body = [(-6.0, -2.0), (6.0, -2.0), (6.0, 2.0), (-6.0, 2.4)]
    cel(draw, Poly(xform(body, x, y, ang, 1.0, flip)), RG_GUN, sh=None)
    stock = [(-10.4, -1.6), (-6.0, -2.0), (-6.0, 2.4), (-9.6, 3.2)]
    cel(draw, Poly(xform(stock, x, y, ang, 1.0, flip)), RG_GUN_DARK, sh=None)
    for rail in (-1.3, 1.3):
        cel(draw, Limb(xform([(5.6, rail), (19.0, rail)], x, y, ang, 1.0, flip), [0.7, 0.7]), (160, 166, 186), sh=None, lw=0.5)
    for k in range(4):
        p = xform([(8.0 + k * 3.0, 0.0)], x, y, ang, 1.0, flip)[0]
        cel(draw, Ell(p[0], p[1], 1.3, 2.3), RG_COIL if (k + charge) % 2 == 0 else shade(RG_COIL, 0.8), sh=None, lw=0.5)
    scope = [(-2.0, -2.0), (3.0, -2.0), (3.0, -4.0), (-2.0, -4.0)]
    cel(draw, Poly(xform(scope, x, y, ang, 1.0, flip)), RG_GUN_DARK, sh=None)
    lens = xform([(3.2, -3.0)], x, y, ang, 1.0, flip)[0]
    Ell(lens[0], lens[1], 0.8, 0.8).draw(draw, fill=RG_VISOR)


def _rg_arm(r, draw, side, hand, near=True):
    """An arm to a hand that is on the rifle, wherever the rifle is."""
    s = r.shoulder(side)
    e = ((s[0] + hand[0]) / 2.0 + (side * 1.0 if not r.d else -r.d * 0.6), (s[1] + hand[1]) / 2.0 + 0.8)
    aw = 2.2 * min(r.build, 1.3)
    cel(draw, Limb([s, e, hand], [aw, aw * 0.9, aw * 0.82]), RG_SUIT if near else shade(RG_SUIT, 0.6), sh=(0.8, 0.4))


def _rg_glove(draw, p, near=True):
    cel(draw, Ell(p[0], p[1], 2.2, 2.2), RG_GLOVE if near else shade(RG_GLOVE, 0.5), sh=(0.6, 0.6))


def _rg_helmet(r, draw):
    """A helmet that comes down over the ears and the back of the head, the red visor a band
    over the eyes, a comms antenna with a light on it."""
    from sprite_base import clipped
    d = r.d
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    ocx, ocy, orx, ory = hx - d * 0.4, hy - 0.8, rx + 1.3, ry + 0.8
    if r.back:
        shell = Poly(arc_pts(ocx, ocy, orx, ory, 150, 390, 34) + [(hx + 7.6, hy + 9.4), (hx - 7.6, hy + 9.4)])
        band = RRect(hx - orx - 1, hy + 0.6, hx + orx + 1, hy + 3.4, 0.2)
        cel(draw, shell, RG_ARMOR, sh=(1.4, 1.0), hi=(0.8, 0.8), regions=[(band, RG_TRIM)])
        # the visor's strap round the back, and its buckle (no light on it: a band with a
        # light in the middle of a head is a face)
        cel(draw, RRect(hx - 2.4, hy - 0.2, hx + 2.4, hy + 4.2, 0.6), (150, 156, 176), sh=(0.5, 0.5))
    elif d:
        dome = arc_pts(0.0, 0.0, orx, ory, 180, 360, 26)
        local = [(u - 0.4, v) for (u, v) in dome] + [(orx - 0.4, 0.6), (-2.4, 0.6), (-2.8, 6.6), (-5.4, 10.2), (-8.6, 10.2),
                                                     (-12.0, 7.4), (-13.8, 3.6)]
        cel(draw, Poly([(hx + d * u, ocy + v) for (u, v) in local]), RG_ARMOR, sh=(1.4, 1.0), hi=(0.8, 0.8))
    else:
        dome = arc_pts(ocx, ocy, orx, ory, 180, 360, 26)
        cel(draw, Poly(dome + [(hx + orx, hy + 3.0), (hx + rx + 0.4, hy + 6.6), (hx + rx - 2.2, hy + 7.4), (hx + rx - 2.6, hy - 1.0),
                               (hx - rx + 2.6, hy - 1.0), (hx - rx + 2.2, hy + 7.4), (hx - rx - 0.4, hy + 6.6), (hx - orx, hy + 3.0)]),
            RG_ARMOR, sh=(1.4, 1.0), hi=(0.8, 0.8))
    if not r.back:
        if d:
            x0, x1 = sorted((hx - d * 6.0, hx + d * (rx + 3.0)))
        else:
            x0, x1 = hx - rx + 0.8, hx + rx - 0.8
        band = RRect(x0, hy - 1.5, x1, hy + 5.1, 1.8)

        def paint(l):
            cel(l, band, RG_VISOR, sh=None, line_color=RG_TRIM, lw=1.0,
                regions=[(RRect(x0, hy + 3.2, x1, hy + 5.4, 0.2), shade(RG_VISOR, 0.9))])
        clipped(draw, Ell(hx, hy, rx + 1.2, ry + 1.2), paint)
        gx = hx - 10.4 if d < 0 else x0 + 1.9      # the glint, at the end the light comes from
        stroke(draw, [(gx, hy + 0.3), (gx + 3.8, hy + 0.3)], 1.0, (255, 206, 200))
    ax = hx - d * 5.0 + (6.0 if not d else 0)
    cel(draw, Limb([(ax, hy - ry + 1.0), (ax + 0.4 - d * 0.4, hy - ry - 4.2)], [0.75, 0.6]), RG_GUN_DARK, sh=None)
    Ell(ax + 0.4 - d * 0.4, hy - ry - 4.8, 1.1, 1.1).draw(draw, fill=RG_VISOR)


def draw_railgunner(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=1.04)
    d = r.d
    cx = r.cx
    if d:
        # the rifle is held level at the hip in both hands and stays there through the stride
        ang, flip = (0.0, 1.0) if d > 0 else (180.0, -1.0)
        gx, gy = cx + d * 1.6, r.hip_y - 4.2
        fore = xform([(9.6, 1.6)], gx, gy, ang, 1.0, flip)[0]
        _rg_arm(r, draw, -d, fore, near=False)
    rig_legs(r, draw, RG_SUIT, (60, 60, 68))
    rig_torso(r, draw, RG_ARMOR)
    if not r.back:
        for k in (-1, 1):
            cel(draw, RRect(cx + k * 3.0 + d - 1.4, r.sh_y + 1.0, cx + k * 3.0 + d + 1.4, r.sh_y + 4.0, 0.5), shade(RG_ARMOR, 0.7), sh=None)
    rig_belt(r, draw, (60, 62, 72), buckle=RG_COIL)
    if r.back:
        # slung across the back, muzzle up past the shoulder
        rig_arms(r, draw, RG_SUIT, RG_GLOVE)
        stroke(draw, [(cx + r.sh_w - 1.0, r.sh_y - 1.6), (cx - r.hip_w + 0.6, r.waist_y + 1.0)], 1.3, RG_TRIM)
        _railgun(draw, cx + 3.4, r.sh_y + 5.6, -150.0, flip=-1.0, charge=frame)
    elif d:
        _rg_glove(draw, fore, near=False)
        _railgun(draw, gx, gy, ang, flip=flip, charge=frame)
        grip = (gx - d * 1.0, gy + 1.6)
        _rg_arm(r, draw, d, grip)
        _rg_glove(draw, grip)
    else:
        # carried across the body, low: stock at his right hip, the coils up at the other shoulder
        gx, gy, ang = cx + 5.2, r.hip_y - 3.2, -156.0
        grip = xform([(-2.4, -1.2)], gx, gy, ang, 1.0, -1.0)[0]
        fore = xform([(6.4, -1.8)], gx, gy, ang, 1.0, -1.0)[0]
        _rg_arm(r, draw, 1, grip)
        _rg_arm(r, draw, -1, fore)
        _railgun(draw, gx, gy, ang, flip=-1.0, charge=frame)
        _rg_glove(draw, grip)
        _rg_glove(draw, fore)
    head_skull(r, draw, SKIN, ears=False)
    if not r.back:
        # no eyes: the visor is over them. Cheeks and a set mouth under it.
        e1, e2, ey, mx, my = face_anchor(r)
        for bx in ((r.hx - 7.4, r.hx + 7.4) if not d else (e1 - d * 1.6,)):
            Ell(bx, ey + 3.6, 1.9, 0.85).draw(draw, fill=mix(SKIN, (255, 128, 128), 0.32))
        ms_mouth(draw, mx, my, "set", SKIN, 1.0 if not d else 0.8)
    _rg_helmet(r, draw)


# ===================================================================
# BOMBARDIER (68) -- goggles, a bandolier of grenades, a lit bomb
# ===================================================================

BOMB_JACKET = (150, 110, 70)
BOMB_OLIVE = (112, 124, 74)
BOMB_BLACK = (52, 50, 62)
BOMB_HAIR = (214, 122, 52)
BOMB_CAP = (74, 68, 84)         # sooty black, like his bombs: the Pilot's cap is the brown leather one
BOMB_SACK = (190, 156, 104)
BOMB_GLOVE = (80, 70, 60)


def _bomb(draw, x, y, rad, frame, lean=1, lit_fuse=True):
    cel(draw, Ell(x, y, rad, rad), BOMB_BLACK, sh=(0.9, 0.9), hi=(0.5, 0.5))
    Ell(x - rad * 0.35, y - rad * 0.35, rad * 0.26, rad * 0.22).draw(draw, fill=(160, 160, 182))
    cel(draw, RRect(x - 1.3, y - rad - 1.4, x + 1.3, y - rad + 0.4, 0.4), (150, 150, 160), sh=None)
    fuse = [(x, y - rad - 1.2), (x + lean * 1.4, y - rad - 3.0), (x + lean * 2.6, y - rad - 3.4)]
    stroke(draw, fuse, 0.8, (230, 210, 160))
    if lit_fuse:
        big = frame % 2 == 0
        sparkle(draw, fuse[-1][0] + lean * 0.6, fuse[-1][1] - 0.4, 3.0 if big else 2.2, (255, 170, 60), core=(255, 250, 220))
        Ell(fuse[-1][0] + lean * 0.6, fuse[-1][1] - 0.4, 1.0 if big else 0.8, 1.0 if big else 0.8).draw(draw, fill=(255, 250, 220))


def _bomb_arm(r, draw, side, hand):
    """The arm that holds the bomb up, to a hand under it."""
    s = r.shoulder(side)
    e = ((s[0] + hand[0]) / 2.0 + (side * 1.4 if not r.d else -r.d * 0.6), (s[1] + hand[1]) / 2.0 + 1.6)
    aw = 2.2 * min(r.build, 1.3)
    cel(draw, Limb([s, e, hand], [aw, aw * 0.9, aw * 0.82]), BOMB_JACKET, sh=(0.8, 0.4))


def _bomb_satchel(r, draw):
    """What he has behind him: a canvas satchel of bombs, two of them showing over the flap."""
    cx, d = r.cx, r.d
    top = r.sh_y + (1.0 if d else 2.6)
    if d:
        x0, x1 = sorted((cx - d * 3.4, cx - d * 8.4))
        _bomb(draw, cx - d * 5.8, top + 0.2, 2.5, 0, lean=-d, lit_fuse=False)
        cel(draw, RRect(x0, top, x1, top + 9.0, 1.6), BOMB_SACK, sh=(0.8, 0.8))
        stroke(draw, [(x0 + 0.6, top + 3.2), (x1 - 0.6, top + 3.2)], 0.7, shade(BOMB_SACK, 1.6))
    else:
        for k in (-1, 1):
            _bomb(draw, cx + k * 2.8, top + 0.2, 2.6, 0, lean=k, lit_fuse=False)
        cel(draw, RRect(cx - 6.0, top, cx + 6.0, top + 8.4, 1.8), BOMB_SACK, sh=(1.0, 1.0))
        stroke(draw, [(cx - 5.4, top + 3.4), (cx + 5.4, top + 3.4)], 0.7, shade(BOMB_SACK, 1.6))
        cel(draw, RRect(cx - 1.3, top + 2.4, cx + 1.3, top + 5.2, 0.5), (200, 200, 210), sh=(0.4, 0.4))


def _bomb_cap(r, draw):
    """A canvas cap down over the back of his head, ear flaps, goggles on the brow; his hair
    stands out all round it and sticks out under it behind."""
    d = r.d
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    ocx, ocy, orx, ory = hx - d * 0.4, hy - 0.8, rx + 1.2, ry + 0.8
    if r.back:
        # a shaggy fringe under the cap behind, solid down past the skull so no neck shows
        # between its points (the kit's own hem there reads as a row of teeth)
        tufts = []
        for i in range(5):
            x = hx - 8.4 + i * 4.2
            tufts += [(x - 2.1, hy + 10.6), (x + (i - 2) * 0.6, hy + 13.4 - abs(i - 2) * 0.5), (x + 2.1, hy + 10.6)]
        cel(draw, Poly([(hx - 11.6, hy + 4.0), (hx - 12.6, hy + 9.6)] + tufts + [(hx + 12.6, hy + 9.6), (hx + 11.6, hy + 4.0)]),
            BOMB_HAIR, sh=(0.8, 0.8))
        cel(draw, Poly(arc_pts(ocx, ocy, orx, ory, 150, 390, 34) + [(hx + 7.4, hy + 9.4), (hx - 7.4, hy + 9.4)]), BOMB_CAP,
            sh=(1.4, 1.0), hi=(0.6, 0.6))
    elif d:
        tuft = [(-4.0, 6.0), (-5.0, 12.6), (-7.2, 9.6), (-9.6, 13.0), (-10.8, 8.8), (-14.4, 10.6), (-13.0, 4.0)]
        cel(draw, Poly([(hx + d * u, hy + v) for (u, v) in tuft]), BOMB_HAIR, sh=(0.6, 0.8))
        dome = arc_pts(0.0, 0.0, orx, ory, 180, 360, 26)
        local = [(u - 0.4, v) for (u, v) in dome] + [(orx - 0.4, -1.2), (-0.6, -1.2), (-1.0, 6.0), (-4.2, 9.6), (-8.4, 9.8),
                                                     (-11.9, 7.4), (-13.7, 3.6)]
        cel(draw, Poly([(hx + d * u, ocy + v) for (u, v) in local]), BOMB_CAP, sh=(1.4, 1.0), hi=(0.6, 0.6))
    else:
        dome = arc_pts(ocx, ocy, orx, ory, 182, 358, 26)
        cel(draw, Poly(dome + [(hx + rx + 1.3, hy - 2.0), (hx - rx - 1.3, hy - 2.0)]), BOMB_CAP, sh=(1.4, 1.0), hi=(0.6, 0.6))
    for s in ((-1, 1) if not d else (-d,)):
        fx = hx + s * (rx + 0.4) if not d else hx - d * 3.8
        cel(draw, RRect(fx - 2.0, hy - 3.0, fx + 2.0, hy + 4.4, 1.6), BOMB_CAP, sh=(0.5, 0.5))
    band = (168, 122, 74)       # the goggles' leather strap, right round the cap
    if d:
        stroke(draw, [(hx - d * (rx + 0.2), hy - 3.4), (hx + d * (rx + 0.6), hy - 4.4)], 1.3, band)
    else:
        stroke(draw, [(hx - rx - 0.6, hy - 4.0), (hx + rx + 0.6, hy - 4.0)], 1.3, band)
    if r.back:
        return
    for k in ((-3.2, 3.2) if not d else (d * 4.4,)):
        gx = hx + k + d * 1.4
        cel(draw, Ell(gx, hy - 4.4, 2.8, 2.4), (206, 196, 176), sh=None)
        cel(draw, Ell(gx, hy - 4.4, 1.8, 1.5), (240, 160, 60), sh=None, line=False)
        Ell(gx - 0.6, hy - 4.9, 0.6, 0.6).draw(draw, fill=(255, 244, 214))


def draw_bombardier(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=1.1)
    d = r.d
    hs = _hold_side(r)
    cx = r.cx
    # the bomb is held up in his open hand, steady, while the other arm swings
    if d:
        palm = (cx + d * 8.4, r.sh_y + 5.6)
        bomb = (palm[0] + d * 1.6, palm[1] - 4.2)
    else:
        palm = (cx + hs * (r.sh_w + 5.2), r.sh_y + 2.4)
        bomb = (palm[0] + hs * 0.8, palm[1] - 4.4)
    if d:
        _bomb_satchel(r, draw)
        rig_arms(r, draw, BOMB_JACKET, BOMB_GLOVE, layer="far")
    rig_legs(r, draw, BOMB_OLIVE, (80, 60, 46))
    rig_torso(r, draw, BOMB_JACKET)
    # bandolier of grenades
    if d:
        s0, s1 = (cx - d * 3.6, r.sh_y - 1.4), (cx + d * 4.0, r.waist_y + 0.8)
    else:
        k = -1 if r.back else 1
        s0, s1 = (cx - k * (r.sh_w - 1.0), r.sh_y - 1.4), (cx + k * (r.hip_w - 0.6), r.waist_y + 0.6)
    stroke(draw, [s0, s1], 1.7, (90, 70, 50))
    if not r.back:
        for t in ((0.2, 0.47, 0.74) if not d else (0.3, 0.68)):
            gx, gy = s0[0] + (s1[0] - s0[0]) * t, s0[1] + (s1[1] - s0[1]) * t
            cel(draw, Ell(gx, gy, 1.8, 2.1), BOMB_OLIVE, sh=(0.5, 0.5), lw=0.6)
            stroke(draw, [(gx - 0.8, gy - 1.9), (gx + 0.8, gy - 1.9)], 0.7, (196, 196, 206))
    rig_belt(r, draw, (90, 70, 50), buckle=(200, 200, 210))
    if r.back:
        _bomb_satchel(r, draw)
        rig_arms(r, draw, BOMB_JACKET, BOMB_GLOVE)
    else:
        if not d:
            rig_arms(r, draw, BOMB_JACKET, BOMB_GLOVE, sides=(-hs,))
        _bomb_arm(r, draw, hs, palm)
    head_skull(r, draw, SKIN)
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    if not r.back:
        head_face(r, draw, SKIN, iris=(120, 90, 60), mood="bright", mouth="grin", brows=True, brow_color=(90, 60, 40))
        # soot smudges
        for (u, v) in ((-6.4, 5.4), (5.6, 6.6)) if not d else ((d * 5.0, 6.6),):
            blob(draw, [Ell(hx + u, hy + v, 1.6, 0.9), Ell(hx + u + 0.8, hy + v + 0.5, 0.9, 0.6)], (150, 120, 110), sh=None, line=False)
    rig_hair(r, draw, BOMB_HAIR, "wild", hat=True, skin=SKIN)
    _bomb_cap(r, draw)
    if not r.back:
        _bomb(draw, bomb[0], bomb[1], 4.0, frame, lean=hs)
        cel(draw, Ell(palm[0], palm[1], 2.4, 2.2), BOMB_GLOVE, sh=(0.6, 0.6))


# ===================================================================
# SENTINEL (69) -- bulky armour, a T-visor, a hex energy shield
# ===================================================================

SEN_ARMOR = (60, 82, 142)
SEN_STEEL = (196, 206, 226)
SEN_GLOW = (100, 210, 255)
SEN_DARK = (26, 32, 56)
SEN_PANE = (132, 214, 255)


def _hex_shield(draw, x, y, rx, ry, frame):
    """The energy shield, point up: a pane of light with a paler heart and a hard bright rim.
    Seen edge-on (walking) it is the same thing with a small rx."""
    def hexagon(k):
        return Poly([(x + rx * k * math.cos(math.radians(a)), y + ry * k * math.sin(math.radians(a)))
                     for a in range(30, 390, 60)])
    layer = draw.sub()
    cel(layer, hexagon(1.0), SEN_PANE, sh=None, line_color=(44, 150, 226), lw=1.1)
    hexagon(0.6).draw(layer, fill=(202, 242, 255))
    # light, not plate: a little of what is behind it shows through
    layer._img.putalpha(layer._img.getchannel("A").point(lambda v: v * 236 // 255))
    draw.merge(layer)
    if frame % 2 == 0 and rx > 5:
        sparkle(draw, x - rx * 0.36, y - ry * 0.46, 1.9, (255, 255, 255))


def _sen_arm(r, draw, side, hand):
    """The shield arm, to a hand held steady where the shield is, whatever the stride."""
    s = r.shoulder(side)
    e = ((s[0] + hand[0]) / 2.0 + (side * 0.7 if not r.d else -r.d * 0.3), (s[1] + hand[1]) / 2.0 + 0.6)
    aw = 2.2 * min(r.build, 1.3)
    cel(draw, Limb([s, e, (hand[0], hand[1] - 1.0)], [aw, aw * 0.9, aw * 0.82]), SEN_ARMOR, sh=(0.8, 0.4))


def _sen_helm(r, draw):
    """A steel helm that is plainly a helm: a blue crest fin from brow to nape, a blue jaw
    and neck guard, and a wide dark T with the eyes alight in it."""
    from sprite_base import clipped
    d = r.d
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    sx, srx, sry = hx - d * 0.4, rx + 0.8, ry + 0.8
    shell = Ell(sx, hy, srx, sry)
    if d:
        # the fin in profile: a blade behind the dome, rising from the brow to its height over
        # the back of the crown and cut off short above the nape
        outer, inner = [], []
        for a, k in ((326, 0.0), (312, 1.5), (296, 3.2), (278, 4.7), (260, 5.7), (244, 5.9), (230, 5.2), (218, 3.6), (211, 0.0)):
            a = math.radians(a if d > 0 else 180 - a)
            outer.append((sx + (srx + k) * math.cos(a), hy + (sry + k) * math.sin(a)))
            inner.append((sx + (srx - 1.0) * math.cos(a), hy + (sry - 1.0) * math.sin(a)))
        cel(draw, Poly(outer + inner[::-1]), SEN_ARMOR, sh=(0.7, 0.0), hi=(0.0, 0.8))
    jaw = Poly([(sx - 20, hy + 4.8), (sx + 20, hy + 4.8), (sx + 20, hy + 20), (sx - 20, hy + 20)])
    jaw_dark = Poly(arc_pts(sx, hy, srx + 1, sry + 1, 22, 120, 16) + arc_pts(sx - 1.8, hy - 1.4, srx, sry, 120, 31, 16))
    cel(draw, shell, SEN_STEEL, sh=(1.6, 1.2), hi=(0.9, 0.9),
        regions=[(jaw, SEN_ARMOR), (jaw_dark, shade(SEN_ARMOR))])
    stroke(draw, [(sx - srx * 0.72, hy + 4.8), (sx + srx * 0.72, hy + 4.8)], 0.7, ink(SEN_ARMOR))
    if not d:
        # the fin head-on and from behind: edge-on, a blade over the crown (and down the back)
        low = hy + 1.0 if r.back else hy - 3.4
        cel(draw, Poly([(hx - 1.8, low), (hx + 1.8, low), (hx + 2.9, hy - ry - 4.0), (hx + 1.9, hy - ry - 5.0),
                        (hx - 1.9, hy - ry - 5.0), (hx - 2.9, hy - ry - 4.0)]), SEN_ARMOR, sh=(0.8, 0.0), hi=(0.7, 0.0))
    if r.back:
        return

    def visor(l):
        if d:
            x0, x1 = sorted((hx - d * 3.4, hx + d * (rx + 3.0)))
            RRect(x0, hy - 1.3, x1, hy + 3.5, 1.4).draw(l, fill=SEN_DARK)
            RRect(hx + d * 5.8 - 1.6, hy + 2.6, hx + d * 5.8 + 1.6, hy + 9.0, 0.8).draw(l, fill=SEN_DARK)
        else:
            RRect(hx - rx + 1.2, hy - 1.3, hx + rx - 1.2, hy + 3.5, 1.4).draw(l, fill=SEN_DARK)
            RRect(hx - 1.9, hy + 2.6, hx + 1.9, hy + 9.0, 0.8).draw(l, fill=SEN_DARK)
    clipped(draw, shell, visor)
    for ex, w in (((hx - 4.7, 2.0), (hx + 4.7, 2.0)) if not d else ((hx + d * 1.6, 2.0), (hx + d * 9.2, 1.3))):
        RRect(ex - w, hy - 0.4, ex + w, hy + 2.6, 1.0).draw(draw, fill=SEN_GLOW)
        Ell(ex - w * 0.3, hy + 0.5, 0.65, 0.65).draw(draw, fill=(255, 255, 255))


def draw_sentinel(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=1.24)
    d = r.d
    cx = r.cx
    shield_side = (-1 if not r.back else 1) if not d else d
    free = -shield_side
    # the shield hand is held steady in front of him; only the other arm swings
    if d:
        grip = (cx + d * 4.8, r.hip_y - 4.6)
        pane = (cx + d * 9.8, r.hip_y - 5.6, 4.7, 9.6)
    else:
        grip = (cx + shield_side * (r.sh_w + 2.6), r.hip_y - 2.8)
        pane = (grip[0] + shield_side * 2.2, grip[1] - 2.6, 7.6, 8.8)
    if r.back:
        # held in front of him, so from behind his own arm and side are in its way
        _hex_shield(draw, *pane, frame=frame)
    if d:
        rig_arms(r, draw, SEN_ARMOR, SEN_STEEL, layer="far", hand_r=2.5)
    rig_legs(r, draw, SEN_ARMOR, SEN_STEEL, width=1.15)
    rig_torso(r, draw, SEN_ARMOR)
    if not r.back:
        px = cx + d * 1.6
        cel(draw, RRect(px - 5.0, r.sh_y - 0.8, px + 5.0, r.sh_y + 5.6, 1.4), SEN_STEEL, sh=(0.8, 0.8), hi=(0.5, 0.5))
        cel(draw, Poly([(px, r.sh_y + 0.2), (px + 2.9, r.sh_y + 2.4), (px, r.sh_y + 4.6), (px - 2.9, r.sh_y + 2.4)]), SEN_GLOW,
            sh=None, lw=0.6)
        Ell(px - 0.5, r.sh_y + 1.9, 0.7, 0.7).draw(draw, fill=(255, 255, 255))
    else:
        # the power pack between the shoulders: what he has behind him
        cel(draw, RRect(cx - 5.2, r.sh_y - 0.6, cx + 5.2, r.sh_y + 6.6, 1.4), SEN_STEEL, sh=(0.8, 0.8), hi=(0.5, 0.5))
        core = [(cx + 2.9 * math.cos(math.radians(a)), r.sh_y + 3.0 + 2.9 * math.sin(math.radians(a))) for a in range(30, 390, 60)]
        cel(draw, Poly(core), SEN_GLOW if frame % 2 == 0 else lit(SEN_GLOW, 0.8), sh=None, line_color=ink(SEN_ARMOR), lw=0.7)
    rig_belt(r, draw, shade(SEN_ARMOR, 1.6), buckle=SEN_GLOW)
    if not d:
        rig_arms(r, draw, SEN_ARMOR, SEN_STEEL, sides=(free,), hand_r=2.5)
    _sen_arm(r, draw, shield_side, grip)
    for side in _sides(r):
        sx, sy = r.shoulder(side)
        px = sx + (side * 1.8 if not d else -d * 0.4)
        cel(draw, Ell(px, sy - 0.9, 5.3 if not d else 4.8, 4.0), SEN_STEEL, sh=(1.0, 0.9), hi=(0.6, 0.6),
            regions=[(RRect(px - 6, sy + 0.5, px + 6, sy + 4, 0.2), SEN_ARMOR)])
    _sen_helm(r, draw)
    cel(draw, Ell(grip[0], grip[1], 2.5, 2.5), SEN_STEEL, sh=(0.6, 0.6))
    if not r.back:
        _hex_shield(draw, *pane, frame=frame)


# ===================================================================
# PILOT (70) -- leather flying cap and goggles, a flowing white scarf
# ===================================================================

PIL_JACKET = (156, 104, 62)
PIL_FUR = (238, 214, 168)
PIL_SCARF = (250, 250, 252)
PIL_SCARF_SH = (198, 212, 236)


def _pil_tail(draw, pts, widths, lit_side=True):
    """One tail of the scarf: a ribbon wide enough to be white cloth and not its own ink
    line, ending in a swallow's fork."""
    from sprite_base import limb_pts
    edge = limb_pts(pts, widths, cap=False)
    n = len(pts)
    (x1, y1), (x0, y0) = pts[-1], pts[-2]
    ln = math.hypot(x1 - x0, y1 - y0) or 1.0
    notch = (x1 - (x1 - x0) / ln * 2.4, y1 - (y1 - y0) / ln * 2.4)
    shape = Poly(edge[:n] + [notch] + edge[n:])
    cel(draw, shape, PIL_SCARF if lit_side else PIL_SCARF_SH, sh=(0.0, 1.0), tone=PIL_SCARF_SH if lit_side else shade(PIL_SCARF_SH, 0.6))


def _pil_cap(r, draw):
    """The leather flying helmet: over the ears and down the back of the head to the collar,
    an ear flap tipped with fleece each side, a seam over the crown."""
    d = r.d
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    ocx, ocy, orx, ory = hx - d * 0.4, hy - 0.8, rx + 1.2, ry + 0.8
    seam = shade(PIL_JACKET, 1.6)
    if r.back:
        cel(draw, Poly(arc_pts(ocx, ocy, orx, ory, 150, 390, 34) + [(hx + 7.4, hy + 9.6), (hx - 7.4, hy + 9.6)]), PIL_JACKET,
            sh=(1.4, 1.0), hi=(0.6, 0.6))
        stroke(draw, [(hx, hy - ry - 0.2), (hx, hy + 8.6)], 0.7, seam)
    elif d:
        dome = arc_pts(0.0, 0.0, orx, ory, 180, 360, 26)
        local = [(u - 0.4, v) for (u, v) in dome] + [(orx - 0.4, -0.8), (-0.6, -0.8), (-1.0, 6.2), (-4.2, 10.0), (-8.4, 10.2),
                                                     (-11.9, 7.6), (-13.7, 3.8)]
        cel(draw, Poly([(hx + d * u, ocy + v) for (u, v) in local]), PIL_JACKET, sh=(1.4, 1.0), hi=(0.6, 0.6))
        stroke(draw, [(hx - d * 0.4, hy - ry - 0.4), (hx - d * 3.4, hy - 2.0)], 0.7, seam)
    else:
        dome = arc_pts(ocx, ocy, orx, ory, 182, 358, 26)
        cel(draw, Poly(dome + [(hx + rx + 1.3, hy - 1.6), (hx - rx - 1.3, hy - 1.6)]), PIL_JACKET, sh=(1.4, 1.0), hi=(0.6, 0.6))
        stroke(draw, [(hx, hy - ry - 0.4), (hx, hy - 2.0)], 0.7, seam)
    for s2 in ((-1, 1) if not d else (-d,)):
        fx = hx + s2 * (rx + 0.2) if not d else hx - d * 3.8
        cel(draw, RRect(fx - 2.3, hy - 3.0, fx + 2.3, hy + 5.8, 1.8), PIL_JACKET, sh=(0.5, 0.5))
        cel(draw, Ell(fx, hy + 6.2, 2.3, 1.5), PIL_FUR, sh=(0.0, 0.5), lw=0.6)
    # the goggles on the brow, their strap right round the cap
    band = (70, 60, 56)
    if d:
        stroke(draw, [(hx - d * (rx + 0.2), hy - 3.6), (hx + d * (rx + 0.6), hy - 4.6)], 1.1, band)
    else:
        stroke(draw, [(hx - rx - 0.6, hy - 4.2), (hx + rx + 0.6, hy - 4.2)], 1.1, band)
    if r.back:
        return
    for k in ((-3.2, 3.2) if not d else (d * 4.4,)):
        gx = hx + k + d * 1.4
        cel(draw, Ell(gx, hy - 4.6, 2.8, 2.4), (214, 200, 150), sh=None)
        cel(draw, Ell(gx, hy - 4.6, 1.8, 1.5), (140, 210, 240), sh=None, line=False)
        Ell(gx - 0.6, hy - 5.1, 0.6, 0.6).draw(draw, fill=(255, 255, 255))


def draw_pilot(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame)
    d = r.d
    cx = r.cx
    wave = [0.0, 1.0, 0.0, -1.0][frame]
    y0 = r.sh_y - 1.8
    # the scarf: two long tails streaming out behind him (to the side head-on; down his back
    # when he walks away), flapping with the stride
    if not r.back:
        s = -1 if not d else -d
        bx = cx + s * (4.2 if not d else 2.6)
        _pil_tail(draw, [(bx, y0 + 2.0), (bx + s * 4.6, y0 + 4.2 - wave * 0.9), (bx + s * 9.4, y0 + 3.6 + wave * 0.9)],
                  [1.9, 2.2, 2.0], lit_side=False)
        _pil_tail(draw, [(bx, y0 + 0.2), (bx + s * 5.0, y0 - 1.6 + wave), (bx + s * 10.4, y0 + 0.8 - wave),
                         (bx + s * 15.6, y0 - 1.2 + wave * 0.6)], [2.2, 2.7, 2.7, 2.4])
    if d:
        rig_arms(r, draw, PIL_JACKET, (110, 80, 60), layer="far")
    rig_legs(r, draw, (130, 120, 96), (80, 60, 46))
    rig_torso(r, draw, PIL_JACKET)
    if not r.back:
        stroke(draw, [(cx + d * 1.6, r.sh_y - 1.0), (cx + d * 1.6, r.hip_y)], 0.6, shade(PIL_JACKET, 1.4))
        # gold wings badge, on his chest whichever way he faces
        wx = cx - 3.4 if not d else cx + d * 2.6
        for k in ((-1, 1) if not d else (-d,)):
            Poly([(wx, r.sh_y + 2.6), (wx + k * 2.8, r.sh_y + 1.6), (wx + k * 2.2, r.sh_y + 3.2)]).draw(draw, fill=GOLD)
        Ell(wx, r.sh_y + 2.6, 0.8, 0.8).draw(draw, fill=GOLD)
    rig_belt(r, draw, (90, 64, 44), buckle=GOLD)
    rig_arms(r, draw, PIL_JACKET, (110, 80, 60), layer="near")
    # the jacket's fleece collar, a ruff on the shoulders, and the scarf wound at the throat
    blob(draw, [Ell(cx - 5.2, r.sh_y - 1.0, 4.6, 2.7), Ell(cx + 5.2, r.sh_y - 1.0, 4.6, 2.7), Ell(cx, r.sh_y - 0.4, 3.0, 2.2)] if not d else
         [Ell(cx - d * 1.2, r.sh_y - 1.0, 5.4, 2.6)], PIL_FUR, sh=(0.5, 0.8), tone=shade(PIL_FUR, 0.9))
    cel(draw, RRect(cx - 5.0, y0 - 1.4, cx + 5.0, y0 + 2.0, 1.5) if not d else RRect(cx - 3.8, y0 - 1.4, cx + 4.2, y0 + 2.0, 1.5), PIL_SCARF,
        sh=(0.0, 0.8), tone=PIL_SCARF_SH)
    head_skull(r, draw, SKIN)
    if not r.back:
        head_face(r, draw, SKIN, iris=(90, 130, 180), mood="bright", mouth="smile", brows=True, brow_color=(110, 70, 40))
        rig_hair(r, draw, (170, 104, 56), "short", hat=True, skin=SKIN)
    _pil_cap(r, draw)
    if r.back:
        _pil_tail(draw, [(cx + 1.6, y0 + 1.0), (cx + 3.8 + wave * 0.8, y0 + 6.0), (cx + 3.2 - wave * 0.8, y0 + 11.0)],
                  [1.9, 2.2, 2.0], lit_side=False)
        _pil_tail(draw, [(cx - 1.2, y0 + 1.0), (cx - 3.2 + wave * 0.9, y0 + 7.0), (cx - 2.0 - wave * 0.9, y0 + 13.6)], [2.2, 2.6, 2.3])
        cel(draw, Ell(cx, y0 + 0.8, 2.6, 2.0), PIL_SCARF, sh=(0.0, 0.6), tone=PIL_SCARF_SH)


# ===================================================================
# GLITCHER (71) -- chromatic ghosting, a missing chunk, an X for an eye
# ===================================================================

GLI_HOOD = (138, 92, 222)
GLI_CYAN = (70, 240, 255)
GLI_MAG = (255, 60, 200)
GLI_HAIR = (60, 50, 90)
GLI_EYE = (120, 60, 200)
# How far the two colour ghosts stand off him each frame, and how much one rides above the other:
# never nothing (they used to blink off on both strides).
_GLI_GHOST = ((2.6, 0.0), (1.9, 0.5), (3.0, -0.4), (2.1, -0.5))


def _gli_x(draw, x, y, k, w, ghost=1.0):
    """His mark: a magenta X with its cyan ghost a little to the left."""
    for col, dx in ((GLI_CYAN, -ghost), (GLI_MAG, 0.0)):
        stroke(draw, [(x + dx - k, y - k), (x + dx + k, y + k)], w, col)
        stroke(draw, [(x + dx + k, y - k), (x + dx - k, y + k)], w, col)


def _gli_tear(draw, y0, y1, dx):
    """Tear a strip of the picture loose and slide it sideways: everything drawn so far between
    y0 and y1 moves over by dx, which leaves a notch in one side of him and a step out of the other."""
    img, s = draw._img, draw._s
    top, bot = int(round(y0 * s)), int(round(y1 * s))
    strip = img.crop((0, top, img.size[0], bot))
    img.paste((0, 0, 0, 0), (0, top, img.size[0], bot))
    img.paste(strip, (int(round(dx * s)), top))


def draw_glitcher(draw, ox, oy, direction, frame):
    from sprite_base import clipped, hood_shape, torso_shape
    r = rig(ox, oy, direction, frame, build=0.96)
    d = r.d
    cx = r.cx
    gdx, gdy = _GLI_GHOST[frame]

    def body(dr, rr, tint):
        rig_legs(rr, dr, tint, tint)
        rig_torso(rr, dr, tint)
        rig_arms(rr, dr, tint, tint, layer="near")
        cel(dr, hood_shape(rr, drape=True), tint, sh=None)

    # the colour-separated ghosts, offset to either side
    for tint, (dx, dy) in ((GLI_CYAN, (-gdx, gdy)), (GLI_MAG, (gdx, -gdy))):
        layer = draw.sub()
        body(layer, rig(ox + dx, oy + dy, direction, frame, build=0.96), tint)
        layer._img.putalpha(layer._img.getchannel("A").point(lambda v: v * 150 // 255))
        draw.merge(layer)
    if d:
        rig_arms(r, draw, GLI_HOOD, SKIN, layer="far")
    rig_legs(r, draw, (70, 60, 110), (240, 240, 250))
    rig_torso(r, draw, GLI_HOOD)
    # the print on his hoodie: two bars of a broken signal, on the chest and on the back
    shell = torso_shape(r)
    x0 = cx - 3.4 + d * 1.4
    for k, (w, col) in enumerate(((6.8, GLI_CYAN), (4.0, GLI_MAG))):
        y = r.sh_y + 3.3 + k * 2.7
        clipped(draw, shell, lambda l: RRect(x0 + k * 1.2, y, x0 + k * 1.2 + w, y + 1.5, 0.2).draw(l, fill=col))
    rig_arms(r, draw, GLI_HOOD, SKIN, layer="near")
    head_skull(r, draw, SKIN)
    mark = None
    if not r.back:
        # the face by hand: his left eye is the X, and stays his left as he turns (head-on the
        # camera's right, the near eye walking left, the far one walking right). The X is a fault
        # in the picture, not a thing on his face, so it goes on over his hair.
        e1, e2, ey, mx, my = face_anchor(r)
        look = r.eye_look()
        if d > 0:
            # the eye that is left is the near one, half under his fringe: a little forward of the
            # kit's place for it, so that more than a sliver shows
            ms_eye(draw, e1 + 1.1, ey, 4.2, 5.6, GLI_EYE, look, "bright", None, SKIN, -d)
            mark = (e2 - 0.2, ey + 0.2, 1.6, 1.0, 0.7)
        elif d:
            ms_eye(draw, e2, ey, 3.0, 5.2, GLI_EYE, look, "bright", None, SKIN, d)
            mark = (e1, ey + 0.2, 2.1, 1.2, 1.0)
        else:
            ms_eye(draw, e1, ey, 4.2, 5.6, GLI_EYE, look, "bright", None, SKIN, -1)
            mark = (e2, ey + 0.2, 2.1, 1.2, 1.0)
        ms_mouth(draw, mx, my, "smirk", SKIN, 0.8 if d else 1.0)
    rig_hair(r, draw, GLI_HAIR, "short", skin=SKIN)
    if mark:
        _gli_x(draw, *mark)
    rig_hood(r, draw, GLI_HOOD, opening=1.1)
    if r.back:
        # from behind the hood carries his mark
        _gli_x(draw, r.hx, r.head_cy - 0.6, 4.0, 1.7)
    # the glitch: strips of him torn loose and slid sideways, different strips every frame (one
    # at rest, two on the move; never across his eyes, and not across his legs, which torn are a
    # smudge); the first of each frame trails a block of the colour it tore toward, set against
    # the end of the strip (`reach` is how wide he is there), not loose beside him
    hy = r.head_cy
    tears = (((hy - 10.0, hy - 7.2, 2.4, 10.7),),
             ((r.sh_y + 1.4, r.sh_y + 4.4, -2.6, 8.2), (hy - 12.4, hy - 10.2, 1.8, 7.0)),
             ((hy - 8.4, hy - 5.8, -2.4, 11.8), (r.waist_y + 0.2, r.waist_y + 3.0, 2.4, 8.0)),
             ((r.waist_y - 1.6, r.waist_y + 1.2, 2.4, 8.0), (hy - 11.0, hy - 8.6, -2.0, 9.6)))[frame]
    for i, (y0, y1, dx, reach) in enumerate(tears):
        _gli_tear(draw, y0, y1, dx)
        if i == 0:
            k = 1 if dx > 0 else -1
            bx = r.hx + k * (reach + abs(dx) - 0.4)
            cel(draw, RRect(min(bx, bx + k * 3.8), y0, max(bx, bx + k * 3.8), y1, 0.1), GLI_MAG if k > 0 else GLI_CYAN, sh=None, line=False)


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
