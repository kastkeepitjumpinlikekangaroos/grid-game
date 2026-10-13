#!/usr/bin/env python3
"""Elemental character sprite generators (IDs 12-26).

Fifteen elementalists, in MapleStory's style. The humans are on the shared rig
and are told apart by their silhouettes -- flame hair, an ice crown, a storm
cloud on the shoulder, a turban, a flower crown, cloud hair and a cloud to
stand on -- while the elementals themselves (Inferno, Ember, Glacier,
Avalanche, Frostbite, Mudslinger) are creatures with bodies of their own.
"""

import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from sprite_base import (
    DOWN, UP, LEFT, RIGHT, FIRE, ICE, GOLD, LEATHER, STEEL, WOOD, SKIN, SKIN_TAN, HAIR_STYLES,
    Ell, Limb, Poly, RRect, arc_pts, blade, blob, bolt, cel, cloud, crystal, flame, flame_pts,
    generate_character, hand_at, head_face, head_skull, ink, leaf, lit, mix, ms_eye, ms_mouth,
    rig, rig_arms, rig_belt, rig_cape, rig_hair, rig_hand, rig_head, rig_hood, rig_legs, rig_robe,
    rig_torso, shade, sparkle, star, stroke, xform, arm_pts, face_anchor, bez, creature_setup, clipped, rot_pts,
)


def _sides(r):
    return (-1, 1) if not r.d else (r.d,)


def _hold_side(r):
    """The hand that carries a staff or weapon: screen right head-on, screen
    left from behind, the leading hand in profile."""
    return (1 if not r.back else -1) if not r.d else r.d


def _staff(draw, r, side, out, top_y, color=WOOD, width=1.1):
    h = hand_at(r, side, 0.0, 0.0, out)
    x = h[0] + 0.2
    cel(draw, Limb([(x, r.base_y - 1.2), (x, top_y)], [width, width * 0.92]), color, sh=(0.5, 0.0))
    return x


# ===================================================================
# PYROMANCER (12) -- hair that is a flame, a fireball in hand
# ===================================================================

PYRO_ROBE = (198, 44, 50)
PYRO_HAIR = (232, 70, 40)
PYRO_TRIM = (255, 176, 56)


def draw_pyromancer(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, head=0.96)
    d = r.d
    sway = [0.0, 1.0, 0.0, -1.0][frame]
    fire_side = _hold_side(r)
    if d:
        rig_arms(r, draw, PYRO_ROBE, SKIN, layer="far", cuff=PYRO_TRIM)
    rig_legs(r, draw, shade(PYRO_ROBE, 1.4), (120, 56, 40))
    rig_robe(r, draw, PYRO_ROBE, trim=PYRO_TRIM, flare=3.2)
    # flames licking up from the hem
    xs = (-6.4, -2.2, 2.2, 6.4) if not d else (-d * 4.6, -d * 0.6, d * 3.4)
    for i, x in enumerate(xs):
        flame(draw, r.cx + x + r.ph * 0.6, r.base_y - 2.6, 3.2, 4.0 + (i % 2) * 1.4, sway=sway * 0.6)
    rig_belt(r, draw, (120, 34, 40), buckle=PYRO_TRIM)
    rig_arms(r, draw, PYRO_ROBE, SKIN, layer="near", cuff=PYRO_TRIM, hands=False)
    # the flame crest the hair rises into
    hx, hy = r.hx, r.head_cy
    flame(draw, hx - d * 2.0, hy - 3.0, 22.0, 20.0, colors=(PYRO_HAIR, (255, 132, 50), (255, 214, 96)),
          sway=sway * 1.6 - d * 3.0)
    rig_head(r, draw, SKIN, hair=PYRO_HAIR, style="spiky", eye_color=(236, 140, 30), expression="grin",
             mood="sharp")
    for side in _sides(r):
        h = rig_hand(r, draw, side, SKIN, reach=0.35 if side == fire_side else 0.0)
        if side == fire_side and not r.back:
            fx, fy = h[0] + (1.8 if not d else d * 2.4), h[1] - 3.0
            cel(draw, Ell(fx, fy + 0.6, 2.6, 2.4), (255, 150, 52), sh=None)
            flame(draw, fx, fy + 2.6, 6.4, 8.4 + [0, 1.0, 0, 1.0][frame], sway=sway)


# ===================================================================
# CRYOMANCER (13) -- ice crown, fur-trimmed robe, crystal staff
# ===================================================================

CRYO_ROBE = (118, 176, 238)
CRYO_FUR = (246, 250, 255)
CRYO_HAIR = (176, 214, 248)


def _snowflake(draw, x, y, rad, color=(255, 255, 255)):
    for a in (0, 60, 120):
        ca, sa = math.cos(math.radians(a)), math.sin(math.radians(a))
        stroke(draw, [(x - ca * rad, y - sa * rad), (x + ca * rad, y + sa * rad)], 0.6, color)


def draw_cryomancer(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, head=0.95)
    d = r.d
    side_s = _hold_side(r)
    out = 1.6 if not d else 0.4
    if r.back:
        x = _staff(draw, r, side_s, out, r.head_cy - 2.0, color=(196, 206, 226))
        crystal(draw, x, r.head_cy - 1.0, -90, 9.0, 4.6)
    if d:
        rig_arms(r, draw, CRYO_ROBE, SKIN, layer="far", cuff=CRYO_FUR)
    rig_legs(r, draw, shade(CRYO_ROBE, 1.2), (150, 170, 206))
    rig_robe(r, draw, CRYO_ROBE, trim=CRYO_FUR, flare=3.2)
    if not r.back:
        _snowflake(draw, r.cx + d * 2.0, r.hip_y - 2.0, 2.2)
    rig_belt(r, draw, (80, 120, 190), buckle=(206, 236, 255))
    # fur collar
    cx = r.cx
    cel(draw, RRect(cx - 7.4, r.sh_y - 3.2, cx + 7.4, r.sh_y + 0.4, 1.8) if not d else
        RRect(cx - 5.4, r.sh_y - 3.2, cx + 5.8, r.sh_y + 0.4, 1.8), CRYO_FUR, sh=(0.0, 0.8))
    rig_arms(r, draw, CRYO_ROBE, SKIN, layer="near", cuff=CRYO_FUR, hands=False)
    rig_head(r, draw, SKIN, hair=CRYO_HAIR, style="swept", eye_color=(70, 150, 214), expression="set",
             mood="calm")
    # a crown of ice crystals
    hx, hy, ry = r.hx, r.head_cy, r.head_ry
    spikes = ((-5.4, -110, 5.0), (-2.6, -98, 7.0), (0.4, -90, 8.4), (3.2, -82, 7.0), (5.8, -70, 5.0)) if not d else \
        ((-d * 2.4, -90 - d * 18, 6.0), (d * 0.8, -90 - d * 8, 7.6), (d * 3.8, -90 + d * 2, 6.0))
    for (u, a, ln) in spikes:
        crystal(draw, hx + u, hy - ry + 2.6, a, ln, 2.8)
    if not r.back:
        x = _staff(draw, r, side_s, out, r.head_cy - 2.0, color=(196, 206, 226))
        crystal(draw, x, r.head_cy - 1.0, -90, 9.0, 4.6)
        for i, (sx, sy) in enumerate(((-4.0, -2.0), (4.2, 3.0), (-3.0, 6.0))):
            if (frame + i) % 2 == 0:
                sparkle(draw, x + sx, r.head_cy - 6.0 + sy, 1.6, (230, 246, 255))
    for side in _sides(r):
        rig_hand(r, draw, side, SKIN, out=out if side == side_s else 0.0)


# ===================================================================
# STORMCALLER (14) -- electric hair, a thundercloud on the shoulder
# ===================================================================

STORM_COAT = (62, 60, 128)
STORM_TRIM = (255, 226, 76)
STORM_HAIR = (250, 246, 196)
STORM_CLOUD = (132, 136, 162)


def draw_stormcaller(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame)
    d = r.d
    rod_side = _hold_side(r)
    out = 1.4 if not d else 0.4
    if d:
        rig_arms(r, draw, STORM_COAT, SKIN, layer="far", cuff=STORM_TRIM)
    rig_legs(r, draw, shade(STORM_COAT, 1.0), (60, 52, 80))
    shape = rig_robe(r, draw, STORM_COAT, trim=STORM_TRIM, flare=2.6, split=True)
    # zigzag bolt embroidered down the front
    if not r.back:
        x = r.cx + d * 2.2
        bolt(draw, [(x - 1.2, r.sh_y + 0.8), (x + 1.4, r.waist_y - 0.6), (x - 1.0, r.waist_y + 1.2),
                    (x + 1.6, r.hip_y + 3.0)], 1.3, STORM_TRIM)
    rig_belt(r, draw, shade(STORM_COAT, 1.6), buckle=STORM_TRIM)
    rig_arms(r, draw, STORM_COAT, SKIN, layer="near", cuff=STORM_TRIM, hands=False)
    rig_head(r, draw, SKIN, hair=STORM_HAIR, style="wild", eye_color=(230, 170, 30), expression="grin",
             mood="sharp")
    # the thundercloud riding his shoulder, a bolt dropping out of it
    side = -rod_side if not d else -d
    cx0 = r.cx + side * 13.0 if not d else r.cx - d * 11.0
    cy0 = r.head_cy - 1.0 + [0, -0.6, 0, -0.6][frame]
    cloud(draw, cx0, cy0, 11.0, 6.0, STORM_CLOUD, tone=shade(STORM_CLOUD, 1.2))
    if frame % 2 == 0:
        bolt(draw, [(cx0 - 0.6, cy0 + 3.0), (cx0 + 1.4, cy0 + 6.0), (cx0 - 0.8, cy0 + 7.0), (cx0 + 0.8, cy0 + 10.4)],
             1.4, STORM_TRIM)
    for side2 in _sides(r):
        h = rig_hand(r, draw, side2, SKIN, out=out if side2 == rod_side else 0.0)
    # lightning rod: a forked metal staff crackling at the tips
    if not r.back:
        h = hand_at(r, rod_side, 0.0, 0.0, out)
        x = h[0] + 0.2
        top = r.head_cy - 4.0
        cel(draw, Limb([(x, r.base_y - 1.0), (x, top)], [0.9, 0.8]), (190, 196, 214), sh=None)
        for s in (-1, 1):
            cel(draw, Limb([(x, top + 1.6), (x + s * 3.0, top - 1.4), (x + s * 3.4, top - 4.2)], [0.7, 0.6, 0.4]),
                (190, 196, 214), sh=None)
            if (frame + (s > 0)) % 2 == 0:
                sparkle(draw, x + s * 3.4, top - 4.6, 2.2, STORM_TRIM)


# ===================================================================
# EARTHSHAKER (15) -- a stout dwarf with boulder pauldrons and rock fists
# ===================================================================

EARTH_STONE = (152, 142, 130)
EARTH_MOSS = (112, 164, 80)
EARTH_TUNIC = (72, 122, 100)
EARTH_BEARD = (200, 112, 54)


def _rock(draw, x, y, rx, ry, color=EARTH_STONE, moss=True):
    pts = []
    for i in range(9):
        a = math.radians(i * 40 + 12)
        k = 1.0 + (0.12 if i % 3 == 0 else (-0.08 if i % 3 == 1 else 0.02))
        pts.append((x + rx * k * math.cos(a), y + ry * k * math.sin(a)))
    cel(draw, Poly(pts), color, sh=(0.9, 0.9), hi=(0.6, 0.6))
    if moss:
        cel(draw, Poly([(x - rx * 0.9, y - ry * 0.3), (x - rx * 0.2, y - ry * 1.02), (x + rx * 0.6, y - ry * 0.86),
                        (x + rx * 0.2, y - ry * 0.4), (x - rx * 0.3, y - ry * 0.2)]), EARTH_MOSS, sh=None, line=False)


def draw_earthshaker(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=1.32, head=0.96)
    d = r.d
    if d:
        rig_arms(r, draw, SKIN_TAN, SKIN_TAN, layer="far", hands=False)
        h = hand_at(r, -d)
        _rock(draw, h[0], h[1], 3.4, 3.0, shade(EARTH_STONE, 0.5), moss=False)
    rig_legs(r, draw, shade(EARTH_TUNIC, 1.0), (96, 70, 52), width=1.1)
    rig_torso(r, draw, EARTH_TUNIC)
    if not r.back:
        cel(draw, Poly([(r.cx - 3.0 + d * 2, r.sh_y - 1.8), (r.cx + 3.0 + d * 2, r.sh_y - 1.8),
                        (r.cx + 2.0 + d * 2, r.waist_y), (r.cx - 2.0 + d * 2, r.waist_y)]), shade(EARTH_TUNIC, 1.3),
            sh=None, line=False)
    rig_belt(r, draw, (96, 70, 52), buckle=EARTH_STONE)
    rig_arms(r, draw, SKIN_TAN, SKIN_TAN, layer="near", hands=False)
    for side in _sides(r):
        sx, sy = r.shoulder(side)
        _rock(draw, sx + (side * 1.2 if not d else 0.0), sy - 1.0, 4.6, 3.6)
    head_skull(r, draw, SKIN_TAN, ears=False)
    hx, hy = r.hx, r.head_cy
    if not r.back:
        head_face(r, draw, SKIN_TAN, iris=(84, 150, 70), mood="sharp", mouth=None, brows=False)
        e1, e2, ey, _, _ = face_anchor(r)
        for side, ex in (((-1, e1), (1, e2)) if not d else ((-d, e1),)):
            blob(draw, [Ell(ex, ey - 3.8, 2.8, 1.3), Ell(ex + side * 2.0, ey - 3.4, 1.6, 1.1)], EARTH_BEARD,
                 sh=(0.0, 0.5), lw=0.8)
        # the beard: a big braided mass
        if d:
            parts = [Ell(hx + d * 4.6, hy + 9.4, 5.6, 5.8), Ell(hx + d * 1.4, hy + 6.8, 4.0, 4.0),
                     Ell(hx + d * 5.4, hy + 14.6, 2.6, 3.0)]
        else:
            parts = [Ell(hx, hy + 9.8, 7.8, 6.6), Ell(hx - 7.4, hy + 6.8, 3.6, 3.8), Ell(hx + 7.4, hy + 6.8, 3.6, 3.8),
                     Ell(hx - 2.6, hy + 15.4, 2.4, 3.2), Ell(hx + 2.6, hy + 15.4, 2.4, 3.2)]
        blob(draw, parts, EARTH_BEARD, sh=(1.1, 1.2))
        if not d:
            for s in (-1, 1):
                cel(draw, RRect(hx + s * 2.6 - 1.0, hy + 15.6, hx + s * 2.6 + 1.0, hy + 16.8, 0.4), GOLD, sh=None)
    else:
        rig_hair(r, draw, EARTH_BEARD, "crop", hat=True)
    # stone helm with a crack and a pair of stubby horns
    rx, ry = r.head_rx, r.head_ry
    for s in ((-1, 1) if not d else (-d,)):
        hb = (hx + s * (rx - 1.0), hy - ry + 3.6) if not d else (hx - d * 3.0, hy - ry + 1.6)
        cel(draw, Limb([hb, (hb[0] + s * 3.4 if not d else hb[0] - d * 3.4, hb[1] - 3.0),
                        (hb[0] + s * 4.0 if not d else hb[0] - d * 5.4, hb[1] - 6.0)], [2.0, 1.4, 0.4]),
            (236, 226, 204), sh=(0.5, 0.5))
    dome = arc_pts(hx - d * 0.4, hy - 0.6, rx + 1.4, ry + 0.8, 182, 358, 26)
    cel(draw, Poly(dome + [(hx + rx + 1.6, hy - 1.4), (hx - rx - 1.6, hy - 1.4)]), EARTH_STONE, sh=(1.4, 1.0),
        hi=(0.7, 0.7))
    stroke(draw, [(hx - 1.0, hy - ry - 0.6), (hx + 0.8, hy - ry + 3.0), (hx - 0.4, hy - ry + 5.4)], 0.6,
           shade(EARTH_STONE, 1.8))
    cel(draw, Poly([(hx - rx * 0.6, hy - ry + 0.2), (hx - rx * 0.1, hy - ry - 0.8), (hx + rx * 0.3, hy - ry + 0.6),
                    (hx - rx * 0.2, hy - ry + 1.8)]), EARTH_MOSS, sh=None, line=False)
    # rock fists, oversized
    for side in _sides(r):
        h = hand_at(r, side)
        _rock(draw, h[0], h[1], 3.6, 3.2, moss=False)


# ===================================================================
# WINDWALKER (16) -- mint and white, a ribbon the wind carries, a fan
# ===================================================================

WIND_ROBE = (226, 246, 238)
WIND_MINT = (96, 206, 170)
WIND_HAIR = (180, 236, 216)


def _swirl(draw, x, y, rad, frame, color=(255, 255, 255)):
    a0 = frame * 50.0
    pts = [(x + math.cos(math.radians(a0 + t * 22)) * rad * (1 - t * 0.06),
            y + math.sin(math.radians(a0 + t * 22)) * rad * 0.42 * (1 - t * 0.06)) for t in range(0, 12)]
    cel(draw, Limb(pts, [0.2] + [0.8] * 9 + [0.5, 0.2]), color, sh=None, line_color=shade(WIND_MINT, 0.8), lw=0.5)


def draw_windwalker(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=0.92)
    d = r.d
    fan_side = _hold_side(r)
    style = dict(HAIR_STYLES["swept"], extra="pony")
    rig_hair(r, draw, WIND_HAIR, style, layer="back")
    # the ribbon streaming behind
    wave = [0.0, 1.0, 0.0, -1.0][frame]
    if not r.back:
        s = -1 if not d else -d
        bx = r.cx + (s * 4.0 if not d else -d * 3.0)
        cel(draw, Limb([(bx, r.waist_y), (bx + s * 6.0, r.waist_y - 2.0 + wave), (bx + s * 11.0, r.waist_y + 1.0 - wave),
                        (bx + s * 15.0, r.waist_y - 1.6)], [1.4, 1.4, 1.2, 0.4]), WIND_MINT, sh=(0.0, 0.6))
    if d:
        rig_arms(r, draw, WIND_ROBE, SKIN, layer="far", cuff=WIND_MINT)
    rig_legs(r, draw, WIND_MINT, (80, 150, 126))
    rig_robe(r, draw, WIND_ROBE, trim=WIND_MINT, flare=2.2, hem=r.hip_y + 3.4, split=True)
    rig_belt(r, draw, WIND_MINT, buckle=(255, 255, 255))
    rig_arms(r, draw, WIND_ROBE, SKIN, layer="near", cuff=WIND_MINT, hands=False)
    rig_head(r, draw, SKIN, hair=WIND_HAIR, style=style, eye_color=(46, 160, 120), expression="smile")
    _swirl(draw, r.cx, r.base_y - 1.4, 11.0, frame)
    if r.back:
        rig_hair(r, draw, WIND_HAIR, style, layer="back")
    for side in _sides(r):
        h = rig_hand(r, draw, side, SKIN, reach=0.3 if side == fan_side else 0.0)
        if side == fan_side:
            # an open war fan, ribbed
            fx, fy = h[0], h[1] - 1.0
            base_a = -100 + (40 if (side > 0 and not d) or d > 0 else -40)
            pts = [(fx, fy)] + [(fx + math.cos(math.radians(base_a + t)) * 8.4, fy + math.sin(math.radians(base_a + t)) * 8.4)
                                for t in range(-50, 51, 10)]
            cel(draw, Poly(pts), (248, 252, 250), sh=None, line_color=shade(WIND_MINT, 1.4))
            for t in range(-50, 51, 20):
                stroke(draw, [(fx, fy), (fx + math.cos(math.radians(base_a + t)) * 8.0,
                                         fy + math.sin(math.radians(base_a + t)) * 8.0)], 0.45, WIND_MINT)
            arc = [(fx + math.cos(math.radians(base_a + t)) * 6.0, fy + math.sin(math.radians(base_a + t)) * 6.0)
                   for t in range(-50, 51, 10)]
            stroke(draw, arc, 0.6, WIND_MINT)


# ===================================================================
# MAGMA KNIGHT (17) -- volcanic plate; the helm is a little volcano
# ===================================================================

MAG_ROCK = (112, 70, 62)
MAG_DARK = (70, 46, 48)
MAG_LAVA = (255, 122, 40)
MAG_HOT = (255, 216, 96)
MAG_FIRE = ((240, 80, 36), (255, 160, 52), (255, 236, 130))


def _cracks(draw, pts_list, glow=0):
    for pts in pts_list:
        stroke(draw, pts, 0.9, MAG_HOT if glow else MAG_LAVA)


def draw_magmaknight(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=1.24, head=0.96)
    d = r.d
    sword_side = _hold_side(r)
    glow = [0, 1, 0, 1][frame]
    sway = [0.0, 1.0, 0.0, -1.0][frame]
    if d:
        rig_arms(r, draw, MAG_ROCK, MAG_DARK, layer="far")
    rig_legs(r, draw, MAG_ROCK, MAG_DARK, width=1.1)
    rig_torso(r, draw, MAG_ROCK)
    cx = r.cx
    if not r.back:
        _cracks(draw, [[(cx - 3.0 + d * 2, r.sh_y), (cx - 1.0 + d * 2, r.sh_y + 3.0), (cx - 2.4 + d * 2, r.waist_y)],
                       [(cx + 2.4 + d * 2, r.sh_y + 1.0), (cx + 1.0 + d * 2, r.sh_y + 4.0)]], glow)
    else:
        _cracks(draw, [[(cx - 2.0, r.sh_y), (cx + 1.0, r.sh_y + 3.4), (cx - 0.6, r.waist_y)]], glow)
    rig_belt(r, draw, MAG_DARK, buckle=MAG_LAVA)
    rig_arms(r, draw, MAG_ROCK, MAG_DARK, layer="near", hands=False)
    for side in _sides(r):
        sx, sy = r.shoulder(side)
        ox2 = side * 1.2 if not d else 0.0
        # pauldrons of jagged rock, lava dripping from them
        cel(draw, Poly([(sx + ox2 - 5.0, sy + 1.4), (sx + ox2 - 4.4, sy - 2.6), (sx + ox2 - 1.6, sy - 4.8), (sx + ox2 + 1.8, sy - 4.2),
                        (sx + ox2 + 4.6, sy - 2.2), (sx + ox2 + 5.0, sy + 1.4)]), MAG_ROCK, sh=(0.8, 0.8), hi=(0.5, 0.5))
        _cracks(draw, [[(sx + ox2 - 2.0, sy - 3.4), (sx + ox2 - 0.6, sy - 1.0), (sx + ox2 - 1.6, sy + 0.8)]], glow)
        Poly([(sx + ox2 + 2.0, sy + 1.2), (sx + ox2 + 3.2, sy + 1.2), (sx + ox2 + 2.6, sy + 3.6 + glow)]).draw(draw, fill=MAG_LAVA)
    head_skull(r, draw, MAG_ROCK, ears=False)
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    # the helm: a cone of volcanic rock with a crater on top, erupting
    top = hy - ry - 1.4
    flame(draw, hx - d * 0.6, top + 2.4, 12.0, 11.0 + glow * 1.6, MAG_FIRE, sway=sway - d * 1.6)
    helm = Poly([(hx - rx - 1.0, hy + 4.4), (hx - rx - 1.6, hy - 1.0), (hx - 5.6, top), (hx - 3.4, top + 1.0), (hx - 1.0, top - 0.2),
                 (hx + 1.2, top + 1.0), (hx + 3.6, top - 0.2), (hx + 5.6, top), (hx + rx + 1.6, hy - 1.0), (hx + rx + 1.0, hy + 4.4)])
    cel(draw, helm, MAG_ROCK, sh=(1.8, 1.2), hi=(0.9, 0.9))
    # lava running down from the crater
    for (u, ln) in ((-3.6, 5.4), (1.4, 7.4), (4.4, 4.4)) if not d else ((-d * 3.0, 5.0), (d * 1.0, 6.6)):
        cel(draw, Limb([(hx + u, top + 0.6), (hx + u * 1.14, top + ln)], [1.1, 0.7]), MAG_LAVA, sh=None, line=False)
    if not r.back:
        vx = hx + d * 3.0
        w = rx - 2.4 if not d else rx * 0.6
        cel(draw, RRect(vx - w, hy + 0.6, vx + w, hy + 3.8, 1.4), (40, 24, 26), sh=None)
        for s in ((-1, 1) if not d else (d,)):
            ex = vx + s * 4.0 if not d else vx + d * 2.4
            Ell(ex, hy + 2.2, 2.2, 1.1).draw(draw, fill=MAG_HOT if glow else MAG_LAVA)
    # molten greatsword
    h = hand_at(r, sword_side)
    ang = 90 + sword_side * 28 if not d else 90 - d * 40
    blade(draw, h[0], h[1], ang, length=15.0, width=2.0, color=MAG_LAVA, hilt=MAG_DARK, grip=(50, 34, 36),
          guard=3.4, flip=sword_side if not d else -d)
    tip = xform([(15.0, 0.0)], h[0], h[1], ang)[0]
    if glow:
        sparkle(draw, tip[0], tip[1], 2.0, MAG_HOT)
    for side in _sides(r):
        rig_hand(r, draw, side, MAG_DARK)


# ===================================================================
# FROSTBITE (18) -- an ice imp: icicle crown, fang grin, ice daggers
# ===================================================================

FROST_SKIN = (164, 216, 246)
FROST_FUR = (246, 250, 255)


def draw_frostbite(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=0.9, head=1.0)
    d = r.d
    if d:
        rig_arms(r, draw, FROST_SKIN, FROST_SKIN, layer="far", hands=False)
    rig_legs(r, draw, FROST_SKIN, shade(FROST_SKIN, 1.4), bare=True)
    rig_torso(r, draw, FROST_SKIN)
    # fur wrap round the middle, a fur collar
    cx = r.cx
    cel(draw, Poly([(cx - r.hip_w - 0.8, r.waist_y - 0.4), (cx + r.hip_w + 0.8, r.waist_y - 0.4),
                    (cx + r.hip_w + 1.6, r.hip_y + 2.6), (cx + 2.0, r.hip_y + 1.6), (cx - 1.0, r.hip_y + 3.2),
                    (cx - r.hip_w - 1.6, r.hip_y + 2.4)]) if not d else
        Poly([(cx - 4.8, r.waist_y - 0.4), (cx + 5.0, r.waist_y - 0.4), (cx + 5.6, r.hip_y + 2.6), (cx - 5.6, r.hip_y + 2.8)]),
        FROST_FUR, sh=(0.8, 0.8), tone=(200, 216, 240))
    blob(draw, [Ell(cx - 4.4, r.sh_y - 1.6, 3.4, 2.2), Ell(cx, r.sh_y - 1.0, 4.0, 2.4), Ell(cx + 4.4, r.sh_y - 1.6, 3.4, 2.2)]
         if not d else [Ell(cx, r.sh_y - 1.4, 4.6, 2.4)], FROST_FUR, sh=(0.6, 0.8), tone=(200, 216, 240))
    rig_arms(r, draw, FROST_SKIN, FROST_SKIN, layer="near", hands=False)
    head_skull(r, draw, FROST_SKIN, ears=False)
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    # long pointed ears
    for s in ((-1, 1) if not d else (-d,)):
        ex = hx + s * (rx - 0.8) if not d else hx - d * 3.4
        tip = (ex + s * 5.0, hy - 3.0) if not d else (ex - d * 4.6, hy - 3.4)
        cel(draw, Poly([(ex, hy - 0.4), tip, (ex + (s * 0.8 if not d else 0), hy + 3.8)]), FROST_SKIN, sh=(0.3, 0.4))
    if not r.back:
        head_face(r, draw, FROST_SKIN, iris=(40, 170, 220), mood="sharp", mouth="fangs", blush=False)
    # a crown of icicles standing up off the scalp
    spikes = ((-6.6, -122, 6.0), (-3.4, -104, 8.4), (0.0, -90, 9.6), (3.4, -76, 8.4), (6.6, -58, 6.0)) if not d else \
        ((-d * 5.2, -90 - d * 40, 7.0), (-d * 2.0, -90 - d * 22, 9.0), (d * 1.6, -90 - d * 6, 8.0))
    for (u, a, ln) in spikes:
        crystal(draw, hx + u, hy - ry + 3.6, a, ln, 3.4, color=(210, 240, 255))
    for side in _sides(r):
        h = rig_hand(r, draw, side, FROST_SKIN)
        if not r.back:
            ang = 90 + (side * 30 if not d else -d * 40)
            crystal(draw, h[0], h[1] + 0.6, ang, 7.4, 2.4, color=(214, 242, 255))


# ===================================================================
# SANDSTORM (19) -- turban and veil, desert robes, a scimitar
# ===================================================================

SAND = (224, 198, 142)
SAND_RED = (196, 60, 52)


def draw_sandstorm(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame)
    d = r.d
    sw_side = _hold_side(r)
    if d:
        rig_arms(r, draw, SAND, SKIN_TAN, layer="far")
    rig_legs(r, draw, shade(SAND, 0.8), (150, 104, 62))
    rig_robe(r, draw, SAND, trim=SAND_RED, flare=3.0, split=True)
    rig_belt(r, draw, SAND_RED, buckle=GOLD)
    if not d and not r.back:
        cel(draw, Poly([(r.cx - 5.0, r.waist_y + 2.6), (r.cx - 7.6, r.waist_y + 7.0), (r.cx - 5.6, r.waist_y + 7.4),
                        (r.cx - 3.6, r.waist_y + 3.2)]), SAND_RED, sh=(0.3, 0.3))
    rig_arms(r, draw, SAND, SKIN_TAN, layer="near", hands=False)
    head_skull(r, draw, SKIN_TAN, ears=False)
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    if not r.back:
        head_face(r, draw, SKIN_TAN, iris=(150, 104, 40), mood="sharp", mouth=None, blush=False, brows=True,
                  brow_color=(90, 60, 40))
        # veil over the lower face
        if d:
            veil = Poly([(hx - d * 3.6, hy + 4.4), (hx + d * (rx + 0.6), hy + 4.0), (hx + d * (rx - 1.0), hy + 9.6),
                         (hx + d * 3.0, hy + 12.4), (hx - d * 4.4, hy + 10.0)])
        else:
            veil = Poly([(hx - rx + 0.4, hy + 4.4), (hx + rx - 0.4, hy + 4.4), (hx + rx - 2.0, hy + 9.6),
                         (hx, hy + 13.0), (hx - rx + 2.0, hy + 9.6)])
        cel(draw, veil, SAND_RED, sh=(0.8, 0.8))
    # turban: wound bands over the crown, a red gem on the front
    dome = arc_pts(hx - d * 0.5, hy - 1.2, rx + 1.8, ry + 1.8, 180, 360, 30)
    cel(draw, Poly(dome + [(hx + rx + 1.8, hy + 1.0 - (0 if not r.back else -3)), (hx - rx - 1.8, hy + 1.0 - (0 if not r.back else -3))]),
        SAND, sh=(1.5, 1.1), hi=(0.8, 0.8))
    for k in (-3.2, 1.0):
        stroke(draw, [(hx - rx * 0.9, hy + k - 0.4), (hx, hy + k - 3.4), (hx + rx * 0.9, hy + k - 5.0)], 0.6,
               shade(SAND, 1.2))
    if not r.back:
        gx = hx + d * 3.6
        cel(draw, Ell(gx, hy - 5.4, 2.0, 2.2), GOLD, sh=None)
        from sprite_base import gem
        gem(draw, gx, hy - 5.4, 1.3, SAND_RED)
    else:
        cel(draw, Limb([(hx + 3.0, hy + 0.6), (hx + 5.4, hy + 8.0 + [0, 1, 0, -1][frame])], [1.8, 0.8]), SAND, sh=None)
    # a scimitar
    h = hand_at(r, sw_side)
    ang = 90 + sw_side * 34 if not d else 90 - d * 44
    blade(draw, h[0], h[1], ang, length=12.0, width=1.8, curve=-0.9, hilt=GOLD, grip=SAND_RED, guard=2.4,
          flip=sw_side if not d else -d)
    for side in _sides(r):
        rig_hand(r, draw, side, SKIN_TAN)
    # grit blowing past
    for i, (u, v) in enumerate(((-13, -6), (12, 2), (-10, 8))):
        k = (frame + i) % 4
        Ell(r.cx + u + k * 1.4, r.sh_y + v - k * 0.4, 0.8, 0.8).draw(draw, fill=lit(SAND, 0.8))


# ===================================================================
# THORNWEAVER (20) -- flower crown, leaf-hemmed dress, a blooming staff
# ===================================================================

THORN_DRESS = (86, 168, 86)
THORN_HAIR = (164, 96, 62)
THORN_PINK = (255, 136, 182)
THORN_WOOD = (132, 88, 56)


def draw_thornweaver(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=0.94)
    d = r.d
    st_side = _hold_side(r)
    out = 1.6 if not d else 0.4
    if d:
        rig_arms(r, draw, THORN_DRESS, SKIN, layer="far")
    rig_legs(r, draw, shade(THORN_DRESS, 1.0), THORN_WOOD)
    rig_robe(r, draw, THORN_DRESS, flare=3.6)
    # a hem of leaves
    xs = [r.cx + i * 2.8 for i in range(-4, 5)] if not d else [r.cx + i * 2.8 for i in range(-3, 3)]
    for i, x in enumerate(xs):
        leaf(draw, x, r.base_y - 4.4, 90 + (i % 2) * 16 - 8, 4.2, 2.6, lit(THORN_DRESS, 0.4) if i % 2 else THORN_DRESS)
    rig_belt(r, draw, THORN_WOOD, buckle=THORN_PINK)
    rig_arms(r, draw, THORN_DRESS, SKIN, layer="near", hands=False)
    # vines wound round the forearms
    for side in _sides(r):
        _, e, w, h = arm_pts(r, side)
        stroke(draw, [e, ((e[0] + w[0]) / 2 + 1.2, (e[1] + w[1]) / 2), w], 0.6, THORN_WOOD)
    rig_head(r, draw, SKIN, hair=THORN_HAIR, style="bob", eye_color=(60, 150, 70), expression="smile")
    # flower crown
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    pts = ((-8.4, 5.6), (-4.4, 8.6), (0.0, 9.6), (4.4, 8.6), (8.4, 5.6)) if not d else \
        ((d * 7.2, 7.0), (d * 3.2, 9.4), (-d * 1.6, 9.8), (-d * 6.0, 8.2))
    for i, (u, v) in enumerate(pts):
        if r.back and i % 2:
            continue
        x, y = hx + u, hy - v
        leaf(draw, x, y, -60 + i * 30, 3.6, 2.2, THORN_DRESS, vein=False)
        if i % 2 == 0:
            for a in range(0, 360, 72):
                Ell(x + math.cos(math.radians(a)) * 1.3, y + math.sin(math.radians(a)) * 1.3, 1.1, 1.1).draw(
                    draw, fill=THORN_PINK)
            Ell(x, y, 0.8, 0.8).draw(draw, fill=(255, 226, 120))
    # thorny staff with a bloom on top
    if not r.back:
        x = _staff(draw, r, st_side, out, r.head_cy - 1.0, THORN_WOOD)
        for y in (r.hip_y - 2, r.sh_y + 1, r.head_cy + 8):
            Poly([(x + 0.8, y), (x + 2.6, y - 1.4), (x + 0.8, y - 1.6)]).draw(draw, fill=shade(THORN_WOOD, 1.2))
        top = r.head_cy - 2.0
        for a in range(0, 360, 60):
            cel(draw, Ell(x + math.cos(math.radians(a + frame * 10)) * 2.4, top + math.sin(math.radians(a + frame * 10)) * 2.4,
                          1.9, 1.9), THORN_PINK, sh=None, lw=0.6)
        cel(draw, Ell(x, top, 1.5, 1.5), (255, 226, 120), sh=None, lw=0.6)
        leaf(draw, x, top + 3.0, 200, 4.4, 2.6, THORN_DRESS)
    for side in _sides(r):
        rig_hand(r, draw, side, SKIN, out=out if side == st_side else 0.0)


# ===================================================================
# CLOUDRUNNER (21) -- cloud-puff hair, riding a little cloud
# ===================================================================

CLOUD_TUNIC = (104, 176, 242)
CLOUD_WHITE = (250, 252, 255)
CLOUD_SCARF = (255, 206, 72)


def draw_cloudrunner(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, bob=[-1, -2, -2, -1][frame], step=0)
    d = r.d
    wave = [0.0, 1.0, 0.0, -1.0][frame]
    # scarf streaming behind
    s = -1 if not d else -d
    y0 = r.sh_y - 1.6
    bx = r.cx + (s * 3.4 if not d else -d * 2.6)
    if not r.back:
        cel(draw, Limb([(bx, y0), (bx + s * 5.4, y0 + 1.0 + wave), (bx + s * 10.4, y0 - 0.6 - wave), (bx + s * 13.6, y0 + 0.8)],
                       [1.5, 1.4, 1.2, 0.5]), CLOUD_SCARF, sh=(0.0, 0.7))
    if d:
        rig_arms(r, draw, CLOUD_TUNIC, SKIN, layer="far")
    rig_legs(r, draw, shade(CLOUD_TUNIC, 1.2), CLOUD_WHITE, bare=True)
    rig_torso(r, draw, CLOUD_TUNIC, bottom=r.hip_y + 2.0)
    cx = r.cx
    cel(draw, Poly([(cx - r.hip_w - 0.4, r.hip_y + 0.2), (cx + r.hip_w + 0.4, r.hip_y + 0.2),
                    (cx + r.hip_w + 0.6, r.hip_y + 2.2), (cx - r.hip_w - 0.6, r.hip_y + 2.2)]) if not d else
        Poly([(cx - 4.6, r.hip_y + 0.2), (cx + 4.8, r.hip_y + 0.2), (cx + 5.0, r.hip_y + 2.2), (cx - 4.8, r.hip_y + 2.2)]),
        CLOUD_WHITE, sh=None)
    rig_belt(r, draw, CLOUD_SCARF, buckle=CLOUD_WHITE)
    rig_arms(r, draw, CLOUD_TUNIC, SKIN, layer="near", hands=False)
    # scarf round the neck
    cel(draw, RRect(cx - 6.8, y0 - 1.4, cx + 6.8, y0 + 1.8, 1.6) if not d else RRect(cx - 5.0, y0 - 1.4, cx + 5.4, y0 + 1.8, 1.6),
        CLOUD_SCARF, sh=(0.6, 0.6))
    rig_head(r, draw, SKIN, hair=CLOUD_WHITE, style="curly", eye_color=(70, 150, 230), expression="open" if frame % 2 else "smile")
    if r.back:
        cel(draw, Limb([(bx, y0), (bx + 4.0, y0 + 6.0 + wave), (bx + 3.0, y0 + 11.0)], [1.5, 1.3, 0.5]), CLOUD_SCARF, sh=(0.0, 0.7))
    for side in _sides(r):
        rig_hand(r, draw, side, SKIN)
    # the cloud he rides on, hiding his feet
    cloud(draw, r.cx, r.base_y + 0.4 - r.bob * 0.0, 20.0, 6.0, CLOUD_WHITE, tone=(206, 222, 246),
          bumps=((-0.34, 0.0, 0.22), (-0.08, -0.16, 0.26), (0.2, -0.08, 0.24), (0.38, 0.06, 0.18)))


# ===================================================================
# INFERNO (22) -- a living flame
# ===================================================================

INF_COLORS = ((226, 62, 38), (255, 142, 44), (255, 226, 110))


def draw_inferno(draw, ox, oy, direction, frame):
    bob = [0, -1, -2, -1][frame]
    base = oy + 54 + bob
    cx = ox + 32
    d = {DOWN: 0, UP: 0, LEFT: -1, RIGHT: 1}[direction]
    back = direction == UP
    sway = [0.0, 1.2, 0.0, -1.2][frame]
    # flame tail instead of legs
    flame(draw, cx - d * 2.0, base - 1.0, 12.0, 16.0, INF_COLORS, sway=sway - d * 2.0, flip=-1)
    # body: a flame torso
    body = Poly(flame_pts(cx, base - 6.0, 17.0, 22.0, sway * 0.5 - d * 1.4))
    cel(draw, body, INF_COLORS[0], sh=None)
    Poly(flame_pts(cx, base - 7.0, 11.0, 15.0, sway * 0.3)).draw(draw, fill=INF_COLORS[1])
    Ell(cx + d * 1.4, base - 15.0, 3.6, 4.2).draw(draw, fill=INF_COLORS[2])
    # flame arms, reaching out
    for side in ((-1, 1) if not d else (d, -d)):
        sx = cx + side * 5.4 if not d else cx + side * 1.4
        near = (not d) or side == d
        col = INF_COLORS[0] if near else shade(INF_COLORS[0], 0.5)
        end = (sx + (side * 5.6 if not d else side * 6.2), base - 18.0 + (sway if side > 0 else -sway) * 0.6)
        cel(draw, Limb([(sx, base - 21.0), ((sx + end[0]) / 2 + (side if not d else 0) * 0.6, base - 17.6), end],
                       [2.6, 2.2, 1.8]), col, sh=None)
        flame(draw, end[0], end[1] + 1.6, 4.4, 6.0, INF_COLORS, sway=(side if not d else d) * 1.0)
    # head: a tall flame with a face in it
    hy = base - 30.0
    hx = cx + d * 1.6
    flame(draw, hx, hy + 10.0, 22.0, 30.0, INF_COLORS, sway=sway * 1.4 - d * 3.4)
    cel(draw, Ell(hx, hy + 2.4, 8.4, 7.6), INF_COLORS[1], sh=None, line=False)
    if back:
        return
    e1, e2 = (hx - 3.6, hx + 3.6) if not d else (hx + d * 0.6, hx + d * 5.4)
    for side, ex in ((-1, e1), (1, e2)):
        w = 1.0 if not d or (ex - hx) * d < 3 else 0.7
        Poly([(ex - side * 2.2 * w, hy + 1.6), (ex - side * 0.6 * w, hy - 0.8), (ex + side * 2.2 * w, hy - 1.0),
              (ex + side * 1.6 * w, hy + 2.0), (ex - side * 0.8 * w, hy + 2.6)]).draw(draw, fill=(90, 20, 22))
        Ell(ex + side * 0.3, hy + 0.6, 0.8 * w, 0.8).draw(draw, fill=(255, 250, 220))
    mx = hx + d * 3.0
    Poly([(mx - 2.8, hy + 4.6), (mx + 2.8, hy + 4.6), (mx + 1.6, hy + 6.6), (mx - 1.6, hy + 6.6)]).draw(draw, fill=(90, 20, 22))
    for k in (-1.4, 0.0, 1.4):
        Poly([(mx + k - 0.5, hy + 4.6), (mx + k + 0.5, hy + 4.6), (mx + k, hy + 5.6)]).draw(draw, fill=(255, 240, 200))


# ===================================================================
# GLACIER (23) -- a chunky ice golem: a rounded block of a head with snow
# lying on it, crystals growing out of one shoulder, big eyes, a glowing
# core, and faceted fists
# ===================================================================

GL_ICE = (176, 226, 250)
GL_DEEP = (104, 166, 226)
GL_SNOW = (248, 251, 255)
GL_CORE = (110, 238, 255)
GL_IRIS = (44, 98, 182)
GL_SHARD = (150, 228, 255)
GL_SNOW_LINE = (128, 168, 220)


def _squircle(cx, cy, rx, ry, n=2.8, steps=36):
    """A rounded square: the soft-cornered block an ice cube is in a cartoon."""
    pts = []
    for i in range(steps):
        a = 2 * math.pi * i / steps
        c, s = math.cos(a), math.sin(a)
        pts.append((cx + rx * math.copysign(abs(c) ** (2.0 / n), c), cy + ry * math.copysign(abs(s) ** (2.0 / n), s)))
    return Poly(pts)


def _ice(draw, shape, color=GL_ICE):
    """Ice in flat cel tones: a shadow side and a pale facet toward the light."""
    cel(draw, shape, color, sh=(1.2, 1.2), hi=(0.7, 0.7), tone=shade(color, 1.1))


def _gem_facet(cx, cy, rx, ry, n=8, rot=22.5):
    """A faceted lump, a chunk of ice or a cut gem: an n-sided polygon."""
    pts = []
    for i in range(n):
        a = math.radians(rot + i * 360.0 / n)
        pts.append((cx + rx * math.cos(a), cy + ry * math.sin(a)))
    return Poly(pts)


def _shine(draw, x, y, k=1.0):
    """The two white streaks across the corner of an ice cube in a cartoon."""
    stroke(draw, [(x, y + 3.0 * k), (x + 3.0 * k, y)], 1.1 * k, GL_SNOW)
    stroke(draw, [(x + 0.6 * k, y + 5.6 * k), (x + 1.6 * k, y + 4.6 * k)], 0.9 * k, GL_SNOW)


def _snow(draw, lumps):
    """Snow lying on ice: one soft mass, lined in a pale blue rather than the
    ice's deep one, so it reads as a cap on the thing and not a band round it."""
    blob(draw, lumps, GL_SNOW, sh=(0.0, 0.9), tone=(214, 230, 250), line_color=GL_SNOW_LINE, lw=0.8)


def draw_glacier(draw, ox, oy, direction, frame):
    d, back, base, cx, ph = creature_setup(ox, oy, direction, frame)
    hx, hy = cx + d * 2.6, base - 32.6
    hrx, hry = 11.4 if not d else 10.6, 9.8

    # crystals grow out of his right shoulder and snow lies on his left: the
    # crystals are on the camera's left head-on, its right from behind, and
    # nearest the camera walking right
    xs = 1 if back else -1

    def cluster(near):
        col = GL_SHARD if near else shade(GL_SHARD, 0.5)
        if d:
            sx, sy = cx - d * 2.6, base - 26.0
            spec = ((0.0, 0.0, -90 - d * 26, 12.0, 5.4), (-d * 3.2, 1.6, -90 - d * 60, 8.4, 4.4), (d * 2.0, 0.8, -84 + d * 4, 7.0, 3.6))
        else:
            sx, sy = cx + xs * 10.2, base - 25.6
            spec = ((0.0, 0.0, -90 + xs * 22, 12.0, 5.4), (xs * 3.0, 1.8, -90 + xs * 56, 8.4, 4.4), (-xs * 2.6, 0.4, -86, 7.0, 3.6))
        for (u, v, a, ln, w) in spec:
            crystal(draw, sx + u, sy + v, a, ln, w, color=col)

    if not back and d <= 0:
        cluster(not d)
    # stubby legs
    for s in ((-1, 1) if not d else (-d, d)):
        near = not d or s == d
        fwd = ph * s if not d else (ph if near else -ph)
        lx = cx + (s * 5.6 if not d else d * fwd * 3.0 + s * 0.8)
        ly = base - (1.0 if fwd < 0 and not d else 0.0)
        _ice(draw, RRect(lx - 3.8, ly - 8.0, lx + 3.8, ly + 0.4, 2.0), GL_DEEP if near else shade(GL_DEEP, 0.6))

    def arm(s, near):
        col = GL_ICE if near else shade(GL_ICE, 0.6)
        fwd = -ph * s if not d else (-ph if near else ph)
        sx = cx + (s * 11.6 if not d else -d * 1.4 + s * 0.4)
        sy = base - (22.4 if not d else 20.4)
        fx = sx + (s * 2.8 if not d else d * (fwd * 3.6 + 0.6))
        fy = base - 12.2 - fwd * 0.8
        cel(draw, Limb([(sx, sy), (fx, fy)], [3.0, 2.6]), shade(col, 0.4), sh=(0.6, 0.0))
        _ice(draw, _gem_facet(sx, sy, 4.6, 4.0), col)
        if near and not d and s == -xs:
            _snow(draw, [Ell(sx + 0.2, sy - 2.8, 4.0, 1.8), Ell(sx - 1.4, sy - 3.8, 2.0, 1.6), Ell(sx + 1.6, sy - 3.4, 1.8, 1.4)])
        _ice(draw, _gem_facet(fx, fy, 5.4, 5.0, 8, 0.0), col)
        if near:
            _shine(draw, fx - 3.6, fy - 3.4, 0.6)
        if near and not back:
            for k in ((-1.8, 0.2, 2.2) if not d else (d * 0.4, d * 2.4)):
                stroke(draw, [(fx + k, fy + 1.0), (fx + k * 0.8, fy + 3.6)], 0.55, GL_DEEP)

    if d:
        arm(-d, False)
    # the body: a chunky block of ice with a glowing core
    bw = 10.0 if not d else 10.2
    bx = cx - d * 0.6
    by0, by1 = base - 26.0, base - 6.6
    body = Poly([(bx - bw + 1.0, by0), (bx + bw - 1.0, by0), (bx + bw + 1.0, by0 + 3.0), (bx + bw - 0.4, by1 - 2.0),
                 (bx + bw - 2.6, by1), (bx - bw + 2.6, by1), (bx - bw + 0.4, by1 - 2.0), (bx - bw - 1.0, by0 + 3.0)])
    _ice(draw, body, GL_ICE)
    if not back:
        kx, ky = cx + d * 5.6, base - 16.6
        glow = GL_CORE if frame % 2 == 0 else lit(GL_CORE, 0.6)
        cel(draw, _gem_facet(kx, ky, 3.2 if not d else 2.3, 3.4, 6, 0.0), glow, sh=None, line_color=GL_DEEP, lw=0.8)
        sparkle(draw, kx - 0.6, ky - 0.8, 1.7, (255, 255, 255))
    else:
        stroke(draw, [(cx - 3.4, base - 22.0), (cx - 0.4, base - 17.0), (cx - 2.0, base - 12.0)], 0.6, GL_DEEP)
    if d > 0 or back:
        cluster(True)
    # the head: a rounded block of ice with snow lying on it
    _ice(draw, _squircle(hx, hy, hrx, hry), GL_ICE)
    k = -d * 1.2
    _snow(draw, [Ell(hx + k + 0.6, hy - hry + 0.8, 7.4 if not d else 6.4, 2.6), Ell(hx + k - 2.6, hy - hry - 0.6, 3.6, 2.6),
                 Ell(hx + k + 2.6, hy - hry - 1.2, 3.8, 2.8), Ell(hx + k - 5.6, hy - hry + 2.0, 1.4, 1.8),
                 Ell(hx + k + 6.4, hy - hry + 2.2, 1.3, 1.6), Ell(hx + k + 0.8, hy - hry + 2.6, 1.4, 1.6)])
    if back:
        stroke(draw, [(hx + 3.0, hy + 1.0), (hx + 5.0, hy + 4.0), (hx + 3.6, hy + 6.4)], 0.6, GL_DEEP)
    else:
        _shine(draw, hx - hrx + 1.8, hy - 3.4, 0.8)
        ey = hy + 1.6
        if d:
            fx0 = hx + d * 2.6
            ms_eye(draw, fx0 + d * 5.6, ey, 3.0, 5.0, GL_IRIS, (float(d), 0.0), "sharp", skin=GL_ICE, side=d, lash=(30, 60, 120))
            ms_eye(draw, fx0, ey, 4.2, 5.6, GL_IRIS, (float(d), 0.0), "sharp", skin=GL_ICE, side=-d, lash=(30, 60, 120))
            Ell(fx0 - d * 2.4, hy + 5.4, 1.8, 0.9).draw(draw, fill=mix(GL_ICE, (255, 150, 180), 0.4))
        else:
            for s in (-1, 1):
                ms_eye(draw, hx + s * 4.6, ey, 4.4, 5.8, GL_IRIS, (0.0, 0.0), "sharp", skin=GL_ICE, side=s, lash=(30, 60, 120))
                Ell(hx + s * 8.0, hy + 5.4, 1.9, 0.9).draw(draw, fill=mix(GL_ICE, (255, 150, 180), 0.4))
        ms_mouth(draw, hx + d * 6.4, hy + 6.8, "o" if frame % 2 else "set", GL_ICE, 0.9)
    for s in ((-1, 1) if not d else (d,)):
        arm(s, True)


# ===================================================================
# MUDSLINGER (24) -- a swamp frog-kid: eyes bulging up in front of a
# lily-pad hat with a lotus on it, a wide grin, mud-spattered dungarees,
# webbed feet and a dripping mud ball
# ===================================================================

MUD_SKIN = (124, 194, 88)
MUD_BELLY = (214, 232, 150)
MUD = (138, 96, 60)
MUD_WET = (104, 70, 48)
MUD_DUNG = (86, 128, 178)
MUD_PAD = (70, 160, 76)
MUD_LOTUS = (255, 168, 204)


def _splat(draw, x, y, k, color=MUD_WET):
    """A splash of wet mud with a drip or two."""
    blob(draw, [Ell(x, y, 1.5 * k, 1.1 * k), Ell(x + 1.2 * k, y + 0.9 * k, 0.6 * k, 0.8 * k),
                Ell(x - 1.1 * k, y + 0.5 * k, 0.5 * k, 0.6 * k)], color, sh=None, line=False)


def _lily_pad(draw, x, y, rx, ry, notch, tilt):
    """A lily pad worn as a hat, seen from a little above and tipped at an
    angle: a flat round leaf with the V cut out of its near edge that makes
    it a lily pad and not a plate. `notch` is the cut's angle (90 = nearest
    the camera), `tilt` the hat's in degrees."""
    pts = arc_pts(x, y, rx, ry, notch + 16, notch + 344, 34) + [(x + math.cos(math.radians(notch)) * rx * 0.12, y)]
    pad = Poly(rot_pts(pts, x, y, tilt))
    cel(draw, pad, MUD_PAD, sh=(0.0, 1.2), hi=(0.0, -0.7), hi_tone=lit(MUD_PAD, 1.2))
    for a in (notch + 120, notch + 180, notch + 240):
        vein = [(x, y), (x + math.cos(math.radians(a)) * rx * 0.72, y + math.sin(math.radians(a)) * ry * 0.72)]
        stroke(draw, rot_pts(vein, x, y, tilt), 0.4, lit(MUD_PAD, 0.8))


def _lotus(draw, x, y, k=1.0):
    for a in (-160, -125, -90, -55, -20):
        r = math.radians(a)
        tip = (x + math.cos(r) * 3.6 * k, y + math.sin(r) * 3.4 * k)
        side = (math.cos(r + 1.57) * 1.2 * k, math.sin(r + 1.57) * 1.2 * k)
        cel(draw, Poly([(x + side[0] * 0.6, y + 0.8 + side[1] * 0.6), (x + side[0] + (tip[0] - x) * 0.5, y + side[1] + (tip[1] - y) * 0.5),
                        tip, (x - side[0] + (tip[0] - x) * 0.5, y - side[1] + (tip[1] - y) * 0.5),
                        (x - side[0] * 0.6, y + 0.8 - side[1] * 0.6)]),
            MUD_LOTUS if a in (-125, -55) else lit(MUD_LOTUS, 0.6), sh=None, lw=0.55)
    cel(draw, Ell(x, y + 0.2, 1.2 * k, 0.9 * k), (255, 224, 100), sh=None, lw=0.5)


def draw_mudslinger(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=1.02, head=1.0)
    d = r.d
    ball_side = _hold_side(r)
    cx = r.cx
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    if d:
        rig_arms(r, draw, MUD_SKIN, MUD_SKIN, layer="far", hands=False)
    # webbed feet on frog legs
    for side in ((-1, 1) if not d else (-d, d)):
        near = r.near(side)
        col = MUD_SKIN if near else shade(MUD_SKIN, 0.6)
        hx0, hy0 = r.hip(side)
        fx, fy = r.foot(side)
        cel(draw, Limb([(hx0, hy0), (fx, fy - 1.0)], [2.6, 2.2]), col, sh=(0.9, 0.0))
        if d:
            foot = Poly([(fx - d * 1.6, fy - 1.6), (fx + d * 3.6, fy - 0.4), (fx + d * 4.4, fy + 1.2), (fx + d * 2.2, fy + 0.6),
                         (fx + d * 1.4, fy + 1.4), (fx - d * 1.8, fy + 1.2)])
        else:
            foot = Poly([(fx - 3.4, fy + 1.2), (fx - 3.0, fy - 0.4), (fx - 1.2, fy - 1.6), (fx + 1.2, fy - 1.6), (fx + 3.0, fy - 0.4),
                         (fx + 3.4, fy + 1.2), (fx + 1.7, fy + 0.6), (fx, fy + 1.4), (fx - 1.7, fy + 0.6)])
        cel(draw, foot, col, sh=(0.0, 0.6))
    # dungarees: a bib and straps over a pale frog belly
    rig_torso(r, draw, MUD_BELLY if r.front else MUD_SKIN)
    if not r.back:
        bx = cx + d * 2.4
        bib = Poly([(bx - 4.6, r.sh_y + 1.6), (bx + 4.6, r.sh_y + 1.6), (bx + 5.2, r.waist_y + 1.0), (bx - 5.2, r.waist_y + 1.0)]) if not d else \
            Poly([(bx - d * 1.4, r.sh_y + 1.6), (bx + d * 2.2, r.sh_y + 1.0), (bx + d * 2.8, r.waist_y + 1.0), (bx - d * 2.4, r.waist_y + 1.0)])
        cel(draw, bib, MUD_DUNG, sh=(0.8, 0.0))
        if not d:
            cel(draw, RRect(bx - 2.0, r.sh_y + 4.0, bx + 2.0, r.sh_y + 6.6, 0.6), shade(MUD_DUNG, 0.6), sh=None, lw=0.6)
    pants = r.hip_y + 2.6
    if d:
        legs = Poly([(cx - 4.6, r.waist_y + 0.4), (cx + 4.8, r.waist_y + 0.4), (cx + 5.4, pants), (cx - 5.2, pants)])
    else:
        legs = Poly([(cx - r.hip_w - 0.2, r.waist_y + 0.4), (cx + r.hip_w + 0.2, r.waist_y + 0.4), (cx + r.hip_w + 0.8, pants),
                     (cx + 0.8, pants), (cx, pants - 1.6), (cx - 0.8, pants), (cx - r.hip_w - 0.8, pants)])
    cel(draw, legs, MUD_DUNG, sh=(1.0, 0.6))
    if not d:
        for s in (-1, 1):
            stroke(draw, [(cx + s * 4.4, r.sh_y + 1.8), (cx + s * 5.0, r.sh_y - 1.8)], 1.3, shade(MUD_DUNG, 0.8))
    if r.back:
        for s in (-1, 1):
            stroke(draw, [(cx + s * 4.6, r.sh_y - 1.6), (cx - s * 3.6, r.waist_y + 0.6)], 1.3, shade(MUD_DUNG, 0.8))
    if d:
        stroke(draw, [(cx + d * 3.6, r.sh_y + 1.8), (cx - d * 1.0, r.sh_y - 2.0)], 1.3, shade(MUD_DUNG, 0.8))
    # mud: splashed up the legs and the bib
    for (u, v, k) in ((-3.4, r.hip_y + 1.0, 1.0), (3.0, r.waist_y + 2.4, 0.8), (-1.2, r.sh_y + 7.2, 0.7)):
        if not r.back or v > r.waist_y:
            _splat(draw, cx + u + d * 1.0, v, k)
    rig_arms(r, draw, MUD_SKIN, MUD_SKIN, layer="near", hands=False)
    # the head: wide and flat, a frog's
    hat_y = hy - ry + 1.4
    _lily_pad(draw, hx - d * 1.6, hat_y, rx + 2.4, 3.8, 112 if not d else 90 + d * 30, -7 if not d else d * 6)
    if not r.back:
        _lotus(draw, hx + (5.6 if not d else -d * 4.6), hat_y - 1.2)
    head = Ell(hx, hy + 0.8, rx + 0.6, ry - 0.4)
    my = face_anchor(r)[4]
    domes = ((-1, hx - 5.8), (1, hx + 5.8)) if not d else ((-d, hx + d * 1.0), (d, hx + d * 7.4))

    def bulges():
        # eyes bulging up over the brow (from behind, two bumps on the crown)
        for side, ex in domes:
            far = d and side == d
            w = 0.74 if far else 1.0
            dy = hy - 7.4
            cel(draw, Ell(ex, dy, 4.6 * w, 4.2), MUD_SKIN if not far else shade(MUD_SKIN, 0.3), sh=(0.5, 0.6))
            if not r.back:
                ms_eye(draw, ex, dy + 0.4, 5.4 * w, 6.2, (64, 44, 30), (float(d), 0.0), "bright", skin=MUD_SKIN,
                       side=side if not d else -side, lash=(40, 70, 30))

    if r.back:
        bulges()
    cel(draw, head, MUD_SKIN, sh=(0.9, 0.8))
    if not r.back:
        cel(draw, Ell(hx + d * 2.6, hy + 6.0, rx * 0.7 if not d else rx * 0.56, 3.6), MUD_BELLY, sh=None, line=False)
        bulges()
    if r.back:
        _lotus(draw, hx + 5.0, hat_y - 1.2)
    else:
        # a frog's grin from cheek to cheek
        mw = 7.0 if not d else 4.6
        mxc = hx + d * 5.4
        stroke(draw, [(mxc - mw, my - 2.6), (mxc - mw * 0.5, my - 0.6), (mxc, my - 0.2), (mxc + mw * 0.5, my - 0.6), (mxc + mw, my - 2.6)],
               0.85, shade(MUD_SKIN, 2.6))
        Ell(mxc + mw * 0.36, my + 0.2, 1.2, 1.0).draw(draw, fill=(232, 98, 118))
        for s in ((-1, 1) if not d else (-d,)):
            Ell(mxc + s * (mw + 0.6) - d * 0.6, my - 3.6, 1.8, 0.9).draw(draw, fill=(240, 150, 140))
    for side in _sides(r):
        h = rig_hand(r, draw, side, MUD_SKIN, reach=0.3 if side == ball_side else 0.0)
        if side == ball_side and not r.back:
            bx, by = h[0] + (1.6 if not d else d * 2.2), h[1] - 3.4
            blob(draw, [Ell(bx, by, 3.6, 3.4), Ell(bx - 1.8, by + 2.8, 1.0, 1.8), Ell(bx + 1.6, by + 2.6, 0.8, 1.5),
                        Ell(bx - 1.8, by + 4.6, 0.8, 0.8)], MUD, sh=(0.9, 0.9), tone=MUD_WET)
            Ell(bx - 1.2, by - 1.3, 1.0, 0.8).draw(draw, fill=lit(MUD, 1.4))


# ===================================================================
# EMBER (25) -- a tiny fire sprite
# ===================================================================

EMB_COLORS = ((244, 96, 40), (255, 172, 60), (255, 238, 150))


def draw_ember(draw, ox, oy, direction, frame):
    bob = [0, -2, -3, -2][frame]
    base = oy + 54 + bob
    cx = ox + 32
    d = {DOWN: 0, UP: 0, LEFT: -1, RIGHT: 1}[direction]
    back = direction == UP
    sway = [0.0, 1.0, 0.0, -1.0][frame]
    by = base - 14.0
    # sparks circling
    for i in range(3):
        a = math.radians(frame * 90 + i * 120)
        sparkle(draw, cx + math.cos(a) * 14.0, by - 2.0 + math.sin(a) * 4.0, 1.6, EMB_COLORS[2])
    # a round flame body, its tip curling up into a lick
    flame(draw, cx - d * 1.0, base - 3.0, 26.0, 36.0, EMB_COLORS, sway=sway * 1.6 - d * 4.0)
    cel(draw, Ell(cx, by, 10.4, 9.6), EMB_COLORS[1], sh=None, line=False)
    cel(draw, Ell(cx + d * 1.4, by + 1.0, 6.8, 6.0), EMB_COLORS[2], sh=None, line=False)
    # little flame hands
    for side in ((-1, 1) if not d else (d,)):
        hx = cx + side * 11.6 if not d else cx + d * 9.4
        hy2 = by + 3.0 + (sway if side > 0 else -sway) * 0.6
        flame(draw, hx, hy2 + 2.4, 4.4, 5.6, EMB_COLORS, sway=(side if not d else d) * 1.2)
    # tiny feet
    for side in (-1, 1):
        cel(draw, Ell(cx + side * 3.4, base - 2.6, 2.2, 1.4), EMB_COLORS[0], sh=None)
    if back:
        return
    # big bright eyes and a little smile
    for side in ((-1, 1) if not d else (d, -d)):
        ex = cx + side * 4.0 + d * 2.0
        w = 1.0 if not d or side == d else 0.7
        ms_eye(draw, ex, by - 0.6, 4.0 * w, 5.2, (150, 60, 30), (float(d), 0.0), "bright", skin=EMB_COLORS[2],
               side=side if not d else -side, lash=(120, 40, 30))
    ms_mouth(draw, cx + d * 3.0, by + 4.2, "open" if frame % 2 else "smile", EMB_COLORS[2], 0.9)
    for side in (-1, 1):
        Ell(cx + side * 7.4 + d * 1.6, by + 2.6, 1.6, 0.8).draw(draw, fill=(255, 140, 110))


# ===================================================================
# AVALANCHE (26) -- a big fluffy yeti: a sky-blue face, a toothy grin,
# little horns, and blue hands and feet out of a snowdrift of fur
# ===================================================================

YETI_FUR = (244, 247, 253)
YETI_SH = (198, 212, 238)
YETI_FACE = (112, 172, 232)
YETI_HORN = (238, 226, 196)
YETI_MOUTH = (96, 38, 78)


def _fluff(cx, cy, rx, ry, tuft=2.4, a0=0.0, a1=360.0, step=30.0, k=0.92):
    """An ellipse with a ring of tufts round it: fur's scalloped edge, all one
    mass once it goes through `blob`."""
    parts = [Ell(cx, cy, rx, ry)]
    a = a0
    while a < a1 - 0.1:
        r = math.radians(a)
        parts.append(Ell(cx + math.cos(r) * rx * k, cy + math.sin(r) * ry * k, tuft, tuft))
        a += step
    return parts


def draw_avalanche(draw, ox, oy, direction, frame):
    d, back, base, cx, ph = creature_setup(ox, oy, direction, frame)
    hx, hy = cx + d * 3.2, base - 31.0
    bw = 13.0 if not d else 12.0

    # feet: big and blue, under the fur
    for s in ((-1, 1) if not d else (-d, d)):
        near = not d or s == d
        fwd = ph * s if not d else (ph if near else -ph)
        lx = cx + (s * 6.0 if not d else d * fwd * 3.4 + s * 1.0)
        ly = base - (1.0 if fwd < 0 and not d else 0.0)
        col = YETI_FUR if near else shade(YETI_FUR, 0.6)
        blob(draw, [Ell(lx, ly - 5.4, 4.2, 4.6)], col, sh=(0.8, 0.8), tone=YETI_SH)
        cel(draw, Ell(lx + d * 1.4, ly - 1.2, 4.4 if not d else 4.8, 2.0), YETI_FACE if near else shade(YETI_FACE, 0.6),
            sh=(0.0, 0.7))

    def arm(s, near):
        col = YETI_FUR if near else shade(YETI_FUR, 0.6)
        fwd = -ph * s if not d else (-ph if near else ph)
        sx = cx + (s * (bw - 2.0) if not d else d * 0.4 + s * 0.6)
        sy = base - 22.0
        fx = sx + (s * 4.6 if not d else d * (fwd * 4.2 + 2.4))
        fy = base - 8.6 - fwd * 0.8
        mid = ((sx + fx) / 2.0 + (s * 1.6 if not d else 0.0), (sy + fy) / 2.0)
        blob(draw, [Limb([(sx, sy), mid, (fx, fy - 2.6)], [4.4, 4.0, 3.6])] +
             [Ell(mid[0] + (s * 3.4 if not d else -d * 3.0), mid[1] + 1.0, 1.8, 1.8)],
             col, sh=(1.0, 0.8), tone=YETI_SH)
        hand = YETI_FACE if near else shade(YETI_FACE, 0.6)
        cel(draw, Ell(fx, fy, 3.6, 3.0), hand, sh=(0.6, 0.6))
        if near:
            for k in ((-1.5, 0.0, 1.5) if not d else (d * 0.2, d * 1.8)):
                Poly([(fx + k - 0.55, fy + 2.0), (fx + k + 0.55, fy + 2.0), (fx + k + (d * 0.4), fy + 3.6)]).draw(
                    draw, fill=(252, 252, 246))

    if d:
        arm(-d, False)
    # the body: a drift of white fur
    by = base - 16.4
    blob(draw, _fluff(cx - d * 0.6, by, bw, 11.0, 2.6, 15.0, 165.0, 25.0) + [Ell(cx - d * 0.6, by - 2.0, bw, 10.0)],
         YETI_FUR, sh=(1.6, 1.4), tone=YETI_SH)
    if not back:
        # the shadow of the head on the chest, fringed like the fur
        bx0 = cx + d * 2.4
        blob(draw, [Ell(bx0, by - 7.6, bw * 0.62 if not d else 5.6, 3.6)] +
             [Ell(bx0 + u, by - 4.4 + (0.8 if i % 2 else 0.0), 1.8, 1.8) for i, u in enumerate((-5.6, -2.8, 0.0, 2.8, 5.6) if not d else (-3.6, -1.0, 1.6, 4.2))],
             mix(YETI_FUR, YETI_SH, 0.6), sh=None, line=False)
    else:
        blob(draw, [Ell(cx, base - 8.0, 2.4, 2.2)], YETI_FUR, sh=(0.4, 0.4), tone=YETI_SH)
    # horns, curling out from under the fur on top of the head
    for s in ((-1, 1) if not d else (-d, d)):
        near = not d or s == -d
        hb = (hx + s * 7.6, hy - 6.4) if not d else (hx + s * 3.4 - d * 1.2, hy - 8.0)
        pts = [hb, (hb[0] + s * 3.4, hb[1] - 1.6), (hb[0] + s * 5.0, hb[1] - 4.6), (hb[0] + s * 4.0, hb[1] - 7.2)]
        cel(draw, Limb(pts, [2.2, 1.8, 1.2, 0.3]), YETI_HORN if near else shade(YETI_HORN, 0.6), sh=(0.4, 0.5))
    # the head: a round mass of fur, a tuft on top
    hrx = 11.0 if not d else 10.2
    blob(draw, _fluff(hx, hy, hrx, 9.6, 2.2, 0.0, 360.0, 30.0) +
         [Ell(hx - d * 1.0 - 1.6, hy - 10.4, 2.6, 2.6), Ell(hx - d * 1.0 + 1.4, hy - 11.2, 2.4, 2.8)],
         YETI_FUR, sh=(1.2, 1.0), tone=YETI_SH)
    if not back:
        # a sky-blue face
        fx0 = hx + d * 3.2
        fw = 7.8 if not d else 6.0
        cel(draw, Poly(arc_pts(fx0, hy + 1.4, fw, 6.8, 0, 360, 32)[:-1]), YETI_FACE, sh=(0.8, 0.8))
        # bangs of fur over the brow
        blob(draw, [Ell(fx0 - fw * 0.55, hy - 4.6, 3.0, 2.4), Ell(fx0, hy - 5.2, 3.2, 2.6), Ell(fx0 + fw * 0.55, hy - 4.6, 3.0, 2.4)],
             YETI_FUR, sh=(0.0, 0.8), tone=YETI_SH)
        ey = hy + 0.8
        if d:
            ms_eye(draw, fx0 + d * 3.0, ey, 2.8, 4.4, (40, 66, 120), (float(d), 0.0), "bright", skin=YETI_FACE, side=d, lash=(30, 44, 90))
            ms_eye(draw, fx0 - d * 1.2, ey, 3.6, 4.8, (40, 66, 120), (float(d), 0.0), "bright", skin=YETI_FACE, side=-d, lash=(30, 44, 90))
        else:
            for s in (-1, 1):
                ms_eye(draw, fx0 + s * 3.4, ey, 3.8, 5.0, (40, 66, 120), (0.0, 0.0), "bright", skin=YETI_FACE, side=s, lash=(30, 44, 90))
        # a big grin with two fangs
        mx, my = fx0 + d * 1.6, hy + 4.4
        mw = 3.8 if not d else 2.8
        Poly([(mx - mw, my), (mx + mw, my)] + arc_pts(mx, my, mw, 3.2, 0, 180, 12)[1:-1]).draw(draw, fill=YETI_MOUTH)
        Ell(mx + d * 0.6, my + 2.2, mw * 0.55, 0.9).draw(draw, fill=(238, 110, 140))
        for s in ((-1, 1) if not d else (d,)):
            tx = mx + s * mw * 0.5 if not d else mx + d * mw * 0.4
            Poly([(tx - 0.8, my - 0.1), (tx + 0.8, my - 0.1), (tx, my + 1.9)]).draw(draw, fill=(252, 252, 246))
        for s in ((-1, 1) if not d else (-d,)):
            Ell(fx0 + s * (fw - 1.4), hy + 3.6, 1.6, 0.8).draw(draw, fill=mix(YETI_FACE, (255, 140, 170), 0.45))
    for s in ((-1, 1) if not d else (d,)):
        arm(s, True)


ELEMENTAL_DRAW_FUNCTIONS = {
    'pyromancer': draw_pyromancer,
    'cryomancer': draw_cryomancer,
    'stormcaller': draw_stormcaller,
    'earthshaker': draw_earthshaker,
    'windwalker': draw_windwalker,
    'magmaknight': draw_magmaknight,
    'frostbite': draw_frostbite,
    'sandstorm': draw_sandstorm,
    'thornweaver': draw_thornweaver,
    'cloudrunner': draw_cloudrunner,
    'inferno': draw_inferno,
    'glacier': draw_glacier,
    'mudslinger': draw_mudslinger,
    'ember': draw_ember,
    'avalanche': draw_avalanche,
}


def main():
    for name, draw_func in ELEMENTAL_DRAW_FUNCTIONS.items():
        generate_character(name, draw_func=draw_func)
    print(f"\nGenerated {len(ELEMENTAL_DRAW_FUNCTIONS)} elemental character sprites.")


if __name__ == "__main__":
    main()
