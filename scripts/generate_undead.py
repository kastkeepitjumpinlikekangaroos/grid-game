#!/usr/bin/env python3
"""Undead/Dark character sprite generators (IDs 27-41).

Fifteen creatures of the night, in MapleStory's style -- spooky-cute rather
than grim. Each has one silhouette nobody else in the category shares: the
necromancer's skull staff, the skeleton king's crown and ermine, the banshee's
streaming hair, the lich's gem cowl, the ghoul's hunch, the reaper's scythe,
the shade's smoke, the revenant's rust and ghost-fire, the gravedigger's
shovel, the dullahan's missing head, the phantom's mask, the mummy's
bandages, the death knight's skull visor, the shadowfiend's horns and wings,
and the poltergeist's sheet and flying crockery.
"""

import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from sprite_base import (
    DOWN, UP, LEFT, RIGHT, GOLD, LEATHER, STEEL, WOOD, SKIN, SKIN_PALE, HAIR_STYLES,
    Ell, Limb, Poly, RRect, arc_pts, blade, blob, cel, cloud, flame, flame_pts, gem,
    generate_character, hand_at, head_face, head_skull, hood_opening, ink, lit, mix, ms_eye,
    ms_mouth, rig, rig_arms, rig_belt, rig_cape, rig_hair, rig_hand, rig_head, rig_hood, rig_legs,
    rig_robe, rig_torso, shade, sparkle, star, stroke, xform, arm_pts, face_anchor,
)

BONE = (240, 234, 214)
VOID = (30, 22, 40)


def _sides(r):
    return (-1, 1) if not r.d else (r.d,)


def _hold_side(r):
    return (1 if not r.back else -1) if not r.d else r.d


def _dir(direction):
    return {DOWN: 0, UP: 0, LEFT: -1, RIGHT: 1}[direction], direction == UP


def skull(draw, x, y, rx, ry, d=0, glow=(255, 70, 70), back=False, bone=BONE, teeth=True):
    """A chibi skull: a big round cranium over a short jaw, huge dark sockets
    with a glowing pupil in each."""
    jaw = RRect(x - rx * 0.62 + d * 1.4, y + ry * 0.35, x + rx * 0.62 + d * 1.4, y + ry * 1.02, rx * 0.3)
    blob(draw, [Ell(x, y - ry * 0.1, rx, ry * 0.92), jaw], bone, sh=(1.2, 1.0))
    if back:
        return
    if d:
        sx = [(x + d * rx * 0.34, 1.0), (x + d * rx * 0.86, 0.6)]
    else:
        sx = [(x - rx * 0.4, 1.0), (x + rx * 0.4, 1.0)]
    for (ex, w) in sx:
        Ell(ex, y + ry * 0.1, rx * 0.3 * w, ry * 0.34).draw(draw, fill=VOID)
        if glow is not None:
            Ell(ex + d * 0.3, y + ry * 0.14, rx * 0.12 * w + 0.3, ry * 0.12 + 0.2).draw(draw, fill=glow)
            Ell(ex + d * 0.3 - 0.3, y + ry * 0.06, 0.45, 0.45).draw(draw, fill=(255, 255, 255))
    nx = x + d * rx * 0.62
    Poly([(nx - 0.9, y + ry * 0.56), (nx + 0.9, y + ry * 0.56), (nx, y + ry * 0.4)]).draw(draw, fill=VOID)
    if teeth:
        jx = x + d * 1.8
        n = 4 if not d else 3
        for i in range(n):
            tx = jx - (n - 1) * 1.1 + i * 2.2
            stroke(draw, [(tx, y + ry * 0.72), (tx, y + ry * 0.98)], 0.5, shade(bone, 1.8))
        stroke(draw, [(jx - n * 1.1, y + ry * 0.74), (jx + n * 1.1, y + ry * 0.74)], 0.5, shade(bone, 1.8))


def ribcage(draw, x, y, w, h, bone=BONE, back=False):
    cel(draw, RRect(x - w, y, x + w, y + h, w * 0.5), VOID, sh=None, line=False)
    stroke(draw, [(x, y + 0.4), (x, y + h)], 0.9, bone)
    for i in range(3):
        yy = y + 1.2 + i * (h - 1.6) / 3.0
        ww = w * (1.0 - i * 0.12)
        stroke(draw, [(x - ww, yy + 1.0), (x - ww * 0.4, yy), (x, yy + 0.3), (x + ww * 0.4, yy), (x + ww, yy + 1.0)],
               0.8, bone)


def bone_arm(draw, a, b, color=BONE, width=1.0):
    cel(draw, Limb([a, b], [width, width]), color, sh=None, lw=0.8)
    for p in (a, b):
        cel(draw, Ell(p[0], p[1], width * 1.4, width * 1.4), color, sh=None, lw=0.8)


# ===================================================================
# NECROMANCER (27) -- pale, hooded, a skull staff with soul fire
# ===================================================================

NEC_ROBE = (88, 54, 124)
NEC_SOUL = (132, 255, 150)
NEC_SKIN = (232, 226, 236)
SOUL_FIRE = ((70, 200, 110), (132, 255, 150), (226, 255, 226))


def draw_necromancer(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame)
    d = r.d
    st_side = _hold_side(r)
    out = 1.6 if not d else 0.4

    def staff():
        h = hand_at(r, st_side, 0.0, 0.0, out)
        x = h[0] + 0.2
        top = r.head_cy - 2.0
        cel(draw, Limb([(x, r.base_y - 1.2), (x, top + 4.0)], [1.0, 0.9]), (74, 58, 70), sh=None)
        flame(draw, x, top - 1.6, 7.0, 10.0 + [0, 1, 0, 1][frame], SOUL_FIRE, sway=[0, 1, 0, -1][frame])
        skull(draw, x, top + 1.6, 3.6, 3.4, glow=NEC_SOUL, teeth=False)

    if r.back:
        staff()
    if d:
        rig_arms(r, draw, NEC_ROBE, NEC_SKIN, layer="far")
    rig_legs(r, draw, shade(NEC_ROBE, 1.4), (60, 44, 70))
    rig_robe(r, draw, NEC_ROBE, trim=BONE, flare=3.4)
    if not r.back:
        # a ribcage clasp on the chest
        cx = r.cx + d * 1.8
        for i in range(3):
            stroke(draw, [(cx - 3.0 + i * 0.3, r.sh_y + 1.0 + i * 1.6), (cx + 3.0 - i * 0.3, r.sh_y + 1.0 + i * 1.6)], 0.7, BONE)
        stroke(draw, [(cx, r.sh_y + 0.4), (cx, r.sh_y + 5.2)], 0.8, BONE)
    rig_belt(r, draw, shade(NEC_ROBE, 1.8), buckle=NEC_SOUL)
    rig_arms(r, draw, NEC_ROBE, NEC_SKIN, layer="near", hands=False)
    rig_head(r, draw, NEC_SKIN, hair=(62, 50, 74), style="swept", eye_color=(60, 200, 110), expression="smirk",
             mood="sharp", blush=False)
    if not r.back:
        e1, e2, ey, _, _ = face_anchor(r)
        for ex in ((e1, e2) if not d else (e1,)):
            stroke(draw, [(ex - 1.8, ey + 3.4), (ex + 1.8, ey + 3.4)], 0.5, shade(NEC_SKIN, 1.4))
    rig_hood(r, draw, NEC_ROBE, peak=2.4)
    if not r.back:
        staff()
    for side in _sides(r):
        rig_hand(r, draw, side, NEC_SKIN, out=out if side == st_side else 0.0)
    # souls drifting round him
    for i in range(2):
        a = math.radians(frame * 90 + i * 180 + 40)
        x = r.cx + math.cos(a) * 14.0
        y = r.hip_y - 2.0 + math.sin(a) * 2.6
        cel(draw, Ell(x, y, 1.8, 1.8), NEC_SOUL, sh=None, lw=0.6)
        Ell(x - 0.5, y - 0.5, 0.6, 0.6).draw(draw, fill=(255, 255, 255))


# ===================================================================
# SKELETON KING (28) -- crown, ermine-trimmed cape, bone axe
# ===================================================================

SK_CAPE = (178, 34, 48)
SK_ERMINE = (250, 248, 244)
SK_GLOW = (255, 64, 60)


def draw_skeletonking(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=0.92)
    d = r.d
    ax_side = _hold_side(r)
    rig_cape(r, draw, SK_CAPE, layer="under")
    # bone legs and arms
    for side in ((-1, 1) if not d else (-d, d)):
        hx0, hy0 = r.hip(side)
        fx, fy = r.foot(side)
        bone_arm(draw, (hx0, hy0 + 1.0), (fx, fy - 1.4), BONE if r.near(side) else shade(BONE, 0.6))
        cel(draw, Ell(fx + d * 0.8, fy, 2.4, 1.4), BONE if r.near(side) else shade(BONE, 0.6), sh=None, lw=0.8)
    if d:
        s, e, w, h = arm_pts(r, -d)
        bone_arm(draw, s, h, shade(BONE, 0.6))
    # ribcage and pelvis
    cx = r.cx
    cel(draw, RRect(cx - 4.4, r.hip_y - 3.0, cx + 4.4, r.hip_y + 0.6, 1.6), BONE, sh=(0.6, 0.6))
    if d:
        cel(draw, RRect(cx - 3.6, r.sh_y - 2.4, cx + 4.0, r.waist_y + 1.0, 2.0), BONE, sh=(0.8, 0.6))
    else:
        cel(draw, RRect(cx - 5.4, r.sh_y - 2.4, cx + 5.4, r.waist_y + 1.0, 2.6), BONE, sh=(0.8, 0.6))
        ribcage(draw, cx, r.sh_y - 1.4, 4.0, 6.4)
    # gold belt with a ruby
    cel(draw, RRect(cx - 5.2, r.waist_y + 0.8, cx + 5.2, r.waist_y + 2.6, 0.6) if not d else
        RRect(cx - 3.8, r.waist_y + 0.8, cx + 4.2, r.waist_y + 2.6, 0.6), GOLD, sh=(0.0, 0.5))
    for side in _sides(r):
        s, e, w, h = arm_pts(r, side)
        bone_arm(draw, s, h)
    # ermine mantle over the shoulders
    blob(draw, [Ell(cx - 5.0, r.sh_y - 1.6, 4.4, 2.8), Ell(cx, r.sh_y - 1.0, 4.6, 2.6), Ell(cx + 5.0, r.sh_y - 1.6, 4.4, 2.8)]
         if not d else [Ell(cx, r.sh_y - 1.4, 6.0, 2.8)], SK_ERMINE, sh=(0.6, 0.8), tone=(210, 212, 226))
    for (u, v) in ((-5.0, -1.2), (0.0, -0.4), (5.0, -1.2)) if not d else ((d * 1.0, -1.0),):
        Poly([(cx + u, r.sh_y + v - 0.6), (cx + u + 0.6, r.sh_y + v + 0.8), (cx + u - 0.6, r.sh_y + v + 0.8)]).draw(draw, fill=VOID)
    # skull and crown
    hx, hy = r.hx, r.head_cy + 0.6
    skull(draw, hx, hy, 10.8, 10.4, d, glow=SK_GLOW, back=r.back)
    cy0 = hy - 9.0
    pts = [(hx - 8.0, cy0 + 2.0)]
    for i in range(5):
        t = i / 4.0
        x = hx - 8.0 + 16.0 * t
        pts += [(x - 1.2, cy0 - 1.0), (x, cy0 - 5.0 - (1.2 if i == 2 else 0.0)), (x + 1.2, cy0 - 1.0)] if i not in (0, 4) else \
            [(x, cy0 - 4.0)]
    pts += [(hx + 8.0, cy0 + 2.0)]
    cel(draw, Poly(pts), GOLD, sh=(0.8, 0.6), hi=(0.5, 0.5))
    if not r.back:
        gem(draw, hx + d * 2.0, cy0 - 0.2, 1.3, SK_GLOW)
    rig_cape(r, draw, SK_CAPE, layer="over")
    # a bone axe
    h = hand_at(r, ax_side)
    ang = -70 if not d else (-60 if d > 0 else -120)
    if not d:
        ang = -90 + ax_side * 24
    x1, y1 = xform([(12.0, 0.0)], h[0], h[1], ang)[0]
    cel(draw, Limb([xform([(-3.0, 0.0)], h[0], h[1], ang)[0], (x1, y1)], [1.0, 1.0]), BONE, sh=None)
    head = xform([(8.0, 0.6), (13.6, 0.4), (14.2, 6.6), (11.4, 7.6), (8.6, 4.0)], h[0], h[1], ang, 1.0,
                 1.0 if (ax_side > 0 or d > 0) else -1.0)
    cel(draw, Poly(head), (206, 212, 226), sh=None, regions=[(Poly(head[2:] + head[:1]), shade((206, 212, 226), 0.8))])
    cel(draw, Ell(h[0], h[1], 1.9, 1.9), BONE, sh=None, lw=0.8)


# ===================================================================
# BANSHEE (29) -- a wailing ghost woman with streaming hair
# ===================================================================

BAN_BODY = (140, 144, 204)
BAN_HAIR = (244, 244, 255)
BAN_GLOW = (170, 220, 255)


def draw_banshee(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, bob=[0, -1, -2, -1][frame], step=0, head=0.9)
    d = r.d
    wave = [0.0, 1.0, 0.0, -1.0][frame]
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    ph = frame * math.pi / 2.0
    # long hair floating out and down around her, as if underwater: every
    # lock starts at the head and falls, curling at the tip -- never up
    if d:
        locks = [(-0.2, 4.0, 15.0), (0.2, 8.0, 18.0), (0.6, 12.0, 14.0)]
        for i, (a, drop, reach) in enumerate(locks):
            x0, y0 = hx - d * 3.0, hy - 2.0 + i * 3.0
            pts = [(x0, y0), (x0 - d * reach * 0.5, y0 + drop * 0.4 + math.sin(ph + i) * 1.2),
                   (x0 - d * reach, y0 + drop + math.sin(ph + i + 1.0) * 1.4),
                   (x0 - d * (reach - 2.4), y0 + drop + 3.4)]
            cel(draw, Limb(pts, [3.6, 3.2, 2.0, 0.5]), BAN_HAIR, sh=(0.6, 0.8), tone=shade(BAN_HAIR, 1.2))
    else:
        for s in (-1, 1):
            for i, (drop, reach) in enumerate(((6.0, 15.4), (13.0, 14.0), (19.0, 10.0))):
                x0, y0 = hx + s * 6.0, hy - 1.0 + i * 3.0
                pts = [(x0, y0), (x0 + s * reach * 0.55, y0 + drop * 0.35 + math.sin(ph + i + s) * 1.2),
                       (x0 + s * reach, y0 + drop + math.sin(ph + i + s + 1.0) * 1.4),
                       (x0 + s * (reach - 2.6), y0 + drop + 3.4)]
                cel(draw, Limb(pts, [3.6, 3.2, 2.0, 0.5]), BAN_HAIR, sh=(0.6, 0.8), tone=shade(BAN_HAIR, 1.2))
    # a gown that frays into a wisp instead of legs
    cx = r.cx
    top = r.sh_y - 2.4
    sway = wave * 1.6
    if d:
        gown = Poly([(cx - d * 3.4, top), (cx + d * 3.0, top), (cx + d * 5.2, r.hip_y), (cx + d * 2.0 + sway, r.base_y - 3.0),
                     (cx - d * 3.0 + sway, r.base_y + 0.6), (cx - d * 7.0 + sway, r.base_y - 4.0), (cx - d * 5.0, r.hip_y)])
    else:
        gown = Poly([(cx - r.sh_w + 1.6, top), (cx + r.sh_w - 1.6, top), (cx + r.sh_w, r.sh_y + 0.6),
                     (cx + r.sh_w + 3.6, r.hip_y + 2.0), (cx + 3.0 + sway, r.base_y - 3.4), (cx + sway * 1.4, r.base_y + 0.6),
                     (cx - 3.0 + sway, r.base_y - 3.4), (cx - r.sh_w - 3.6, r.hip_y + 2.0), (cx - r.sh_w, r.sh_y + 0.6)])
    cel(draw, gown, BAN_BODY, sh=(1.6, 1.0), tone=shade(BAN_BODY, 1.2))
    # arms raised to her face, wailing
    for side in ((-1, 1) if not d else (d,)):
        rig_arms(r, draw, BAN_BODY, (226, 230, 250), sides=(side,), reach=1.0, hands=True)
    face_col = (226, 230, 250)
    head_skull(r, draw, face_col, ears=False)
    if not r.back:
        head_face(r, draw, face_col, iris=(80, 110, 190), mood="closed", mouth="open", blush=False, brows=True,
                  brow_color=(120, 130, 190), tilt=-0.6)
        # tears streaming down
        e1, e2, ey, _, _ = face_anchor(r)
        for ex in ((e1, e2) if not d else (e1,)):
            k = [0.0, 1.0, 2.0, 1.0][frame]
            cel(draw, Poly([(ex - 0.6, ey + 2.2), (ex + 0.6, ey + 2.2), (ex + 0.8, ey + 4.0 + k), (ex, ey + 4.8 + k),
                            (ex - 0.8, ey + 4.0 + k)]), BAN_GLOW, sh=None, lw=0.4)
    rig_hair(r, draw, BAN_HAIR, dict(HAIR_STYLES["long"], side=9.0), skin=face_col)
    # sound rings off her wail
    if not r.back and frame % 2 == 1:
        mx = r.hx + d * 7.0
        for k, rad in enumerate((3.0, 5.4)):
            pts = arc_pts(mx + (d * 3.0 if d else 0), hy + 7.4 + (0 if d else 5.0), rad, rad * 0.7,
                          (-40 if d > 0 else 140) if d else 40, (40 if d > 0 else 220) if d else 140, 8)
            stroke(draw, pts, 0.6, BAN_GLOW)


# ===================================================================
# LICH (30) -- a skull under a gem cowl, navy robes, a soul gem staff
# ===================================================================

LICH_ROBE = (44, 58, 104)
LICH_GLOW = (110, 214, 255)


def draw_lich(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=0.95)
    d = r.d
    st_side = _hold_side(r)
    out = 1.6 if not d else 0.4

    def staff():
        h = hand_at(r, st_side, 0.0, 0.0, out)
        x = h[0] + 0.2
        top = r.head_cy - 3.0
        cel(draw, Limb([(x, r.base_y - 1.2), (x, top + 2.6)], [1.0, 0.9]), (80, 70, 90), sh=None)
        cel(draw, Poly([(x - 3.4, top - 2.0), (x - 1.0, top + 3.2), (x + 1.0, top + 3.2), (x + 3.4, top - 2.0),
                        (x + 2.2, top + 0.4), (x, top - 0.6), (x - 2.2, top + 0.4)]), GOLD, sh=None)
        from sprite_base import crystal
        crystal(draw, x, top + 1.0, -90, 7.0, 4.4, color=LICH_GLOW)
        if frame % 2 == 0:
            sparkle(draw, x + 3.6, top - 5.0, 1.8, (220, 250, 255))

    if r.back:
        staff()
    if d:
        rig_arms(r, draw, LICH_ROBE, BONE, layer="far")
    rig_legs(r, draw, shade(LICH_ROBE, 1.0), (40, 40, 60))
    rig_robe(r, draw, LICH_ROBE, trim=GOLD, flare=3.8)
    if not r.back:
        cx = r.cx + d * 1.8
        cel(draw, Poly([(cx - 2.4, r.sh_y - 1.8), (cx + 2.4, r.sh_y - 1.8), (cx + 3.4, r.base_y - 3.0), (cx - 3.4, r.base_y - 3.0)]),
            (70, 90, 150), sh=(0.6, 0.0))
        gem(draw, cx, r.sh_y + 1.4, 1.5, LICH_GLOW)
    rig_arms(r, draw, LICH_ROBE, BONE, layer="near", hands=False)
    skull(draw, r.hx, r.head_cy + 1.0, 10.0, 9.6, d, glow=LICH_GLOW, back=r.back)
    rig_hood(r, draw, LICH_ROBE, peak=0.0, opening=1.06)
    # gold circlet with a gem, standing up off the cowl
    hx, hy, ry = r.hx, r.head_cy, r.head_ry
    if not r.back:
        cx0 = hx + d * 2.0
        cel(draw, Poly([(cx0 - 6.4, hy - ry + 0.8), (cx0 - 3.4, hy - ry - 3.0), (cx0, hy - ry - 5.4), (cx0 + 3.4, hy - ry - 3.0),
                        (cx0 + 6.4, hy - ry + 0.8), (cx0 + 4.0, hy - ry + 2.0), (cx0, hy - ry - 0.6), (cx0 - 4.0, hy - ry + 2.0)]),
            GOLD, sh=(0.5, 0.5))
        gem(draw, cx0, hy - ry - 1.4, 1.6, LICH_GLOW)
    if not r.back:
        staff()
    for side in _sides(r):
        h = hand_at(r, side, 0.0, 0.0, out if side == st_side else 0.0)
        bone_arm(draw, (h[0], h[1] - 1.0), (h[0], h[1] + 0.6), BONE if r.near(side) else shade(BONE, 0.6), 1.2)


# ===================================================================
# GHOUL (31) -- hunched, grey-green, a mouthful of jagged teeth
# ===================================================================

GH_SKIN = (150, 176, 140)
GH_RAGS = (116, 92, 76)
GH_GLOW = (255, 72, 60)


def draw_ghoul(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=1.08, head=0.96)
    d = r.d
    # hunched: the head hangs lower and further forward
    r.head_cy += 2.4
    r.hx += d * 2.4
    if d:
        rig_arms(r, draw, GH_SKIN, GH_SKIN, layer="far", reach=0.4, hands=False)
    rig_legs(r, draw, GH_SKIN, GH_SKIN, bare=True)
    rig_torso(r, draw, GH_RAGS)
    cx = r.cx
    # torn hem
    if not d:
        Poly([(cx - r.hip_w, r.hip_y + 0.4), (cx - 3.0, r.hip_y + 3.0), (cx - 1.0, r.hip_y + 0.8), (cx + 2.0, r.hip_y + 3.4),
              (cx + r.hip_w, r.hip_y + 0.4)]).draw(draw, fill=GH_RAGS)
        stroke(draw, [(cx - 3.0, r.sh_y + 2.0), (cx - 1.0, r.sh_y + 4.0)], 0.6, shade(GH_RAGS, 1.4))
    rig_arms(r, draw, GH_SKIN, GH_SKIN, layer="near", reach=0.4, hands=False)
    # long clawed hands
    for side in ((-1, 1) if not d else (-d, d)):
        h = hand_at(r, side, 0.4)
        col = GH_SKIN if r.near(side) else shade(GH_SKIN, 0.6)
        cel(draw, Ell(h[0], h[1], 2.4, 2.2), col, sh=(0.5, 0.5))
        for k in (-1, 0, 1):
            x = h[0] + k * 1.3 + d * 0.6
            Poly([(x - 0.5, h[1] + 1.2), (x + 0.5, h[1] + 1.2), (x + k * 0.3 + d * 0.6, h[1] + 3.8)]).draw(draw, fill=(240, 236, 220))
    head_skull(r, draw, GH_SKIN, ears=False)
    hx, hy, rx = r.hx, r.head_cy, r.head_rx
    # ragged ears
    for s in ((-1, 1) if not d else (-d,)):
        ex = hx + s * (rx - 0.4) if not d else hx - d * 3.0
        cel(draw, Poly([(ex, hy - 1.0), (ex + (s * 3.4 if not d else -d * 3.0), hy - 3.4), (ex + (s * 1.4 if not d else -d * 1.0), hy + 2.6)]),
            GH_SKIN, sh=(0.3, 0.3))
    if not r.back:
        e1, e2, ey, mx, my = face_anchor(r)
        for side, ex in (((-1, e1), (1, e2)) if not d else ((-d, e1), (d, e2))):
            w = 1.0 if not d or side == -d else 0.7
            Ell(ex, ey, 2.4 * w, 2.4).draw(draw, fill=VOID)
            Ell(ex, ey + 0.2, 1.1 * w, 1.1).draw(draw, fill=GH_GLOW)
            Ell(ex - 0.3, ey - 0.3, 0.45, 0.45).draw(draw, fill=(255, 255, 255))
        # a wide jagged mouth
        mcx = hx + d * 5.0
        mw = 5.4 if not d else 3.6
        Poly([(mcx - mw, my - 1.4), (mcx + mw, my - 1.4), (mcx + mw * 0.7, my + 1.8), (mcx - mw * 0.7, my + 1.8)]).draw(draw, fill=(90, 30, 40))
        n = 5 if not d else 3
        for i in range(n):
            tx = mcx - mw + (2 * mw) * (i + 0.5) / n
            Poly([(tx - 0.8, my - 1.4), (tx + 0.8, my - 1.4), (tx, my + 0.4)]).draw(draw, fill=(246, 240, 222))
    # a few lank strands
    rig_hair(r, draw, (84, 96, 80), dict(vol=0.9, fringe=((-0.5, 3.4), (0.3, 4.4)), sweep=0.6, side=4.0, back=6.0,
                                           spikes=(4, 1.6)), skin=GH_SKIN)


# ===================================================================
# REAPER (32) -- a black hood, a skull in shadow, an enormous scythe
# ===================================================================

REAP_CLOAK = (46, 42, 58)
REAP_GLOW = (255, 60, 70)


def draw_reaper(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, bob=[0, -1, -1, 0][frame], step=0)
    d = r.d
    sc_side = _hold_side(r)
    sway = [0.0, 0.8, 0.0, -0.8][frame]

    def scythe():
        h = hand_at(r, sc_side, 0.2, 2.0, 1.0 if not d else 0.0)
        x0, y0 = h[0] + (0.0 if not d else 0.0), r.base_y - 1.0
        top = (h[0] + (sc_side * 1.4 if not d else d * 3.0), r.head_cy - 8.0)
        cel(draw, Limb([(x0 - (sc_side * 1.0 if not d else d * 1.6), y0), top], [1.1, 1.0]), (120, 88, 64), sh=(0.5, 0.0))
        dirn = -sc_side if not d else d
        blade_pts = [top, (top[0] + dirn * 6.0, top[1] - 3.4), (top[0] + dirn * 13.0, top[1] - 1.2),
                     (top[0] + dirn * 17.4, top[1] + 4.0), (top[0] + dirn * 12.0, top[1] + 1.4),
                     (top[0] + dirn * 5.4, top[1] + 1.6), (top[0], top[1] + 2.4)]
        cel(draw, Poly(blade_pts), (214, 220, 234), sh=None,
            regions=[(Poly(blade_pts[3:] + [top]), shade((214, 220, 234), 0.9))])
        stroke(draw, [(top[0] + dirn * 4.0, top[1] - 1.6), (top[0] + dirn * 12.0, top[1] - 0.4)], 0.5, (255, 255, 255))
        cel(draw, Ell(top[0], top[1] + 1.0, 1.6, 1.6), (150, 150, 170), sh=None)

    if r.back:
        scythe()
    cx = r.cx
    top = r.sh_y - 2.4
    hem = r.base_y - 0.8
    if d:
        pts = [(cx - d * 3.4, top), (cx + d * 3.0, top), (cx + d * 5.4, r.hip_y), (cx + d * 6.0 + sway, hem)]
        for i in range(4):
            t = (i + 0.5) / 4.0
            pts += [(cx + d * (6.0 - 14.0 * t) + sway, hem + (1.2 if i % 2 == 0 else -1.6))]
        pts += [(cx - d * 8.4 + sway, hem - 2.0), (cx - d * 5.0, r.hip_y)]
    else:
        w = r.sh_w
        pts = [(cx - w + 1.8, top), (cx + w - 1.8, top), (cx + w, r.sh_y + 0.6), (cx + w + 5.0 + sway, hem)]
        for i in range(1, 6):
            t = i / 6.0
            pts.append((cx + w + 5.0 + sway - (2 * w + 10.0) * t, hem + (1.2 if i % 2 else -1.8)))
        pts += [(cx - w - 5.0 + sway, hem), (cx - w, r.sh_y + 0.6)]
    cel(draw, Poly(pts), REAP_CLOAK, sh=(1.8, 1.0))
    if d:
        rig_arms(r, draw, REAP_CLOAK, BONE, layer="far", hands=False)
    rig_arms(r, draw, REAP_CLOAK, BONE, layer="near", hands=False, reach=0.2)
    skull(draw, r.hx + d * 1.0, r.head_cy + 1.4, 8.6, 8.4, d, glow=REAP_GLOW, back=r.back)
    rig_hood(r, draw, REAP_CLOAK, peak=4.0, opening=0.96)
    if not r.back:
        scythe()
    for side in _sides(r):
        h = hand_at(r, side, 0.2 if side != sc_side else 0.2, 2.0 if side == sc_side else 0.0,
                    (1.0 if not d else 0.0) if side == sc_side else 0.0)
        col = BONE if r.near(side) else shade(BONE, 0.6)
        cel(draw, Ell(h[0], h[1], 1.8, 1.8), col, sh=None, lw=0.8)
        for k in (-1, 0, 1):
            stroke(draw, [(h[0] + k * 1.0, h[1] + 1.0), (h[0] + k * 1.2, h[1] + 2.6)], 0.5, col)


# ===================================================================
# SHADE (33) -- a living shadow: smoke, two lilac eyes, a grin
# ===================================================================

SHADE_BODY = (48, 34, 72)
SHADE_EDGE = (86, 62, 124)
SHADE_GLOW = (214, 164, 255)


def draw_shade(draw, ox, oy, direction, frame):
    d, back = _dir(direction)
    bob = [0, -1, -2, -1][frame]
    base = oy + 54 + bob
    cx = ox + 32
    wave = [0.0, 1.0, 0.0, -1.0][frame]
    # wisps rising off it
    for i, (u, h) in enumerate(((-9.0, 9.0), (-3.0, 12.0), (4.0, 10.0), (9.4, 8.0))):
        flame(draw, cx + u - d * 2.0, base - 32.0 + (i % 2) * 2.0, 6.0, h, (SHADE_BODY, SHADE_EDGE, SHADE_EDGE),
              sway=wave * (1 if i % 2 else -1) - d * 3.0, line=True)
    # the body: a smoky mass narrowing to a tail
    parts = [Ell(cx, base - 24.0, 12.4, 11.0), Ell(cx - d * 1.0, base - 13.0, 8.6, 7.6),
             Ell(cx + wave - d * 2.0, base - 6.0, 5.0, 4.2), Ell(cx + wave * 1.6 - d * 3.0, base - 2.0, 2.6, 2.4)]
    blob(draw, parts, SHADE_BODY, sh=(1.8, 1.4), tone=shade(SHADE_BODY, 0.8),
         regions=[(Ell(cx - 5.0, base - 29.0, 5.0, 3.4), SHADE_EDGE)])
    # tendril arms
    for side in ((-1, 1) if not d else (d,)):
        sx = cx + side * 9.0 if not d else cx + d * 6.0
        end = (sx + (side * 6.0 if not d else d * 6.0), base - 14.0 + wave * side)
        cel(draw, Limb([(sx, base - 20.0), (sx + (side * 4.0 if not d else d * 4.0), base - 18.0 - wave * side), end],
                       [2.6, 2.0, 0.5]), SHADE_BODY, sh=(0.6, 0.6), tone=shade(SHADE_BODY, 0.8))
    if back:
        return
    fx = cx + d * 4.0
    for side, ex in (((-1, fx - 4.6), (1, fx + 4.6)) if not d else ((d, fx + d * 3.0), (-d, fx - d * 2.6))):
        w = 1.0 if not d or side == d else 0.66
        Ell(ex, base - 26.0, 2.8 * w, 3.6).draw(draw, fill=SHADE_GLOW)
        Ell(ex - 0.6 * w, base - 26.8, 1.0 * w, 1.1).draw(draw, fill=(255, 255, 255))
    # a crescent grin
    mx = fx + d * 1.0
    pts = arc_pts(mx, base - 21.6, 5.0 if not d else 3.6, 2.6, 10, 170, 10) + \
        arc_pts(mx, base - 21.0, 4.0 if not d else 2.8, 1.4, 170, 10, 10)
    Poly(pts).draw(draw, fill=SHADE_GLOW)


# ===================================================================
# REVENANT (34) -- rusted armour, a tattered cape, teal ghost-fire
# ===================================================================

REV_RUST = (170, 112, 76)
REV_ARMOR = (126, 138, 132)
REV_GHOST = (96, 240, 214)
REV_CAPE = (78, 90, 96)
GHOST_FIRE = ((50, 170, 160), (96, 240, 214), (220, 255, 246))


def draw_revenant(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=1.14)
    d = r.d
    sw_side = _hold_side(r)
    sway = [0.0, 1.0, 0.0, -1.0][frame]
    rig_cape(r, draw, REV_CAPE, layer="under")
    if d:
        rig_arms(r, draw, REV_ARMOR, REV_ARMOR, layer="far")
    rig_legs(r, draw, REV_ARMOR, shade(REV_RUST, 1.2), width=1.08)
    rig_torso(r, draw, REV_ARMOR)
    cx = r.cx
    # rust bloom and dents on the plate, and a glowing wound
    if not r.back:
        for (u, v, k) in ((-3.0, 1.4, 1.6), (2.4, 4.6, 1.2), (-1.4, 6.4, 1.0)):
            blob(draw, [Ell(cx + u + d * 1.4, r.sh_y + v, k * 1.3, k)], REV_RUST, sh=None, line=False)
        cel(draw, Ell(cx + d * 2.2 + 2.0, r.sh_y + 2.2, 1.2, 1.6), REV_GHOST, sh=None, line_color=shade(REV_ARMOR, 1.6))
    rig_belt(r, draw, shade(REV_RUST, 1.4), buckle=REV_ARMOR)
    rig_arms(r, draw, REV_ARMOR, REV_ARMOR, layer="near", hands=False)
    for side in _sides(r):
        sx, sy = r.shoulder(side)
        cel(draw, Ell(sx + (side * 1.0 if not d else 0), sy - 1.0, 4.2, 2.8), REV_ARMOR, sh=(0.6, 0.6),
            regions=[(Ell(sx + (side * 2.0 if not d else 1.0), sy - 1.4, 1.6, 1.2), REV_RUST)])
    # ghost-fire where the neck should be, a battered bucket helm on it
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    flame(draw, hx - d * 1.0, hy - 4.0, 18.0, 16.0, GHOST_FIRE, sway=sway - d * 3.0)
    helm = Poly([(hx - rx + 0.6, hy - ry + 3.6), (hx - rx * 0.4, hy - ry + 0.6), (hx + rx * 0.4, hy - ry + 0.6),
                 (hx + rx - 0.6, hy - ry + 3.6), (hx + rx - 0.2, hy + ry - 2.0), (hx - rx + 0.2, hy + ry - 2.0)])
    cel(draw, helm, REV_ARMOR, sh=(1.6, 1.2), hi=(0.8, 0.8),
        regions=[(Ell(hx - rx * 0.5, hy + ry * 0.4, 2.6, 2.0), REV_RUST), (Ell(hx + rx * 0.4, hy - ry * 0.4, 1.8, 1.4), REV_RUST)])
    if not r.back:
        vx = hx + d * 3.0
        w = rx - 2.4 if not d else rx * 0.6
        cel(draw, RRect(vx - w, hy - 0.4, vx + w, hy + 2.6, 1.0), VOID, sh=None)
        for s in ((-1, 1) if not d else (d,)):
            ex = vx + s * 3.6 if not d else vx + d * 2.4
            Ell(ex, hy + 1.1, 1.8, 1.0).draw(draw, fill=REV_GHOST)
        stroke(draw, [(vx, hy + 2.8), (vx, hy + ry - 2.6)], 0.8, shade(REV_ARMOR, 1.6))
    rig_cape(r, draw, REV_CAPE, layer="over")
    # a notched sword burning with ghost-fire
    h = hand_at(r, sw_side)
    ang = 90 + sw_side * 26 if not d else 90 - d * 40
    blade(draw, h[0], h[1], ang, length=13.0, width=1.8, color=(186, 200, 196), hilt=REV_RUST, grip=(70, 60, 60),
          guard=2.8, flip=sw_side if not d else -d)
    tip = xform([(13.0, 0.0)], h[0], h[1], ang)[0]
    flame(draw, tip[0], tip[1] + 1.4, 4.6, 6.0, GHOST_FIRE, sway=sway)
    for side in _sides(r):
        rig_hand(r, draw, side, REV_ARMOR)


# ===================================================================
# GRAVEDIGGER (35) -- battered hat, patched coat, a big shovel
# ===================================================================

GD_COAT = (122, 94, 70)
GD_HAT = (84, 76, 80)
GD_PATCH = (170, 140, 96)
GD_SKIN = (238, 214, 190)


def draw_gravedigger(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=1.06)
    d = r.d
    sh_side = _hold_side(r)
    out = 1.8 if not d else 0.4

    def shovel():
        h = hand_at(r, sh_side, 0.0, 0.0, out)
        x = h[0] + 0.2
        top = r.head_cy + 2.0
        cel(draw, Limb([(x, r.base_y - 5.0), (x, top)], [1.0, 1.0]), WOOD, sh=(0.4, 0.0))
        cel(draw, Poly([(x - 2.4, top - 2.4), (x + 2.4, top - 2.4), (x + 2.4, top), (x - 2.4, top)]), WOOD, sh=None)
        bladep = Poly([(x - 3.4, r.base_y - 6.0), (x + 3.4, r.base_y - 6.0), (x + 3.2, r.base_y - 1.6), (x, r.base_y + 0.6),
                       (x - 3.2, r.base_y - 1.6)])
        cel(draw, bladep, (176, 182, 196), sh=(0.8, 0.6), hi=(0.5, 0.5))
        Ell(x - 1.0, r.base_y - 3.0, 1.0, 0.8).draw(draw, fill=(130, 100, 70))

    if r.back:
        shovel()
    if d:
        rig_arms(r, draw, GD_COAT, (150, 120, 90), layer="far")
    rig_legs(r, draw, (86, 80, 90), (70, 54, 44))
    rig_robe(r, draw, GD_COAT, flare=2.4, hem=r.hip_y + 4.0, split=True)
    if not r.back:
        cel(draw, RRect(r.cx - 5.0 + d * 1.0, r.hip_y - 1.0, r.cx - 2.0 + d * 1.0, r.hip_y + 2.0, 0.6), GD_PATCH, sh=None)
        stroke(draw, [(r.cx - 4.6 + d, r.hip_y - 0.6), (r.cx - 2.4 + d, r.hip_y + 1.6)], 0.4, shade(GD_PATCH, 1.6))
        # a lantern on the belt
        lx = r.cx + (4.6 if not d else d * 2.8)
        cel(draw, RRect(lx - 1.6, r.waist_y + 2.4, lx + 1.6, r.waist_y + 6.2, 0.6), (255, 226, 120), sh=None,
            line_color=ink((180, 140, 70)))
        Ell(lx, r.waist_y + 4.4, 0.7, 1.0).draw(draw, fill=(255, 250, 220))
    rig_belt(r, draw, (80, 60, 44), buckle=(200, 190, 170))
    rig_arms(r, draw, GD_COAT, (150, 120, 90), layer="near", hands=False)
    head_skull(r, draw, GD_SKIN)
    hx, hy = r.hx, r.head_cy
    if not r.back:
        head_face(r, draw, GD_SKIN, iris=(80, 90, 70), mood="calm", mouth="set", blush=False, brows=True,
                  brow_color=(90, 80, 76))
        # stubble
        for (u, v) in ((-2.4, 8.6), (0.0, 9.6), (2.4, 8.6), (-4.2, 7.4), (4.2, 7.4)) if not d else ((d * 5.0, 8.6), (d * 7.4, 7.8), (d * 3.0, 9.4)):
            Ell(hx + u, hy + v, 0.4, 0.4).draw(draw, fill=shade(GD_SKIN, 1.6))
    rig_hair(r, draw, (120, 110, 100), "short", hat=True, skin=GD_SKIN)
    # a battered wide hat with a bent brim
    cx0 = hx - d * 0.8
    brim = Poly(arc_pts(cx0 + d * 1.0, hy - 6.2, 15.0 if not d else 13.8, 3.8, 0, 360)[:-1])
    cel(draw, brim, GD_HAT, sh=(0.0, 1.2))
    crown = Poly([(cx0 - 7.2, hy - 6.2), (cx0 - 6.6, hy - 13.0), (cx0 - 2.0, hy - 14.4), (cx0 + 1.0, hy - 13.0),
                  (cx0 + 5.4, hy - 14.0), (cx0 + 7.0, hy - 12.6), (cx0 + 7.2, hy - 6.2)])
    cel(draw, crown, GD_HAT, sh=(1.4, 0.6))
    cel(draw, Poly([(cx0 - 7.2, hy - 6.2), (cx0 + 7.2, hy - 6.2), (cx0 + 7.1, hy - 8.0), (cx0 - 7.1, hy - 8.0)]),
        GD_PATCH, sh=None)
    if not r.back:
        shovel()
    for side in _sides(r):
        rig_hand(r, draw, side, (150, 120, 90), out=out if side == sh_side else 0.0)


# ===================================================================
# DULLAHAN (36) -- a headless knight carrying its head
# ===================================================================

DUL_ARMOR = (72, 70, 94)
DUL_CAPE = (104, 40, 120)
DUL_GLOW = (150, 255, 120)
DUL_FIRE = ((70, 170, 60), (150, 255, 120), (230, 255, 210))


def draw_dullahan(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=1.12)
    d = r.d
    head_side = (-1 if not r.back else 1) if not d else d
    sway = [0.0, 1.0, 0.0, -1.0][frame]
    rig_cape(r, draw, DUL_CAPE, layer="under")
    if d:
        rig_arms(r, draw, DUL_ARMOR, DUL_ARMOR, layer="far")
    rig_legs(r, draw, DUL_ARMOR, shade(DUL_ARMOR, 1.2), width=1.08)
    rig_torso(r, draw, DUL_ARMOR)
    cx = r.cx
    if not r.back:
        cel(draw, Poly([(cx - 2.6 + d * 2, r.sh_y + 0.6), (cx + 2.6 + d * 2, r.sh_y + 0.6), (cx + d * 2, r.waist_y)]),
            DUL_CAPE, sh=None)
    rig_belt(r, draw, shade(DUL_ARMOR, 1.4), buckle=DUL_GLOW)
    rig_arms(r, draw, DUL_ARMOR, DUL_ARMOR, layer="near", hands=False)
    for side in _sides(r):
        sx, sy = r.shoulder(side)
        cel(draw, Poly([(sx - 4.6, sy + 1.4), (sx - 3.0, sy - 2.8), (sx + (side * 1.4 if not d else 0), sy - 4.4),
                        (sx + 3.0, sy - 2.8), (sx + 4.6, sy + 1.4)]), DUL_ARMOR, sh=(0.8, 0.8), hi=(0.5, 0.5))
    # the gorget with nothing in it but green fire
    gx, gy = r.cx + d * 0.6, r.sh_y - 2.2
    flame(draw, gx - d * 1.0, gy + 0.8, 13.0, 17.0 + [0, 1.6, 0, 1.6][frame], DUL_FIRE, sway=sway - d * 2.4)
    cel(draw, Ell(gx, gy, 5.6 if not d else 4.4, 2.2), shade(DUL_ARMOR, 0.6), sh=None)
    Ell(gx, gy - 0.2, 4.0 if not d else 3.0, 1.2).draw(draw, fill=DUL_FIRE[0])
    rig_cape(r, draw, DUL_CAPE, layer="over")
    # the head, under one arm: a helmeted skull with glowing eyes
    h = hand_at(r, head_side, 0.4, 3.0)
    hxh, hyh = h[0] + (head_side * 2.0 if not d else d * 2.6), h[1] - 4.0
    cel(draw, Ell(hxh, hyh, 6.8, 6.6), DUL_ARMOR, sh=(1.2, 1.2), hi=(0.6, 0.6))
    if not r.back:
        cel(draw, RRect(hxh - 4.8, hyh - 1.4, hxh + 4.8, hyh + 1.8, 1.0), VOID, sh=None)
        for s in (-1, 1):
            Ell(hxh + s * 2.4, hyh + 0.2, 1.5, 1.0).draw(draw, fill=DUL_GLOW)
    cel(draw, Limb([(hxh - 0.4, hyh - 6.4), (hxh + 1.4, hyh - 9.2), (hxh + 3.6, hyh - 9.8)], [1.2, 0.9, 0.4]), DUL_CAPE, sh=None)
    for side in _sides(r):
        rig_hand(r, draw, side, DUL_ARMOR, reach=0.4 if side == head_side else 0.0, lift=3.0 if side == head_side else 0.0)


# ===================================================================
# PHANTOM (37) -- a porcelain mask, a swirling cloak, a thin rapier
# ===================================================================

PH_CLOAK = (58, 72, 130)
PH_GHOST = (206, 226, 252)
PH_MASK = (250, 250, 252)
PH_GLOW = (120, 200, 255)


def draw_phantom(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, bob=[0, -1, -2, -1][frame], step=0)
    d = r.d
    wave = [0.0, 1.0, 0.0, -1.0][frame]
    rp_side = _hold_side(r)
    cx = r.cx
    # the cloak, fanning out, over a ghostly tail
    cel(draw, Poly(flame_pts(cx - d * 1.0, r.base_y + 1.0, 14.0, 18.0, wave - d * 2.0, -1.0)), PH_GHOST, sh=None,
        line_color=shade(PH_GHOST, 1.6))
    # a high collar standing up behind the head
    hx0, hy0 = r.hx, r.head_cy
    if d:
        cel(draw, Poly([(hx0 - d * 2.0, r.sh_y - 1.0), (hx0 - d * 10.4, hy0 - 1.4), (hx0 - d * 12.0, hy0 - 4.6),
                        (hx0 - d * 11.4, hy0 + 5.0), (hx0 - d * 5.0, r.sh_y)]), shade(PH_CLOAK, 0.6), sh=None)
    else:
        for s2 in (-1, 1):
            cel(draw, Poly([(hx0 + s2 * 5.0, r.sh_y - 0.4), (hx0 + s2 * 12.6, hy0 - 2.6), (hx0 + s2 * 14.0, hy0 - 5.6),
                            (hx0 + s2 * 14.4, hy0 + 3.0), (hx0 + s2 * 10.4, r.sh_y + 0.6)]), shade(PH_CLOAK, 0.6), sh=None)
    w = r.sh_w
    if d:
        cloak = Poly([(cx - d * 3.0, r.sh_y - 2.6), (cx + d * 3.2, r.sh_y - 2.2), (cx + d * 5.4, r.hip_y + 3.0),
                      (cx - d * (10.0 + wave), r.hip_y + 5.0), (cx - d * 6.0, r.sh_y + 1.0)])
    else:
        cloak = Poly([(cx - w + 1.0, r.sh_y - 2.8), (cx + w - 1.0, r.sh_y - 2.8), (cx + w + 7.4 + wave, r.hip_y + 5.4),
                      (cx + 2.0, r.hip_y + 3.2), (cx, r.hip_y + 5.0), (cx - 2.0, r.hip_y + 3.2), (cx - w - 7.4 + wave, r.hip_y + 5.4)])
    cel(draw, cloak, PH_CLOAK, sh=(1.6, 1.0), regions=[(Poly([(cx - 20, r.hip_y + 1.0), (cx + 20, r.hip_y + 1.0),
                                                               (cx + 20, r.hip_y + 6.0), (cx - 20, r.hip_y + 6.0)]),
                                                         lit(PH_CLOAK, 0.5))])
    rig_arms(r, draw, PH_CLOAK, PH_GHOST, layer="near", hands=False, reach=0.3)
    head_skull(r, draw, PH_GHOST, ears=False)
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    if not r.back:
        # half mask over the right side of the face
        if d:
            mask = Poly([(hx + d * 0.0, hy - 5.6), (hx + d * (rx - 0.4), hy - 4.0), (hx + d * (rx - 0.6), hy + 5.4),
                         (hx + d * 5.4, hy + 7.0), (hx - d * 0.4, hy + 3.4)])
        else:
            mask = Poly([(hx - 0.4, hy - 6.0), (hx + rx - 1.6, hy - 5.0), (hx + rx - 0.8, hy + 2.0), (hx + rx - 3.4, hy + 6.6),
                         (hx + 1.0, hy + 5.4), (hx - 0.8, hy + 1.6)])
        cel(draw, mask, PH_MASK, sh=(0.8, 0.8))
        e1, e2, ey, mx, my = face_anchor(r)
        pairs = ((-1, e1), (1, e2)) if not d else ((-d, e1), (d, e2))
        for side, ex in pairs:
            w2 = 1.0 if not d or side == -d else 0.7
            if (not d and side > 0) or (d and side == d):
                Ell(ex, ey, 1.8 * w2, 1.4).draw(draw, fill=VOID)
                Ell(ex, ey, 1.0 * w2, 0.9).draw(draw, fill=PH_GLOW)
            else:
                ms_eye(draw, ex, ey, 3.8 * w2, 5.0, (70, 110, 170), (float(d), 0.0), "sharp", skin=PH_GHOST, side=side)
        ms_mouth(draw, mx - (1.6 if not d else 0), my, "smirk", PH_GHOST, 0.9)
    rig_hair(r, draw, (44, 52, 90), "swept", skin=PH_GHOST)
    # a thin rapier
    h = hand_at(r, rp_side, 0.3)
    ang = 90 + rp_side * 40 if not d else 90 - d * 60
    blade(draw, h[0], h[1], ang, length=15.0, width=0.9, guard=1.6, hilt=(210, 214, 230), flip=rp_side if not d else -d)
    cel(draw, Ell(h[0], h[1] - 0.4, 2.4, 2.2), (210, 214, 230), sh=None, lw=0.6)
    for side in _sides(r):
        rig_hand(r, draw, side, PH_GHOST, reach=0.3)


# ===================================================================
# MUMMY (38) -- all bandages, one eye peeking out, a gold scarab
# ===================================================================

MUM_WRAP = (238, 226, 196)
MUM_SH = (204, 188, 150)
MUM_GLOW = (110, 210, 255)


def _wraps(draw, x0, x1, y0, y1, step=2.4, color=MUM_SH, slant=0.8):
    y = y0 + step * 0.6
    i = 0
    while y < y1:
        stroke(draw, [(x0, y + slant * (1 if i % 2 else -1)), (x1, y - slant * (1 if i % 2 else -1))], 0.5, color)
        y += step
        i += 1


def draw_mummy(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=1.02)
    d = r.d
    wave = [0.0, 1.0, 0.0, -1.0][frame]
    if d:
        rig_arms(r, draw, MUM_WRAP, MUM_WRAP, layer="far", reach=0.7)
    rig_legs(r, draw, MUM_WRAP, MUM_SH, bare=True)
    for side in ((-1, 1) if not d else (-d, d)):
        hx0, hy0 = r.hip(side)
        fx, fy = r.foot(side)
        _wraps(draw, min(hx0, fx) - 1.6, max(hx0, fx) + 1.6, hy0 + 1.0, fy - 1.0, 2.2)
    rig_torso(r, draw, MUM_WRAP)
    cx = r.cx
    x0, x1 = (cx - r.sh_w + 0.6, cx + r.sh_w - 0.6) if not d else (cx - 4.0, cx + 4.4)
    _wraps(draw, x0, x1, r.sh_y - 1.4, r.hip_y, 2.4)
    if not r.back:
        sx = cx + d * 1.8
        cel(draw, Ell(sx, r.sh_y + 2.4, 2.4, 2.0), GOLD, sh=(0.4, 0.4))
        cel(draw, Ell(sx, r.sh_y + 2.2, 1.2, 1.0), (60, 150, 170), sh=None, line=False)
        for s in (-1, 1):
            Poly([(sx + s * 1.8, r.sh_y + 1.6), (sx + s * 4.4, r.sh_y + 0.8), (sx + s * 3.6, r.sh_y + 2.6)]).draw(draw, fill=GOLD)
    rig_arms(r, draw, MUM_WRAP, MUM_WRAP, layer="near", reach=0.7)
    # a loose end trailing off an arm
    s_, e, w_, h = arm_pts(r, _hold_side(r), 0.7)
    cel(draw, Limb([(e[0], e[1]), (e[0] + (3.0 if not d else -d * 2.0), e[1] + 4.0 + wave), (e[0] + (5.0 if not d else -d * 4.0), e[1] + 7.0 - wave)],
                   [0.9, 0.8, 0.5]), MUM_WRAP, sh=None, lw=0.6)
    head_skull(r, draw, MUM_WRAP, ears=False)
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    _wraps(draw, hx - rx + 1.0, hx + rx - 1.0, hy - ry + 1.0, hy + ry - 1.0, 2.8, MUM_SH, 1.2)
    if not r.back:
        e1, e2, ey, _, _ = face_anchor(r)
        ex = e1 if not d else e1
        cel(draw, Ell(ex, ey, 3.2, 2.8), VOID, sh=None, line=False)
        Ell(ex, ey, 1.6, 1.4).draw(draw, fill=MUM_GLOW)
        Ell(ex - 0.5, ey - 0.5, 0.6, 0.6).draw(draw, fill=(255, 255, 255))
        # a bandage band across the other eye
        ex2 = e2
        stroke(draw, [(ex2 - 3.4, ey - 1.6), (ex2 + 3.0, ey + 1.4)], 2.2, MUM_WRAP)
        stroke(draw, [(ex2 - 3.4, ey - 1.6), (ex2 + 3.0, ey + 1.4)], 0.5, MUM_SH)
    # a trailing end at the back of the head
    tail_x = hx + (7.0 if not d else -d * 6.0)
    cel(draw, Limb([(tail_x, hy - 4.0), (tail_x + (3.0 if not d else -d * 3.0), hy - 1.0 + wave), (tail_x + (4.4 if not d else -d * 5.4), hy + 3.0)],
                   [1.0, 0.9, 0.5]), MUM_WRAP, sh=None, lw=0.6)


# ===================================================================
# DEATH KNIGHT (39) -- a skull visor, spiked black plate, a rune blade
# ===================================================================

DK_ARMOR = (60, 56, 78)
DK_TRIM = (150, 36, 50)
DK_GLOW = (255, 64, 72)
DK_RUNE = (206, 100, 255)


def draw_deathknight(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=1.2)
    d = r.d
    sw_side = _hold_side(r)
    glow = [0, 1, 0, 1][frame]
    rig_cape(r, draw, DK_TRIM, layer="under")
    if d:
        rig_arms(r, draw, DK_ARMOR, DK_ARMOR, layer="far")
    rig_legs(r, draw, DK_ARMOR, shade(DK_ARMOR, 0.8), width=1.1)
    rig_torso(r, draw, DK_ARMOR)
    cx = r.cx
    if not r.back:
        skull(draw, cx + d * 2.0, r.sh_y + 2.4, 2.8, 2.6, d, glow=None, bone=(206, 200, 214), teeth=False)
    rig_belt(r, draw, DK_TRIM, buckle=(206, 200, 214))
    rig_arms(r, draw, DK_ARMOR, DK_ARMOR, layer="near", hands=False)
    for side in _sides(r):
        sx, sy = r.shoulder(side)
        ox2 = side * 1.2 if not d else 0.0
        cel(draw, Ell(sx + ox2, sy - 1.0, 4.6, 3.2), DK_ARMOR, sh=(0.8, 0.8), hi=(0.5, 0.5))
        for k in (-1, 0, 1):
            bx = sx + ox2 + k * 2.4
            Poly([(bx - 0.9, sy - 3.0), (bx + 0.9, sy - 3.0), (bx + (side * 0.8 if not d else 0), sy - 7.0)]).draw(draw, fill=(200, 196, 214))
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    # a crown of spikes rising off the helm
    for i, (u, ln) in enumerate(((-6.4, 5.0), (-3.2, 7.0), (0.0, 8.4), (3.2, 7.0), (6.4, 5.0)) if not d else
                                ((-d * 3.6, 6.0), (-d * 0.4, 8.0), (d * 2.8, 6.4))):
        bx = hx + u
        Poly([(bx - 1.3, hy - ry + 2.6), (bx + 1.3, hy - ry + 2.6), (bx + u * 0.2, hy - ry - ln + 2.0)]).draw(draw, fill=DK_ARMOR)
        stroke(draw, [(bx - 1.3, hy - ry + 2.6), (bx + u * 0.2, hy - ry - ln + 2.0), (bx + 1.3, hy - ry + 2.6)], 0.6, ink(DK_ARMOR))
    cel(draw, Ell(hx - d * 0.4, hy + 0.2, rx + 1.0, ry + 1.2), DK_ARMOR, sh=(1.6, 1.2), hi=(0.8, 0.8))
    if not r.back:
        # the visor is a skull: sockets that glow, a grille of teeth
        vx = hx + d * 3.0
        for s in ((-1, 1) if not d else (d, -d)):
            w = 1.0 if not d or s == d else 0.6
            ex = vx + s * 3.8 * (1 if not d else 0.8)
            Ell(ex, hy + 1.0, 2.8 * w, 2.4).draw(draw, fill=VOID)
            Ell(ex, hy + 1.2, 1.4 * w, 1.0).draw(draw, fill=DK_GLOW if glow else shade(DK_GLOW, 0.4))
        Poly([(vx - 0.9, hy + 4.4), (vx + 0.9, hy + 4.4), (vx, hy + 3.0)]).draw(draw, fill=VOID)
        for k in (-2.4, -0.8, 0.8, 2.4):
            stroke(draw, [(vx + k, hy + 5.8), (vx + k, hy + 8.2)], 0.6, VOID)
    rig_cape(r, draw, DK_TRIM, layer="over")
    h = hand_at(r, sw_side)
    ang = 90 + sw_side * 26 if not d else 90 - d * 40
    blade(draw, h[0], h[1], ang, length=15.4, width=2.2, color=(92, 88, 110), hilt=DK_TRIM, grip=(40, 34, 48),
          guard=3.6, flip=sw_side if not d else -d)
    for t in (0.3, 0.55, 0.8):
        p = xform([(15.4 * t, 0.0)], h[0], h[1], ang)[0]
        Ell(p[0], p[1], 0.7, 0.7).draw(draw, fill=DK_RUNE)
    for side in _sides(r):
        rig_hand(r, draw, side, DK_ARMOR)


# ===================================================================
# SHADOWFIEND (40) -- a horned, winged little demon with a third eye
# ===================================================================

SF_SKIN = (84, 52, 108)
SF_WING = (56, 34, 74)
SF_GLOW = (255, 96, 214)
SF_HORN = (232, 222, 206)


def draw_shadowfiend(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=1.1, head=1.0)
    d = r.d
    flap = [0.0, 1.0, 0.0, 1.0][frame]
    cx = r.cx
    # bat wings behind
    for s in ((-1, 1) if not d else (-d,)):
        bx = cx + s * 3.0 if not d else cx - d * 3.0
        by = r.sh_y - 1.0
        k = 1.0 if not d else 0.9
        tipx = bx + (s if not d else -d) * (14.0 + flap * 2.0) * k
        pts = [(bx, by), (tipx, by - 9.0 - flap * 2.0), (tipx - (s if not d else -d) * 1.4, by + 1.0),
               (tipx - (s if not d else -d) * 4.8, by - 1.4), (tipx - (s if not d else -d) * 6.4, by + 4.0),
               (tipx - (s if not d else -d) * 9.4, by + 1.6), (bx + (s if not d else -d) * 1.0, by + 6.0)]
        cel(draw, Poly(pts), SF_WING, sh=(0.8, 0.8))
        for j in (1, 3, 5):
            stroke(draw, [(bx, by), pts[j]], 0.5, shade(SF_WING, 1.4))
    # a spaded tail
    tx = cx + (5.0 if not d else -d * 4.0)
    wag = [0.0, 1.0, 0.0, -1.0][frame]
    cel(draw, Limb([(tx, r.hip_y), (tx + (4.0 if not d else -d * 4.0), r.hip_y + 3.0 + wag), (tx + (8.0 if not d else -d * 8.0), r.hip_y - 1.0)],
                   [1.2, 1.0, 0.7]), SF_SKIN, sh=None)
    ex, ey = tx + (8.0 if not d else -d * 8.0), r.hip_y - 1.0
    cel(draw, Poly([(ex, ey - 2.4), (ex + 2.0, ey + 0.8), (ex, ey + 0.2), (ex - 2.0, ey + 0.8)]), SF_SKIN, sh=None)
    if d:
        rig_arms(r, draw, SF_SKIN, SF_SKIN, layer="far", hands=False)
    rig_legs(r, draw, SF_SKIN, shade(SF_SKIN, 1.0), bare=True)
    rig_torso(r, draw, SF_SKIN)
    if not r.back:
        cel(draw, Ell(cx + d * 2.0, r.waist_y - 1.0, 3.6, 3.4), lit(SF_SKIN, 0.6), sh=None, line=False)
    rig_arms(r, draw, SF_SKIN, SF_SKIN, layer="near", hands=False)
    for side in _sides(r):
        h = hand_at(r, side)
        cel(draw, Ell(h[0], h[1], 2.2, 2.0), SF_SKIN, sh=(0.5, 0.5))
        for k in (-1, 0, 1):
            x = h[0] + k * 1.2
            Poly([(x - 0.5, h[1] + 1.2), (x + 0.5, h[1] + 1.2), (x + k * 0.3, h[1] + 3.2)]).draw(draw, fill=SF_HORN)
    head_skull(r, draw, SF_SKIN, ears=False)
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    # curling horns
    for s in ((-1, 1) if not d else (-d, d)):
        near = not d or s == -d
        hb = (hx + s * (rx - 3.6), hy - ry + 2.6)
        pts = [hb, (hb[0] + s * 3.6, hb[1] - 4.4), (hb[0] + s * 7.4, hb[1] - 4.8), (hb[0] + s * 9.0, hb[1] - 2.0)]
        cel(draw, Limb(pts, [2.2, 1.8, 1.2, 0.3]), SF_HORN if near else shade(SF_HORN, 0.6), sh=(0.4, 0.6))
    if not r.back:
        e1, e2, ey, mx, my = face_anchor(r)
        for side, ex in (((-1, e1), (1, e2)) if not d else ((-d, e1), (d, e2))):
            w = 1.0 if not d or side == -d else 0.7
            ms_eye(draw, ex, ey, 4.0 * w, 5.0, SF_GLOW, (float(d), 0.0), "sharp", glow=None, skin=SF_SKIN, side=side,
                   lash=(30, 16, 40))
        # the third eye on the brow
        tx3 = hx + d * 3.6
        Ell(tx3, hy - 4.4, 1.6, 2.2).draw(draw, fill=VOID)
        Ell(tx3, hy - 4.2, 0.9, 1.2).draw(draw, fill=SF_GLOW)
        ms_mouth(draw, mx, my, "fangs", SF_SKIN, 1.2)


# ===================================================================
# POLTERGEIST (41) -- a sheet ghost sticking its tongue out, crockery flying
# ===================================================================

PG_SHEET = (246, 246, 252)
PG_SH = (204, 210, 232)


def draw_poltergeist(draw, ox, oy, direction, frame):
    d, back = _dir(direction)
    bob = [0, -2, -3, -1][frame]
    base = oy + 54 + bob
    cx = ox + 32
    wave = [0.0, 1.0, 0.0, -1.0][frame]
    # orbiting junk: a teacup, a book, a fork -- behind first, then in front
    items = []
    for i in range(3):
        a = math.radians(frame * 90 + i * 120 + 20)
        items.append((math.sin(a), cx + math.cos(a) * 16.0, base - 20.0 + math.sin(a) * 3.0, i))

    def junk(x, y, kind):
        if kind == 0:
            cel(draw, Poly([(x - 2.4, y - 1.4), (x + 2.4, y - 1.4), (x + 1.8, y + 1.6), (x - 1.8, y + 1.6)]), (255, 190, 200),
                sh=(0.4, 0.4))
            cel(draw, Limb(arc_pts(x + 2.6, y, 1.2, 1.2, -80, 80, 6), [0.4] * 7), (255, 190, 200), sh=None, lw=0.5)
        elif kind == 1:
            cel(draw, Poly([(x - 2.8, y - 2.0), (x + 2.8, y - 1.4), (x + 2.6, y + 2.0), (x - 3.0, y + 1.4)]), (110, 150, 230),
                sh=(0.4, 0.4))
            stroke(draw, [(x - 2.4, y + 1.0), (x + 2.2, y + 1.6)], 0.6, (250, 246, 230))
        else:
            cel(draw, Limb([(x - 3.0, y + 2.0), (x + 1.4, y - 1.6)], [0.5, 0.5]), (206, 210, 224), sh=None, lw=0.5)
            for k in (-0.8, 0.0, 0.8):
                stroke(draw, [(x + 1.4 + k, y - 1.6), (x + 2.2 + k, y - 3.2)], 0.4, (206, 210, 224))

    for (depth, x, y, i) in items:
        if depth < 0:
            junk(x, y, i)
    # the sheet: round top, wavy hem
    hem_y = base - 3.0
    pts = arc_pts(cx - d * 0.6, base - 24.0, 12.4, 13.0, 180, 360, 30)
    pts += [(cx + 12.8, base - 12.0), (cx + 12.0 + wave, hem_y)]
    for i in range(1, 6):
        t = i / 6.0
        pts.append((cx + 12.0 + wave - 24.0 * t, hem_y + (2.2 if i % 2 else -0.6) + (wave * 0.6 if i % 2 else 0)))
    pts += [(cx - 12.0 + wave, hem_y), (cx - 12.8, base - 12.0)]
    cel(draw, Poly(pts), PG_SHEET, sh=(1.8, 1.4), tone=PG_SH)
    # stubby sheet arms
    for side in ((-1, 1) if not d else (d,)):
        ax = cx + side * 12.0 if not d else cx + d * 8.0
        cel(draw, Limb([(ax - (side if not d else d) * 2.0, base - 18.0), (ax + (side if not d else d) * 2.4, base - 14.0 + wave * side)],
                       [2.4, 1.6]), PG_SHEET, sh=(0.6, 0.6), tone=PG_SH)
    if not back:
        fx = cx + d * 4.0
        for side, ex in (((-1, fx - 4.6), (1, fx + 4.6)) if not d else ((d, fx + d * 3.0), (-d, fx - d * 3.0))):
            w = 1.0 if not d or side == d else 0.7
            ms_eye(draw, ex, base - 24.0, 4.0 * w, 5.4, (70, 70, 110), (float(d), 0.0), "bright", skin=PG_SHEET,
                   side=side if not d else -side)
        mx = fx + d * 1.0
        stroke(draw, [(mx - 2.4, base - 19.0), (mx, base - 18.2), (mx + 2.4, base - 19.0)], 0.7, shade(PG_SH, 1.6))
        cel(draw, RRect(mx - 1.2, base - 18.8, mx + 1.2, base - 15.6, 1.1), (255, 130, 150), sh=None, lw=0.6)
        for side in (-1, 1):
            Ell(fx + side * 7.2, base - 20.6, 1.6, 0.8).draw(draw, fill=(255, 196, 206))
    for (depth, x, y, i) in items:
        if depth >= 0:
            junk(x, y, i)


UNDEAD_DRAW_FUNCTIONS = {
    'necromancer': draw_necromancer,
    'skeletonking': draw_skeletonking,
    'banshee': draw_banshee,
    'lich': draw_lich,
    'ghoul': draw_ghoul,
    'reaper': draw_reaper,
    'shade': draw_shade,
    'revenant': draw_revenant,
    'gravedigger': draw_gravedigger,
    'dullahan': draw_dullahan,
    'phantom': draw_phantom,
    'mummy': draw_mummy,
    'deathknight': draw_deathknight,
    'shadowfiend': draw_shadowfiend,
    'poltergeist': draw_poltergeist,
}


def main():
    for name, draw_func in UNDEAD_DRAW_FUNCTIONS.items():
        generate_character(name, draw_func=draw_func)
    print(f"\nGenerated {len(UNDEAD_DRAW_FUNCTIONS)} undead character sprites.")


if __name__ == "__main__":
    main()
