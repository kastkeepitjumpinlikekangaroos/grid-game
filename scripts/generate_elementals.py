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

# The robe is a deep crimson and the hair a flame with its hot orange and
# yellow middle showing: they were two reds a step apart, one red column from
# crown to hem.
PYRO_ROBE = (176, 36, 54)
PYRO_HAIR = (238, 86, 36)
PYRO_TRIM = (255, 176, 56)
PYRO_MID = (255, 154, 48)
PYRO_CORE = (255, 230, 112)


def _pyro_glow(draw, x, yb, w, h, sway, drop=0.0, flip=1.0):
    """The hot middle of the flame her hair rises into (the crest stands on
    (x, yb), `w` by `h`): an orange flame and a yellow heart inside it, drawn
    over the hair so that the head itself is alight. `drop` carries them down
    the back of her head when she is seen from behind."""
    Poly(flame_pts(x, yb - h * 0.1 + drop, w * 0.6, h * 0.7 + drop, sway * 0.64, -flip)).draw(draw, fill=PYRO_MID)
    Poly(flame_pts(x, yb - h * 0.16 + drop * 0.9, w * 0.32, h * 0.4 + drop * 0.9, sway * 0.3, flip)).draw(draw, fill=PYRO_CORE)


def _pyro_arm(draw, sh, el, hand):
    """The arm that holds the fireball out, palm up (the rig's arms hang to the
    hip, and the fireball sat on her belt)."""
    wr = (hand[0] + (el[0] - hand[0]) * 0.25, hand[1] + (el[1] - hand[1]) * 0.25)
    cel(draw, Limb([sh, el, wr], [2.2, 2.0, 1.8]), PYRO_ROBE, sh=(0.8, 0.4))
    cel(draw, Ell(wr[0], wr[1], 1.9, 1.5), PYRO_TRIM, sh=None, lw=0.6)


def _pyro_fireball(draw, x, y, sway, flick):
    """The fireball over her palm: a ball of fire with its flame standing off
    it, one mass, big enough to be seen beside her at game size."""
    blob(draw, [Ell(x, y, 3.5, 3.3), Poly(flame_pts(x, y + 1.8, 8.6, 9.4 + flick, sway))], FIRE[0], sh=None)
    Poly(flame_pts(x, y + 1.9, 5.4, 6.4 + flick, sway * 0.7, -1.0)).draw(draw, fill=FIRE[1])
    Ell(x, y + 0.3, 2.5, 2.3).draw(draw, fill=FIRE[1])
    Poly(flame_pts(x, y + 1.6, 2.8, 4.0 + flick * 0.5, sway * 0.4)).draw(draw, fill=FIRE[2])
    Ell(x, y + 0.7, 1.5, 1.4).draw(draw, fill=FIRE[2])


def draw_pyromancer(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, head=0.96)
    d = r.d
    cx, sy = r.cx, r.sh_y
    sway = [0.0, 1.0, 0.0, -1.0][frame]
    flick = [0.0, 1.0, 0.0, 1.0][frame]
    fire_side = _hold_side(r)
    # The fire hand is held out from her, palm up, and stays there while she
    # walks: out to her side head-on and from behind, ahead of her in profile.
    if d:
        el, hand = (cx + d * 2.8, sy + 6.0), (cx + d * 8.8, sy + 8.2)
    else:
        el, hand = (cx + fire_side * 9.0, sy + 6.0), (cx + fire_side * 12.6, sy + 8.4)
    if d:
        rig_arms(r, draw, PYRO_ROBE, SKIN, layer="far", cuff=PYRO_TRIM)
    rig_legs(r, draw, shade(PYRO_ROBE, 1.4), (120, 56, 40))
    rig_robe(r, draw, PYRO_ROBE, trim=PYRO_TRIM, flare=3.2)
    # flames licking up from the hem
    xs = (-6.4, -2.2, 2.2, 6.4) if not d else (-d * 4.6, -d * 0.6, d * 3.4)
    for i, x in enumerate(xs):
        flame(draw, r.cx + x + r.ph * 0.6, r.base_y - 2.6, 3.2, 4.0 + (i % 2) * 1.4, sway=sway * 0.6)
    rig_belt(r, draw, (120, 34, 40), buckle=PYRO_TRIM)
    if not d:
        rig_arms(r, draw, PYRO_ROBE, SKIN, layer="near", cuff=PYRO_TRIM, sides=(-fire_side,))
    _pyro_arm(draw, r.shoulder(fire_side), el, hand)
    # Her hair is a flame: the crest it rises into stands behind her head, and
    # its hot middle is laid over the hair after the head is drawn.
    hx, hy = r.hx, r.head_cy
    fx, fyb, fw, fh, fsw = hx - d * 2.0, hy - 5.5, 22.0, 18.6, sway * 1.6 - d * 3.0
    cel(draw, Poly(flame_pts(fx, fyb, fw, fh, fsw)), PYRO_HAIR, sh=None)
    rig_head(r, draw, SKIN, hair=PYRO_HAIR, style="spiky", eye_color=(236, 140, 30), expression="grin",
             mood="sharp", hat=True, brows=False)
    _pyro_glow(draw, fx, fyb, fw, fh, fsw, drop=13.0 if r.back else 0.0)
    cel(draw, Ell(hand[0], hand[1], 2.2, 2.2), SKIN, sh=(0.6, 0.6))
    _pyro_fireball(draw, hand[0] + (d * 1.2 if d else fire_side * 0.8), hand[1] - 5.5, sway * 0.8 + (d * 1.6 if d else fire_side * 0.8), flick)


# ===================================================================
# CRYOMANCER (13) -- ice crown, fur-trimmed robe, crystal staff
# ===================================================================

CRYO_ROBE = (88, 148, 230)
CRYO_FUR = (246, 250, 255)
CRYO_FUR_SH = (206, 222, 244)
CRYO_FUR_LINE = (112, 144, 206)
CRYO_HAIR = (176, 214, 248)
CRYO_GEM = (112, 208, 252)
CRYO_BAND = (58, 104, 198)
CRYO_SHAFT = (94, 110, 186)


def _snowflake(draw, x, y, rad, color=(255, 255, 255), w=0.9):
    for a in (0, 60, 120):
        ca, sa = math.cos(math.radians(a)), math.sin(math.radians(a))
        stroke(draw, [(x - ca * rad, y - sa * rad), (x + ca * rad, y + sa * rad)], w, color)


def _cryo_fur(draw, pts, rx=2.4, ry=2.0):
    """A band of fur trim: lumps along a line, one mass, lined in a pale blue (white lined in
    the kit's slate ink came out a grey chain)."""
    blob(draw, [Ell(x, y, rx, ry) for (x, y) in pts], CRYO_FUR, sh=(0.4, 0.6), tone=CRYO_FUR_SH,
         line_color=CRYO_FUR_LINE, lw=0.7)


def _cryo_crown(draw, r):
    """A tiara of three ice crystals on a band: upright and apart, so it is a crown on the hair
    and not more hair (the crystals were the hair's own blue)."""
    hx, hy, ry, d = r.hx, r.head_cy, r.head_ry, r.d
    y0 = hy - ry + 3.0
    if d:
        band = Limb([(hx + d * 6.4, y0 + 1.8), (hx + d * 1.0, y0 + 0.2), (hx - d * 4.8, y0 + 0.8)], [0.9, 1.0, 0.9],
                    cap0=True)
        spikes = ((d * 4.6, -90 + d * 4, 6.4), (d * 0.6, -90 - d * 4, 10.0), (-d * 3.4, -90 - d * 12, 6.4))
    else:
        band = Limb(bez((hx - 7.6, y0 + 2.0), (hx, y0 - 1.0), (hx + 7.6, y0 + 2.0), 8), 1.0, cap0=True)
        spikes = ((-4.8, -97, 6.6), (0.0, -90, 10.4), (4.8, -83, 6.6))
    for (u, a, ln) in spikes:
        crystal(draw, hx + u, y0 + 0.6, a, ln, 3.5, color=CRYO_GEM)
    cel(draw, band, CRYO_BAND, sh=None, lw=0.7)


def _cryo_staff(draw, bot, top, frame):
    """The staff from its foot to its head, a big crystal standing in a silver cup on top."""
    cel(draw, Limb([bot, top], [1.15, 1.0]), CRYO_SHAFT, sh=(0.5, 0.0), lw=0.7)
    ang = math.degrees(math.atan2(top[1] - bot[1], top[0] - bot[0]))
    cel(draw, Poly(xform([(-1.6, -1.5), (0.8, -2.5), (0.8, 2.5), (-1.6, 1.5)], top[0], top[1], ang)), (226, 236, 250),
        sh=None, lw=0.7)
    crystal(draw, top[0], top[1], ang, 10.5, 5.6, color=CRYO_GEM)
    tip = xform([(6.0, 0.0)], top[0], top[1], ang)[0]
    for i, (sx, sy) in enumerate(((-4.6, -2.0), (4.4, 2.6), (-3.6, 5.6))):
        if (frame + i) % 2 == 0:
            sparkle(draw, tip[0] + sx, tip[1] + sy, 1.7, (236, 250, 255))


def draw_cryomancer(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, head=0.95)
    d = r.d
    cx = r.cx
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    side_s = _hold_side(r)
    out = 4.0
    if d:
        rig_arms(r, draw, CRYO_ROBE, SKIN, layer="far", cuff=CRYO_FUR)
    rig_legs(r, draw, shade(CRYO_ROBE, 1.2), (150, 170, 206))
    rig_robe(r, draw, CRYO_ROBE, flare=3.2)
    # a snowflake on the chest, a bigger one on the back
    if r.back:
        _snowflake(draw, cx, r.sh_y + 3.6, 3.0, w=1.0)
    elif not d:
        _snowflake(draw, cx, r.sh_y + 3.4, 2.4)
    rig_belt(r, draw, (52, 90, 176), buckle=(206, 236, 255))
    # fur at the hem and the collar
    hem = r.base_y - 3.6
    sway = r.ph * 0.8
    if d:
        _cryo_fur(draw, [(cx + d * u + sway * d, hem) for u in (-6.2, -2.2, 1.8, 5.6)])
        _cryo_fur(draw, [(cx + u, r.sh_y - 1.4) for u in (-2.8, 3.0)], 2.9, 2.1)
    else:
        _cryo_fur(draw, [(cx + u + sway, hem) for u in (-8.2, -4.1, 0.0, 4.1, 8.2)])
        _cryo_fur(draw, [(cx + u, r.sh_y - 1.4) for u in (-4.8, 0.0, 4.8)], 2.9, 2.1)
    # the staff: out to the side head-on and from behind; in profile planted ahead of him and
    # leaning forward, so it stands clear of his face
    if d:
        bot, top = (cx + d * 6.0, r.base_y - 0.8), (cx + d * 16.0, r.base_y - 44.0)
        grip = (cx + d * 9.5, r.base_y - 16.0)
        s = r.shoulder(d)
        el = ((s[0] + grip[0]) / 2.0 - d * 0.4, (s[1] + grip[1]) / 2.0 + 1.3)
        cel(draw, Limb([s, el, grip], [2.2, 2.0, 1.8]), CRYO_ROBE, sh=(0.8, 0.4))
        _cryo_fur(draw, [(grip[0] - d * 1.6, grip[1] - 0.2)], 1.8, 2.3)
    else:
        rig_arms(r, draw, CRYO_ROBE, SKIN, layer="near", hands=False, sides=(-side_s,))
        rig_arms(r, draw, CRYO_ROBE, SKIN, layer="near", hands=False, sides=(side_s,), out=out)
        for s2 in (-1, 1):
            w = arm_pts(r, s2, 0.0, 0.0, out if s2 == side_s else 0.0)[2]
            _cryo_fur(draw, [(w[0], w[1] - 0.2)], 2.6, 1.8)
        grip = hand_at(r, side_s, 0.0, 0.0, out)
        bot, top = (grip[0] + 0.2, r.base_y - 0.8), (grip[0] + 0.2, hy - 9.0)
    if r.back:
        head_skull(r, draw, SKIN)
        # the hair's underside at the nape, so no skin shows through its hem
        clipped(draw, Ell(hx, hy, rx - 0.9, ry - 0.9),
                lambda l: Ell(hx, hy + ry - 2.4, rx * 0.8, 3.6).draw(l, fill=shade(CRYO_HAIR, 1.3)))
        rig_hair(r, draw, CRYO_HAIR, "swept", hat=True, skin=SKIN)
    else:
        rig_head(r, draw, SKIN, hair=CRYO_HAIR, style="swept", eye_color=(44, 112, 210), expression="set",
                 mood="calm", hat=True, brows=False)
    _cryo_crown(draw, r)
    _cryo_staff(draw, bot, top, frame)
    if d:
        cel(draw, Ell(grip[0], grip[1], 2.2, 2.2), SKIN, sh=(0.6, 0.6))
    else:
        for side in (-1, 1):
            rig_hand(r, draw, side, SKIN, out=out if side == side_s else 0.0)


# ===================================================================
# STORMCALLER (14) -- electric hair, a thundercloud at his shoulder
# ===================================================================

STORM_COAT = (62, 60, 128)
STORM_TRIM = (255, 226, 76)
STORM_TRIM_LINE = (222, 146, 20)
STORM_HAIR = (255, 234, 112)
STORM_CLOUD = (124, 128, 166)
STORM_CLOUD_SH = (88, 90, 136)
STORM_STEEL = (190, 198, 222)
# hair standing on end: taller spikes than the kit's "wild", and longer at the nape
STORM_STYLE = dict(HAIR_STYLES["wild"], spikes=(8, 5.6), side=7.5)


def _storm_bolt(draw, pts, width):
    """Lightning that is yellow: the kit's bolt is lined so heavily that a thin one is all line."""
    n = len(pts)
    cel(draw, Limb(pts, [width * (1.0 - 0.8 * i / (n - 1.0)) for i in range(n)], cap=False), STORM_TRIM, sh=None,
        line_color=STORM_TRIM_LINE, lw=0.5)


def _storm_flash(draw, x, y, k):
    """The lightning flash of a warning sign, sewn on his coat."""
    pts = ((0.9, -4.4), (-2.3, 0.8), (-0.3, 0.8), (-1.4, 4.4), (2.3, -1.2), (0.3, -1.2), (2.1, -4.4))
    cel(draw, Poly([(x + u * k, y + v * k) for (u, v) in pts]), STORM_TRIM, sh=None, line_color=STORM_TRIM_LINE,
        lw=0.5)


def _storm_rod(draw, bot, top, frame):
    """A lightning rod: a steel staff ending in a fork like a tuning fork's, a spark arcing
    between its tines (two straight splayed tines were a pitchfork)."""
    ux, uy = top[0] - bot[0], top[1] - bot[1]
    ln = math.hypot(ux, uy)
    ux, uy = ux / ln, uy / ln
    px, py = -uy, ux
    at = lambda a, b: (top[0] + ux * a + px * b, top[1] + uy * a + py * b)
    parts = [Limb([bot, top], [1.0, 0.9])]
    for s in (-1, 1):
        parts.append(Limb([at(-1.2, 0.0), at(0.6, s * 2.2), at(2.6, s * 2.9), at(6.4, s * 2.9)], [0.9, 0.85, 0.8, 0.6]))
    blob(draw, parts, STORM_STEEL, sh=(0.5, 0.0), lw=0.6)
    cel(draw, Ell(top[0], top[1], 1.4, 1.4), STORM_TRIM, sh=None, lw=0.6)
    k = 1.0 if frame % 2 == 0 else -1.0
    y = 5.0 if frame % 2 == 0 else 3.6
    cel(draw, Limb([at(y, -2.6), at(y + 1.1 * k, -0.9), at(y - 1.1 * k, 0.9), at(y, 2.6)], 0.7, cap0=True), STORM_TRIM,
        sh=None, line_color=STORM_TRIM_LINE, lw=0.4)
    if frame % 2 == 0:
        tip = at(7.6, 2.9)
        sparkle(draw, tip[0], tip[1], 1.9, STORM_TRIM)


def draw_stormcaller(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame)
    d = r.d
    cx = r.cx
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    rod_side = _hold_side(r)
    out = 4.5
    if d:
        rig_arms(r, draw, STORM_COAT, SKIN, layer="far", cuff=STORM_TRIM)
    rig_legs(r, draw, shade(STORM_COAT, 1.0), (60, 52, 80))
    shape = rig_robe(r, draw, STORM_COAT, flare=2.6, split=True)
    # a yellow band round the hem, wide enough to be yellow
    hem = r.base_y - 2.6
    clipped(draw, shape, lambda l: Poly([(cx - 20, hem - 2.5), (cx + 20, hem - 2.5), (cx + 20, hem + 1.0),
                                         (cx - 20, hem + 1.0)]).draw(l, fill=STORM_TRIM))
    clipped(draw, shape, lambda l: stroke(l, [(cx - 20, hem - 2.5), (cx + 20, hem - 2.5)], 0.5, STORM_TRIM_LINE))
    shape.draw(draw, fill=None, outline=ink(STORM_COAT), width=1.0)
    # the flash on his chest, and on his back
    if not d:
        _storm_flash(draw, cx, r.sh_y + 2.5, 0.95 if r.back else 0.85)
    rig_belt(r, draw, shade(STORM_COAT, 1.6), buckle=STORM_TRIM)
    # the rod: out to the side head-on and from behind; in profile planted ahead of him and
    # leaning forward, so it stands clear of his face
    if d:
        bot, top = (cx + d * 6.0, r.base_y - 0.8), (cx + d * 16.6, r.base_y - 43.0)
        grip = (cx + d * 9.9, r.base_y - 16.0)
        s = r.shoulder(d)
        el = ((s[0] + grip[0]) / 2.0 - d * 0.4, (s[1] + grip[1]) / 2.0 + 1.3)
        cel(draw, Limb([s, el, grip], [2.2, 2.0, 1.8]), STORM_COAT, sh=(0.8, 0.4))
        cel(draw, Ell(grip[0] - d * 1.5, grip[1] - 0.3, 1.2, 2.0), STORM_TRIM, sh=None, line_color=STORM_TRIM_LINE, lw=0.5)
    else:
        rig_arms(r, draw, STORM_COAT, SKIN, layer="near", cuff=STORM_TRIM, hands=False, sides=(-rod_side,))
        rig_arms(r, draw, STORM_COAT, SKIN, layer="near", cuff=STORM_TRIM, hands=False, sides=(rod_side,), out=out)
        grip = hand_at(r, rod_side, 0.0, 0.0, out)
        bot, top = (grip[0] + 0.2, r.base_y - 0.8), (grip[0] + 0.2, hy - 13.0)
    if r.back:
        head_skull(r, draw, SKIN)
        # the hair's underside at the nape, so no skin shows through its hem
        clipped(draw, Ell(hx, hy, rx - 0.9, ry - 0.9),
                lambda l: Ell(hx, hy + ry - 2.4, rx * 0.8, 3.6).draw(l, fill=shade(STORM_HAIR, 1.3)))
        rig_hair(r, draw, STORM_HAIR, STORM_STYLE, skin=SKIN)
    else:
        rig_head(r, draw, SKIN, hair=STORM_HAIR, style=STORM_STYLE, eye_color=(230, 170, 30), expression="grin",
                 mood="sharp")
    # the thundercloud riding at his shoulder, clear of his face, a bolt dropping out of it
    ccx = cx - rod_side * 17.6
    ccy = hy - 4.5 + [0, -0.6, 0, -0.6][frame]
    if frame % 2 == 0:
        _storm_bolt(draw, [(ccx - 0.4, ccy + 2.6), (ccx + 1.7, ccy + 6.2), (ccx - 1.1, ccy + 7.4), (ccx + 0.8, ccy + 12.0)],
                    1.5)
    cloud(draw, ccx, ccy, 10.5, 6.6, STORM_CLOUD, tone=STORM_CLOUD_SH)
    _storm_rod(draw, bot, top, frame)
    if d:
        cel(draw, Ell(grip[0], grip[1], 2.2, 2.2), SKIN, sh=(0.6, 0.6))
    else:
        for side in (-1, 1):
            rig_hand(r, draw, side, SKIN, out=out if side == rod_side else 0.0)


# ===================================================================
# EARTHSHAKER (15) -- a stout dwarf with boulder pauldrons and rock fists
# ===================================================================

EARTH_STONE = (152, 142, 130)
EARTH_MOSS = (112, 164, 80)
EARTH_TUNIC = (72, 122, 100)
EARTH_BEARD = (200, 112, 54)
EARTH_HORN = (236, 226, 204)


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


def _earth_braid(draw, x, y, lean):
    """A braid hanging from (x, y): three beads of hair, a gold ring, a tuft."""
    blob(draw, [Ell(x + lean * 0.3, y + 2.2, 2.6, 2.5), Ell(x + lean * 0.6, y + 5.8, 2.3, 2.4),
                Ell(x + lean, y + 9.0, 1.9, 2.1), Ell(x + lean * 1.3, y + 12.6, 1.5, 1.8)], EARTH_BEARD, sh=(0.8, 0.9))
    cel(draw, RRect(x + lean * 1.15 - 1.8, y + 10.2, x + lean * 1.15 + 1.8, y + 11.8, 0.6), GOLD, sh=(0.0, 0.5), lw=0.7)


def draw_earthshaker(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=1.32, head=0.96)
    d = r.d
    if d:
        rig_arms(r, draw, SKIN_TAN, SKIN_TAN, layer="far", hands=False)
        h = hand_at(r, -d)
        _rock(draw, h[0], h[1], 3.6, 3.2, shade(EARTH_STONE, 0.5), moss=False)
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
    hx, hy = r.hx, r.head_cy
    rx, ry = r.head_rx, r.head_ry
    sw = r.ph * 0.8
    head_skull(r, draw, SKIN_TAN, ears=False)
    if r.back:
        # from behind: a mane of hair from under the helm, and a braid down his back
        blob(draw, [Ell(hx, hy + 4.0, rx + 0.7, 6.6), Ell(hx - 6.6, hy + 8.6, 4.3, 3.6), Ell(hx + 6.6, hy + 8.6, 4.3, 3.6),
                    Ell(hx, hy + 9.8, 4.8, 3.4)], EARTH_BEARD, sh=(1.1, 1.2))
        _earth_braid(draw, hx, hy + 9.6, sw)
    elif d:
        # in profile: hair over the back of the skull, a braid behind, and the beard growing
        # along the jaw from under the helm, not hung on the front of the face
        blob(draw, [Ell(hx - d * 7.0, hy + 3.4, 5.4, 5.8), Ell(hx - d * 8.8, hy + 7.8, 3.6, 3.6)], EARTH_BEARD, sh=(1.0, 1.1))
        _earth_braid(draw, hx - d * 10.0, hy + 8.6, -d * abs(sw) - d * 0.4)
        head_face(r, draw, SKIN_TAN, iris=(84, 150, 70), mood="sharp", mouth=None, brows=False)
        e1, e2, ey, _, _ = face_anchor(r)
        blob(draw, [Ell(e1, ey - 3.8, 2.8, 1.3), Ell(e1 - d * 2.0, ey - 3.4, 1.6, 1.1)], EARTH_BEARD, sh=(0.0, 0.5), lw=0.8)
        blob(draw, [Ell(hx - d * 3.0, hy + 4.2, 2.7, 4.4), Ell(hx + d * 1.8, hy + 9.0, 5.6, 3.5), Ell(hx + d * 6.0, hy + 11.0, 4.6, 5.0),
                    Ell(hx + d * 6.6, hy + 15.8, 2.8, 3.2)], EARTH_BEARD, sh=(1.1, 1.2))
        cel(draw, RRect(hx + d * 6.6 - 1.9, hy + 16.6, hx + d * 6.6 + 1.9, hy + 18.2, 0.6), GOLD, sh=(0.0, 0.5), lw=0.7)
        # the moustache, bristling out past his lip
        cel(draw, Ell(hx + d * 8.6, hy + 6.8, 3.2, 1.9), lit(EARTH_BEARD, 0.5), sh=(0.0, 0.6), lw=0.8)
    else:
        head_face(r, draw, SKIN_TAN, iris=(84, 150, 70), mood="sharp", mouth=None, brows=False)
        e1, e2, ey, _, _ = face_anchor(r)
        for side, ex in ((-1, e1), (1, e2)):
            blob(draw, [Ell(ex, ey - 3.8, 2.8, 1.3), Ell(ex + side * 2.0, ey - 3.4, 1.6, 1.1)], EARTH_BEARD,
                 sh=(0.0, 0.5), lw=0.8)
        # the beard: a big mass parted into two braids, each tied with a gold ring
        blob(draw, [Ell(hx, hy + 9.8, 7.8, 6.6), Ell(hx - 7.4, hy + 6.8, 3.6, 3.8), Ell(hx + 7.4, hy + 6.8, 3.6, 3.8),
                    Ell(hx - 2.7, hy + 15.4, 2.5, 3.2), Ell(hx + 2.7, hy + 15.4, 2.5, 3.2),
                    Ell(hx - 2.7, hy + 19.0, 1.6, 1.8), Ell(hx + 2.7, hy + 19.0, 1.6, 1.8)], EARTH_BEARD, sh=(1.1, 1.2))
        for s in (-1, 1):
            cel(draw, RRect(hx + s * 2.7 - 1.9, hy + 16.4, hx + s * 2.7 + 1.9, hy + 18.0, 0.6), GOLD, sh=(0.0, 0.5),
                lw=0.7)
        # the moustache over it
        blob(draw, [Ell(hx - 3.1, hy + 6.7, 3.5, 1.9), Ell(hx + 3.1, hy + 6.7, 3.5, 1.9)], lit(EARTH_BEARD, 0.5),
             sh=(0.0, 0.6), lw=0.8)
    # stone helm: a pair of stubby horns, a crack, moss; a darker band round its brim
    if d:
        # the far horn, forward of the near one (the head is turned a little to the camera)
        hb = (hx + d * 5.2, hy - ry + 3.0)
        cel(draw, Limb([hb, (hb[0] + d * 1.8, hb[1] - 3.8), (hb[0] + d * 0.6, hb[1] - 7.2)], [1.9, 1.3, 0.4]),
            shade(EARTH_HORN, 0.9), sh=(0.5, 0.5))
    else:
        for s in (-1, 1):
            hb = (hx + s * (rx - 1.0), hy - ry + 3.6)
            cel(draw, Limb([hb, (hb[0] + s * 3.4, hb[1] - 3.0), (hb[0] + s * 4.0, hb[1] - 6.0)], [2.0, 1.4, 0.4]),
                EARTH_HORN, sh=(0.5, 0.5))
    dome = arc_pts(hx - d * 0.4, hy - 0.6, rx + 1.4, ry + 0.8, 182, 358, 26)
    cel(draw, Poly(dome + [(hx + rx + 1.6, hy - 1.4), (hx - rx - 1.6, hy - 1.4)]), EARTH_STONE, sh=(1.4, 1.0),
        hi=(0.7, 0.7),
        regions=[(Poly([(hx - rx - 3, hy - 3.8), (hx + rx + 3, hy - 3.8), (hx + rx + 3, hy), (hx - rx - 3, hy)]),
                  shade(EARTH_STONE, 1.3))])
    stroke(draw, [(hx - 1.2, hy - ry - 0.8), (hx + 0.8, hy - ry + 2.6), (hx - 0.8, hy - ry + 4.6), (hx + 0.4, hy - ry + 6.4)],
           0.8, shade(EARTH_STONE, 2.2))
    cel(draw, Poly([(hx - rx * 0.6, hy - ry + 0.2), (hx - rx * 0.1, hy - ry - 0.8), (hx + rx * 0.3, hy - ry + 0.6),
                    (hx - rx * 0.2, hy - ry + 1.8)]), EARTH_MOSS, sh=None, line=False)
    if d:
        # the near horn, on the side of the dome
        hb = (hx - d * 1.6, hy - ry + 5.0)
        cel(draw, Limb([hb, (hb[0] - d * 2.4, hb[1] - 4.6), (hb[0] - d * 1.2, hb[1] - 8.8)], [2.2, 1.5, 0.4]),
            EARTH_HORN, sh=(0.5, 0.5))
    # rock fists, oversized
    for side in _sides(r):
        h = hand_at(r, side)
        _rock(draw, h[0], h[1], 4.0, 3.5, moss=False)


# ===================================================================
# WINDWALKER (16) -- mint and white, a sash the wind carries, a fan
# ===================================================================

WIND_ROBE = (226, 246, 238)
WIND_MINT = (96, 206, 170)
WIND_HAIR = (160, 232, 206)
WIND_DEEP = (44, 150, 126)


def _wind_ring(draw, cx, cy, rx, ry, frame):
    """Two gusts chasing each other round her feet, an eighth of a turn a frame, so the ring
    turns steadily through the walk. It lies on the ground and is drawn before her: the feet
    stand in it."""
    for k in (0, 1):
        a0 = frame * 45.0 + k * 180.0 + 20.0
        pts, ws = [], []
        for i in range(13):
            t = i / 12.0
            a = math.radians(a0 + t * 125.0)
            pts.append((cx + rx * math.cos(a), cy + ry * math.sin(a)))
            ws.append(0.2 + 0.75 * math.sin(math.pi * t ** 0.8))
        cel(draw, Limb(pts, ws, cap=True, cap0=True), (244, 255, 250), sh=None, line_color=shade(WIND_MINT, 0.8),
            lw=0.5)


def _wind_ribbon(draw, x0, y0, sx, wave, length, drop, ph0=0.0):
    """One tail of her sash streaming out along sx (-1 or 1): a soft S the wind lifts and drops."""
    pts = []
    for i in range(9):
        t = i / 8.0
        pts.append((x0 + sx * length * t, y0 + drop * t + math.sin(t * 6.0 + wave * 1.4 + ph0) * 2.3 * t ** 0.6))
    cel(draw, Limb(pts, [0.9, 1.1, 1.15, 1.15, 1.1, 1.1, 1.05, 0.95, 0.5]), WIND_MINT, sh=(0.0, 0.5), lw=0.5)


def _wind_tail(draw, pts, tie, tie_r):
    """The ponytail: thin at its tie, full below it, curling to a point."""
    cel(draw, Limb(pts, [1.7, 3.5, 3.2, 1.8, 0.4]), WIND_HAIR, sh=(0.9, 0.9))
    if tie is not None:
        cel(draw, Ell(tie[0], tie[1], tie_r[0], tie_r[1]), WIND_DEEP, sh=None)


def draw_windwalker(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=0.92)
    r0 = rig(ox, oy, direction, frame, build=0.92, step=0)      # the arm with the fan does not swing
    d = r.d
    cx = r.cx
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    fan_side = _hold_side(r)
    fan_reach = 0.3 if not d else 0.8
    wave = [0.0, 1.0, 0.0, -1.0][frame]
    sw = r.ph * 0.9
    style = HAIR_STYLES["swept"]
    _wind_ring(draw, cx, r.base_y - 3.2, 10.5, 3.0, frame)
    if not d and not r.back:
        _wind_tail(draw, [(hx + rx * 0.45, hy - ry * 0.75), (hx + rx + 3.4, hy - 2.6), (hx + rx + 4.2 + sw, hy + 4.0),
                          (hx + rx + 2.8 + sw * 1.5, hy + 9.0), (hx + rx + 4.6 + sw * 1.8, hy + 11.6)], None, None)
    oy0 = r.waist_y + 0.2
    if not r.back:
        # the sash's tails, streaming from the bow behind her
        s = -1 if not d else -d
        bx = cx + s * (4.0 if not d else 3.4)
        _wind_ribbon(draw, bx, oy0 + 0.6, s, wave, 15.0, -2.2)
        _wind_ribbon(draw, bx, oy0 + 2.8, s, wave, 10.5, 2.2, 2.2)
        # a loop of the bow showing past her waist
        cel(draw, Ell(bx + s * 1.6, oy0 + 1.6, 2.2, 2.0), WIND_MINT, sh=(0.4, 0.5), lw=0.6)
    if d:
        rig_arms(r, draw, WIND_ROBE, SKIN, layer="far", cuff=WIND_MINT)
    rig_legs(r, draw, WIND_MINT, (80, 150, 126))
    rig_robe(r, draw, WIND_ROBE, trim=WIND_MINT, flare=2.2, hem=r.hip_y + 3.4)
    # the sash: a wide band, knotted in front
    if d:
        band = RRect(cx - 4.6, oy0, cx + 4.7, oy0 + 3.4, 0.8)
    else:
        band = RRect(cx - r.hip_w - 0.7, oy0, cx + r.hip_w + 0.7, oy0 + 3.4, 0.8)
    cel(draw, band, WIND_DEEP, sh=(0.0, 0.8))
    if not r.back:
        cel(draw, Ell(cx + d * 3.2, oy0 + 1.7, 1.4, 1.4), (255, 255, 255), sh=None, lw=0.6)
    for side in _sides(r):
        if side == fan_side:
            rig_arms(r0, draw, WIND_ROBE, SKIN, layer="near", cuff=WIND_MINT, hands=False, sides=(side,),
                     reach=fan_reach)
        else:
            rig_arms(r, draw, WIND_ROBE, SKIN, layer="near", cuff=WIND_MINT, hands=False, sides=(side,))
    if r.back:
        head_skull(r, draw, SKIN)
        # the hair's underside at the nape, so no skin shows through its hem
        clipped(draw, Ell(hx, hy, rx - 0.9, ry - 0.9),
                lambda l: Ell(hx, hy + ry - 2.4, rx * 0.8, 3.6).draw(l, fill=shade(WIND_HAIR, 1.3)))
        rig_hair(r, draw, WIND_HAIR, style, skin=SKIN)
        ty = hy - ry * 0.5
        _wind_tail(draw, [(hx, ty), (hx - 2.4, hy + 2.4), (hx - 4.2 - sw, hy + 9.0), (hx - 2.6 - sw * 1.5, hy + 14.4),
                          (hx - 4.8 - sw * 1.8, hy + 17.0)], (hx - 0.2, ty + 0.4), (2.5, 1.5))
    else:
        rig_head(r, draw, SKIN, hair=WIND_HAIR, style=style, eye_color=(46, 160, 120), expression="smile")
    if d:
        tx, ty = hx - d * (rx + 1.2), hy - 4.6
        _wind_tail(draw, [(tx, ty), (hx - d * (rx + 6.2), hy - 1.6), (hx - d * (rx + 6.6 + sw * 0.6), hy + 5.4),
                          (hx - d * (rx + 4.2 + sw * 1.3), hy + 10.6), (hx - d * (rx + 6.4 + sw * 1.6), hy + 13.2)],
                   (tx - d * 0.5, ty + 0.3), (1.6, 2.3))
    for side in _sides(r):
        if side != fan_side:
            rig_hand(r, draw, side, SKIN)
            continue
        # an open war fan, ribbed, the hand closed over its pivot
        h = hand_at(r0, side, fan_reach)
        fx, fy = h[0], h[1] - 1.0
        base_a = -100 + (40 if (side > 0 and not d) or d > 0 else -40)
        pts = [(fx, fy)] + [(fx + math.cos(math.radians(base_a + t)) * 8.8, fy + math.sin(math.radians(base_a + t)) * 8.8)
                            for t in range(-50, 51, 10)]
        cel(draw, Poly(pts), (248, 252, 250), sh=None, line_color=shade(WIND_MINT, 1.4))
        for t in range(-50, 51, 20):
            stroke(draw, [(fx, fy), (fx + math.cos(math.radians(base_a + t)) * 8.4,
                                     fy + math.sin(math.radians(base_a + t)) * 8.4)], 0.5, WIND_MINT)
        arc = [(fx + math.cos(math.radians(base_a + t)) * 6.3, fy + math.sin(math.radians(base_a + t)) * 6.3)
               for t in range(-50, 51, 10)]
        stroke(draw, arc, 0.7, WIND_MINT)
        rig_hand(r0, draw, side, SKIN, reach=fan_reach)
    if r.back:
        # the sash's bow on her back, its tails streaming
        _wind_ribbon(draw, cx + 1.4, oy0 + 1.2, 1, wave, 15.0, -2.0)
        _wind_ribbon(draw, cx + 1.4, oy0 + 3.0, 1, wave, 10.5, 2.2, 2.2)
        blob(draw, [Ell(cx - 2.7, oy0 + 1.3, 2.7, 2.0), Ell(cx + 2.7, oy0 + 1.3, 2.7, 2.0)], WIND_MINT, sh=(0.5, 0.6),
             lw=0.6)
        cel(draw, Ell(cx, oy0 + 1.6, 1.3, 1.6), lit(WIND_MINT, 0.8), sh=None, lw=0.6)


# ===================================================================
# MAGMA KNIGHT (17) -- volcanic plate; the helm is a little volcano
#
# Three tones of one rock, so the plates read as plates: the rock
# itself (the cone, the breastplate, the pauldrons), its lit face (the
# face plate, gauntlets, boots) and the dark that shows between them
# (arms, legs). The lava is wide or it is not there: a crack under a
# unit across is gone at the size he is seen.
# ===================================================================

MAG_ROCK = (112, 70, 62)
MAG_LIT = (148, 98, 78)
MAG_DARK = (70, 46, 48)
MAG_PIT = (40, 24, 26)
MAG_LAVA = (255, 122, 40)
MAG_HOT = (255, 216, 96)
MAG_FIRE = ((240, 80, 36), (255, 160, 52), (255, 236, 130))


def _fissure(draw, pts, glow=0, w=1.3):
    """A crack with lava in it: a band tapering to both ends, hotter down its middle."""
    n = len(pts)
    Limb(pts, [0.3] + [w] * (n - 2) + [0.3], cap=False).draw(draw, fill=(255, 160, 52) if glow else MAG_LAVA)
    mid = lambda a, b: ((a[0] + b[0]) / 2.0, (a[1] + b[1]) / 2.0)
    stroke(draw, [mid(pts[0], pts[1])] + list(pts[1:-1]) + [mid(pts[-2], pts[-1])], 0.7,
           (255, 240, 170) if glow else MAG_HOT)


def _magma_helm(draw, r, glow, sway):
    """A great helm of volcanic rock: a bucket of a face plate under a cone with a crater in
    its top, erupting. The helm is the whole head: no skull shows under it."""
    hx, hy, rx, ry, d = r.hx, r.head_cy, r.head_rx, r.head_ry, r.d
    top = hy - ry - 1.4
    brim = hy - 0.6
    bot = hy + ry + 0.4
    ax = hx - d * 0.6
    flame(draw, ax, top + 2.4, 12.0, 11.5 + glow * 1.6, MAG_FIRE, sway=sway - d * 1.6)
    # the face plate, in the cone's shadow along its top
    fw = rx + 0.3
    plate = Poly([(hx - fw, brim - 2.0), (hx + fw, brim - 2.0), (hx + fw, bot - 3.6), (hx + fw - 3.2, bot),
                  (hx - fw + 3.2, bot), (hx - fw, bot - 3.6)])
    cel(draw, plate, MAG_LIT, sh=(1.6, 1.0),
        regions=[(Poly([(hx - fw, brim - 2.0), (hx + fw, brim - 2.0), (hx + fw, brim + 2.2), (hx - fw, brim + 2.2)]),
                  shade(MAG_LIT, 1.6))])
    # the cone: concave flanks up to a jagged rim, its left face lit
    cw = fw + 0.7
    cone = ([(ax - 5.2, top), (ax - 3.2, top + 1.0), (ax - 1.0, top - 0.2), (ax + 1.0, top + 1.0), (ax + 3.2, top - 0.2)] +
            bez((ax + 5.2, top), (ax + 8.4, hy - 5.8), (hx + cw, brim - 0.6), 6) +
            arc_pts(hx, brim + 0.2, cw - 0.2, 1.2, 0, 180, 10) +
            bez((hx - cw, brim - 0.6), (ax - 8.4, hy - 5.8), (ax - 5.2, top), 6)[:-1])
    facet = Poly([(ax - 5.4, top - 1.0), (ax - 2.6, top - 1.0), (ax - 6.4, brim - 1.6), (hx - cw - 1.0, brim - 1.6)])
    cel(draw, Poly(cone), MAG_ROCK, sh=(1.8, 1.2), regions=[(facet, MAG_LIT)])
    # lava running down from the crater
    if r.back:
        runs = ((-3.6, 6.6), (0.2, 15.0), (3.4, 8.6))
    elif d:
        runs = ((-d * 3.0, 5.4), (d * 1.2, 7.4))
    else:
        runs = ((-3.2, 5.4), (0.9, 8.2), (3.8, 4.6))
    for (u, ln) in runs:
        Limb([(ax + u, top + 0.4), (ax + u * 1.3, top + ln + glow * 0.8)], [1.5, 0.9]).draw(draw, fill=MAG_LAVA)
    if r.back:
        return
    # the visor: a slit across and, head-on, one down, with the eyes burning in it
    vx = hx + d * 3.0
    w = rx - 2.2 if not d else rx * 0.62
    slit = [RRect(vx - w, hy + 1.0, vx + w, hy + 4.6, 1.5)]
    if not d:
        slit.append(RRect(hx - 0.9, hy + 3.6, hx + 0.9, bot - 2.2, 0.6))
    blob(draw, slit, MAG_PIT, sh=None)
    for s in ((-1, 1) if not d else (d,)):
        ex = vx + s * 4.2 if not d else vx + d * 2.6
        Ell(ex, hy + 2.8, 2.5, 1.35).draw(draw, fill=MAG_HOT if glow else MAG_LAVA)


def draw_magmaknight(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=1.24, head=0.96)
    d = r.d
    sword_side = _hold_side(r)
    glow = [0, 1, 0, 1][frame]
    sway = [0.0, 1.0, 0.0, -1.0][frame]
    if d:
        rig_arms(r, draw, MAG_DARK, MAG_LIT, layer="far", hand_r=2.5)
    rig_legs(r, draw, MAG_DARK, MAG_LIT, width=1.1)
    rig_torso(r, draw, MAG_ROCK)
    # the breastplate split by a fissure
    cx = r.cx
    if d:
        _fissure(draw, [(cx + d * 2.4, r.sh_y - 0.4), (cx + d * 4.0, r.sh_y + 1.8), (cx + d * 2.8, r.sh_y + 3.6),
                        (cx + d * 4.0, r.waist_y + 0.4)], glow, 1.0)
    else:
        m = -1 if r.back else 1
        _fissure(draw, [(cx - m * 1.6, r.sh_y - 1.6), (cx + m * 1.3, r.sh_y + 1.4), (cx - m * 1.1, r.sh_y + 3.8),
                        (cx + m * 0.9, r.waist_y + 0.8)], glow)
        _fissure(draw, [(cx + m * 1.3, r.sh_y + 1.4), (cx + m * 3.8, r.sh_y + 2.4), (cx + m * 5.4, r.sh_y + 4.4)], glow, 0.9)
    rig_belt(r, draw, MAG_DARK, buckle=MAG_LAVA)
    rig_arms(r, draw, MAG_DARK, MAG_LIT, layer="near", hands=False)
    # pauldrons of jagged rock, lava welling out from under them
    for side in _sides(r):
        sx, sy = r.shoulder(side)
        px = sx + (side * 1.4 if not d else 0.0)
        m = side if not d else -d
        for (u, ln) in ((-2.6, 3.0 + glow), (1.8, 4.4 - glow)):
            Poly([(px + m * u - 1.1, sy + 1.0), (px + m * u + 1.1, sy + 1.0), (px + m * u, sy + 1.0 + ln)]).draw(
                draw, fill=MAG_LAVA)
        cel(draw, Poly([(px - m * 5.4, sy + 1.6), (px - m * 4.8, sy - 2.4), (px - m * 2.4, sy - 5.0), (px - m * 0.6, sy - 3.8),
                        (px + m * 1.4, sy - 5.4), (px + m * 4.4, sy - 2.8), (px + m * 5.4, sy + 1.6)]),
            MAG_ROCK, sh=(1.0, 1.0), hi=(1.1, 1.1), hi_tone=MAG_LIT)
    _magma_helm(draw, r, glow, sway)
    # molten greatsword, carried low and out to the side, its point clear of the ground
    h = hand_at(r, sword_side)
    ang = 90 - sword_side * 48 if not d else 90 - d * 52
    blade(draw, h[0], h[1], ang, length=14.0, width=2.2, color=MAG_LAVA, hilt=MAG_LIT, grip=MAG_PIT,
          guard=3.4, flip=sword_side if not d else d)
    stroke(draw, xform([(2.2, 0.0), (12.6, 0.0)], h[0], h[1], ang), 1.0, (255, 240, 170) if glow else MAG_HOT)
    if glow:
        tip = xform([(15.0, 0.0)], h[0], h[1], ang)[0]
        sparkle(draw, tip[0], tip[1], 2.0, MAG_HOT)
    for side in _sides(r):
        rig_hand(r, draw, side, MAG_LIT, hand_r=2.5)


# ===================================================================
# FROSTBITE (18) -- an ice imp: a mohawk of icicles, long ears, a
# fanged grin, an ice dagger in each fist
# ===================================================================

FROST_SKIN = (164, 216, 246)
FROST_FUR = (246, 250, 255)
FROST_FUR_SH = (200, 216, 240)
FROST_HIDE = (66, 94, 164)
FROST_ICE = (214, 242, 255)
FROST_LASH = (34, 52, 104)
FROST_MOUTH = (32, 44, 96)


def _frost_dagger(draw, r, side, far=False):
    """A shard of ice gripped point down and outward (forward, in profile), clear of the body."""
    h = hand_at(r, side)
    ang = 90 - (side * 44 if not r.d else r.d * 50)
    crystal(draw, h[0], h[1] + 0.4, ang, 9.6, 3.3, color=shade(FROST_ICE, 0.9) if far else FROST_ICE)


def _frost_grin(draw, r):
    """A wide mouth full of teeth: dark, so the fangs show against the pale face."""
    _, _, _, mx, my = face_anchor(r)
    d = r.d
    if d:
        a, b = (mx - d * 2.8, my - 0.6), (mx + d * 3.3, my - 2.0)
        shape = Poly(bez(a, (mx + d * 0.6, my - 0.4), b, 6) + bez(b, (mx + d * 1.2, my + 2.8), a, 6)[1:-1])
        teeth = Poly([(a[0], my - 1.0), (b[0], my - 2.4), (mx + d * 2.5, my - 0.2), (mx + d * 1.6, my - 1.2),
                      (mx + d * 0.6, my + 0.5), (mx - d * 0.3, my - 0.8), (mx - d * 1.2, my + 0.3)])
    else:
        a, b = (mx - 3.7, my - 1.5), (mx + 3.7, my - 1.5)
        shape = Poly(bez(a, (mx, my - 0.1), b, 8) + bez(b, (mx, my + 3.6), a, 8)[1:-1])
        teeth = Poly([(mx - 3.4, my - 1.8), (mx + 3.4, my - 1.8), (mx + 2.5, my + 0.9), (mx + 1.6, my - 0.5),
                      (mx + 0.8, my + 0.5), (mx, my - 0.3), (mx - 0.8, my + 0.5), (mx - 1.6, my - 0.5),
                      (mx - 2.5, my + 0.9)])
    cel(draw, shape, FROST_MOUTH, sh=None, regions=[(teeth, (255, 255, 255))], lw=0.7)


def draw_frostbite(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=0.9, head=1.0)
    d = r.d
    cx = r.cx
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    if d:
        _frost_dagger(draw, r, -d, far=True)
        rig_arms(r, draw, FROST_SKIN, FROST_SKIN, layer="far")
    rig_legs(r, draw, FROST_SKIN, shade(FROST_SKIN, 1.4), bare=True)
    rig_torso(r, draw, FROST_SKIN)
    # a ragged kilt of dark hide under a fur waistband
    top, hem = r.waist_y - 0.2, r.hip_y + 2.0
    sw = r.ph * 0.6
    if d:
        kilt = [(cx - d * 4.9, top), (cx + d * 5.1, top), (cx + d * 5.9 + sw, hem), (cx + d * 3.6 + sw, hem + 2.8),
                (cx + d * 1.6 + sw, hem + 0.5), (cx - d * 0.8 + sw, hem + 3.0), (cx - d * 3.2 + sw, hem + 0.5),
                (cx - d * 5.9 + sw, hem + 2.6)]
        band = [Ell(cx + u, top + 0.7, 2.4, 1.6) for u in (-3.2, 0.0, 3.2)]
    else:
        w = r.hip_w + 1.0
        kilt = [(cx - w, top), (cx + w, top), (cx + w + 1.0 + sw, hem), (cx + 5.6 + sw, hem + 2.8), (cx + 3.4 + sw, hem + 0.5),
                (cx + 1.2 + sw, hem + 3.2), (cx - 1.2 + sw, hem + 0.7), (cx - 3.4 + sw, hem + 3.0),
                (cx - 5.4 + sw, hem + 0.5), (cx - w - 1.0 + sw, hem + 2.6)]
        band = [Ell(cx + u, top + 0.7, 2.3, 1.6) for u in (-5.6, -2.8, 0.0, 2.8, 5.6)]
    cel(draw, Poly(kilt), FROST_HIDE, sh=(1.0, 0.8))
    blob(draw, band, FROST_FUR, sh=(0.5, 0.7), tone=FROST_FUR_SH)
    # the fur ruff round his neck
    blob(draw, [Ell(cx - 4.6, r.sh_y - 1.5, 3.6, 2.4), Ell(cx, r.sh_y - 0.8, 4.2, 2.6), Ell(cx + 4.6, r.sh_y - 1.5, 3.6, 2.4)]
         if not d else [Ell(cx - d * 0.4, r.sh_y - 1.3, 5.0, 2.6)], FROST_FUR, sh=(0.6, 0.8), tone=FROST_FUR_SH)
    rig_arms(r, draw, FROST_SKIN, FROST_SKIN, layer="near", hands=False)
    # long ears: head-on they stand out from behind the skull
    inner = shade(FROST_SKIN, 1.3)
    if not d:
        for s in (-1, 1):
            ex = hx + s * (rx - 1.8)
            cel(draw, Poly([(ex, hy - 1.6), (ex + s * 6.6, hy - 4.4), (ex + s * 0.8, hy + 3.8)]), FROST_SKIN, sh=(0.3, 0.4),
                regions=[(Poly([(ex + s * 1.2, hy - 0.4), (ex + s * 5.0, hy - 3.4), (ex + s * 1.4, hy + 2.6)]), inner)])
    head_skull(r, draw, FROST_SKIN, ears=False)
    if d:
        # in profile the near ear lies along the head and sweeps up and back past it
        tip = (hx - d * (rx + 4.4), hy - 5.8)
        ear = bez((hx - d * 3.0, hy - 2.6), (hx - d * 9.0, hy - 6.4), tip, 6) + \
            bez(tip, (hx - d * 9.4, hy - 0.6), (hx - d * 4.4, hy + 3.4), 6)[1:]
        cel(draw, Poly(ear), FROST_SKIN, sh=None,
            regions=[(Poly([(hx - d * 4.4, hy + 0.8), (hx - d * (rx - 2.4), hy - 2.6), (hx - d * 4.6, hy + 2.6)]), inner)])
    if not r.back:
        head_face(r, draw, FROST_SKIN, iris=(26, 104, 204), mood="sharp", mouth=None, blush=False, brows=False,
                  lash=FROST_LASH)
        _frost_grin(draw, r)
    # the icicles: a fan across the crown head-on, a ridge over the skull from the side and behind
    if d:
        for (phi, ln) in ((-20, 8.0), (4, 10.4), (27, 9.0), (48, 6.0)):
            a = math.radians(phi)
            crystal(draw, hx - d * math.sin(a) * (rx - 2.2), hy - math.cos(a) * (ry - 2.2), -90 - d * (phi + 10), ln, 3.5,
                    color=FROST_ICE)
    else:
        for (u, a, ln) in ((-6.6, -122, 6.6), (-3.4, -105, 9.2), (0.0, -90, 11.2), (3.4, -75, 9.2), (6.6, -58, 6.6)):
            crystal(draw, hx + u, hy - ry + 3.6, a, ln, 3.6, color=FROST_ICE)
        if r.back:
            for (v, ln) in ((8.4, 6.6), (13.0, 5.6)):
                crystal(draw, hx, hy - ry + v, -90, ln, 3.5, color=FROST_ICE)
    for side in _sides(r):
        _frost_dagger(draw, r, side)
        rig_hand(r, draw, side, FROST_SKIN)


# ===================================================================
# SANDSTORM (19) -- turban and veil, desert robes, a scimitar
# ===================================================================

SAND = (214, 172, 108)          # the robe: a step deeper than sand, so he is not the Lagoon's colour
SAND_RED = (196, 60, 52)
SAND_TURBAN = (244, 230, 196)   # the turban's cloth, paler than the robe
SAND_FOLD = (204, 174, 124)     # every other winding of it

from sprite_base import gem  # noqa: E402


def _sm_wedge(ax, ay, a0, a1, rad=40.0):
    return Poly([(ax, ay)] + [(ax + rad * math.cos(math.radians(a)), ay + rad * math.sin(math.radians(a)))
                              for a in (a0, a1)])


def _sm_turban(draw, r):
    """A wound turban, wider than the head and down over the ears and the nape: windings fan
    out from the jewel in two tones, which is what says "wound cloth" at 77px (two hairlines on
    a plain dome said mushroom). Its brim rises to a point over the brow so the eyes are clear."""
    hx, hy, rx, ry, d = r.hx, r.head_cy, r.head_rx, r.head_ry, r.d
    if r.back:
        shape = Poly(arc_pts(hx, hy - 4.6, rx + 3.2, ry + 0.6, 150, 390, 40) +
                     [(hx + 10.5, hy + 5.4), (hx, hy + 7.2), (hx - 10.5, hy + 5.4)])
        ax, ay = hx + 10.0, hy + 3.4        # where the end is tucked in, on his right
        bands = [_sm_wedge(ax, ay, a, a + 20) for a in (186, 226, 266)]
    elif d:
        local = arc_pts(-1.2, -4.6, rx + 2.9, ry + 0.6, 6.8, -208, 40) + \
            [(-12.6, 5.2), (-9.0, 6.8), (-4.6, 4.8), (-1.0, 0.2), (4.0, -3.0), (rx + 0.6, -3.4)]
        shape = Poly([(hx + d * u, hy + v) for (u, v) in local])
        ax, ay = hx + d * (rx - 0.5), hy - 3.6
        bands = [_sm_wedge(ax, ay, a if d > 0 else 180 - a, (a + 20) if d > 0 else 160 - a) for a in (190, 228, 262)]
    else:
        shape = Poly(arc_pts(hx, hy - 4.6, rx + 3.2, ry + 0.6, 150, 390, 40) +
                     [(hx + 11.0, hy - 0.4), (hx + 5.5, hy - 3.0), (hx, hy - 6.2), (hx - 5.5, hy - 3.0), (hx - 11.0, hy - 0.4)])
        ax, ay = hx, hy - 6.2
        bands = [_sm_wedge(ax, ay, a, a + 19) for a in (-31, -72, 192, 233)]
    cel(draw, shape, SAND_TURBAN, sh=(1.5, 1.2), tone=shade(SAND_FOLD, 0.5), regions=[(b, SAND_FOLD) for b in bands])


def _sm_scimitar(draw, x, y, ang, flip):
    """A scimitar gripped at (x, y): the blade sweeps up from the grip and broadens to a clipped
    point. The kit's curved sword at this size is a straight knife."""
    spine = [(0.4, -0.9), (4.0, -1.5), (8.0, -3.0), (11.4, -5.4), (13.4, -8.0)]
    edge = [(13.9, -6.0), (13.3, -3.8), (11.6, -1.2), (8.2, 0.9), (4.0, 1.5), (0.4, 1.0)]
    mid = [(0.4, 0.1), (4.0, 0.1), (8.0, -0.9), (11.4, -3.1), (13.6, -6.9)]
    cel(draw, Poly(xform(spine + edge, x, y, ang, 1.0, flip)), STEEL, sh=None,
        regions=[(Poly(xform(mid + edge, x, y, ang, 1.0, flip)), shade(STEEL, 1.0))])
    stroke(draw, xform([(1.6, -0.6), (7.6, -2.1), (11.0, -4.3)], x, y, ang, 1.0, flip), 0.55, lit(STEEL, 1.4))
    cel(draw, Limb(xform([(-3.6, 0.0), (-0.2, 0.0)], x, y, ang, 1.0, flip), [0.95, 0.95]), SAND_RED, sh=None)
    px, py = xform([(-4.2, 0.3)], x, y, ang, 1.0, flip)[0]
    cel(draw, Ell(px, py, 1.3, 1.3), GOLD, sh=None)
    cel(draw, Poly(xform([(-0.6, -2.7), (0.7, -2.7), (0.7, 2.7), (-0.6, 2.7)], x, y, ang, 1.0, flip)), GOLD, sh=None)


def _sm_tail(draw, pts, color, w=(1.5, 1.3, 0.8)):
    cel(draw, Limb(pts, list(w)), color, sh=(0.5, 0.3))


def draw_sandstorm(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame)
    d = r.d
    cx = r.cx
    sw_side = _hold_side(r)
    sway = [0.0, 1.0, 0.0, -1.0][frame]
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    # the sword hand: at his side head-on; in profile held forward and steady
    reach, out = (0.5, 3.0 + r.ph * 2.4) if d else (0.0, 1.6)

    def held(side):
        return (reach, out) if side == sw_side else (0.0, 0.0)

    # the turban's loose end, hanging behind his right ear (the back of the head in profile)
    ts = (1 if r.back else -1) if not d else -d
    tx = hx + ts * (rx + 0.6)
    _sm_tail(draw, [(tx, hy + 1.0), (tx + ts * 1.2 + sway * 0.5, hy + 6.0), (tx + ts * 1.6 - sway * 0.8, hy + 11.0)],
             SAND_TURBAN, (1.7, 1.5, 0.9))
    if d:
        rig_arms(r, draw, SAND, SKIN_TAN, layer="far")
    rig_legs(r, draw, shade(SAND, 1.6), (132, 88, 56))
    rig_robe(r, draw, SAND, trim=SAND_RED, flare=3.0, split=True, hem=r.base_y - 4.6)
    rig_belt(r, draw, SAND_RED, buckle=GOLD)
    # the sash's two ends at his right hip (the front hip in profile), swinging against the stride
    sx0 = cx + (ts * 4.6 if not d else d * 2.2)
    sy0 = r.waist_y + 2.8
    _sm_tail(draw, [(sx0, sy0), (sx0 - sway * 0.5, sy0 + 3.6), (sx0 + sway * 0.9, sy0 + 7.2)], SAND_RED, (2.0, 1.9, 1.2))
    for side in _sides(r):
        rc, oc = held(side)
        rig_arms(r, draw, SAND, SKIN_TAN, layer="near", sides=(side,), hands=False, reach=rc, out=oc)
    head_skull(r, draw, SKIN_TAN, ears=False)
    if r.back:
        # the veil's band across the nape, under the turban's edge
        cel(draw, RRect(hx - 10.2, hy + 4.2, hx + 10.2, hy + 9.6, 2.4), SAND_RED, sh=(0.8, 0.8))
    else:
        head_face(r, draw, SKIN_TAN, iris=(150, 104, 40), mood="sharp", mouth=None, blush=False, brows=True,
                  brow_color=(90, 60, 40))
        # the veil: from under the eyes round the jaw, hanging to a point at the throat
        if d:
            _sm_tail(draw, [(hx - d * 3.0, hy + 7.0), (hx - d * (rx - 0.6), hy + 6.0)], SAND_RED, (1.6, 1.6))
            veil = Poly([(hx - d * 4.4, hy + 5.8), (hx + d * 6.0, hy + 5.6), (hx + d * (rx + 0.1), hy + 5.6),
                         (hx + d * (rx + 0.5), hy + 8.2), (hx + d * (rx - 2.4), hy + 11.0), (hx + d * 4.4, hy + 14.0),
                         (hx - d * 1.6, hy + 11.2), (hx - d * 5.0, hy + 9.4)])
        else:
            veil = Poly([(hx - rx + 0.2, hy + 5.9), (hx - 4.0, hy + 5.6), (hx, hy + 5.2), (hx + 4.0, hy + 5.6),
                         (hx + rx - 0.2, hy + 5.9), (hx + rx - 1.8, hy + 9.8), (hx + 4.2, hy + 12.8), (hx, hy + 15.0),
                         (hx - 4.2, hy + 12.8), (hx - rx + 1.8, hy + 9.8)])
        cel(draw, veil, SAND_RED, sh=(1.0, 1.0))
    _sm_turban(draw, r)
    if not r.back:
        gx, gy = (hx, hy - 6.6) if not d else (hx + d * (rx - 1.8), hy - 5.6)
        cel(draw, Ell(gx, gy, 2.2, 2.4), GOLD, sh=None)
        gem(draw, gx, gy, 1.4, SAND_RED)
    if r.back or d:
        # where the veil is tied: a knot at the nape and two ends on the wind
        kx, ky = (hx, hy + 7.6) if not d else (hx - d * (rx - 0.2), hy + 6.2)
        k = -d if d else 1
        for s2, ln in ((-1, 9.0), (1, 7.4)):
            if d:
                pts = [(kx, ky), (kx + k * 3.4, ky + 2.0 + s2 * 1.4 + sway * 0.8), (kx + k * 6.4, ky + 5.2 + s2 * 2.0 - sway * 0.9)]
            else:
                pts = [(kx + s2 * 0.6, ky), (kx + s2 * 2.2 + sway * 0.7, ky + ln * 0.5), (kx + s2 * 3.2 - sway * 0.9, ky + ln)]
            _sm_tail(draw, pts, SAND_RED)
        cel(draw, Ell(kx, ky, 2.3, 1.9), SAND_RED, sh=(0.5, 0.5))
    # the scimitar, raised: up and out at his side, or forward in profile
    h = hand_at(r, sw_side, reach, 0.0, out)
    if d:
        _sm_scimitar(draw, h[0], h[1], -10 if d > 0 else 190, d)
    else:
        _sm_scimitar(draw, h[0], h[1], -75 if sw_side > 0 else -105, -sw_side)
    for side in _sides(r):
        rc, oc = held(side)
        rig_hand(r, draw, side, SKIN_TAN, reach=rc, out=oc)


# ===================================================================
# THORNWEAVER (20) -- flower crown, leaf-hemmed dress, a blooming staff
# ===================================================================

THORN_DRESS = (86, 168, 86)
THORN_HAIR = (164, 96, 62)
THORN_PINK = (255, 136, 182)
THORN_WOOD = (132, 88, 56)
THORN_LEAF = (136, 204, 96)     # a young leaf: the light green of the hem and the wreath
THORN_GOLD = (255, 226, 120)


def _tw_flower(draw, x, y, k=1.0, rot=0.0):
    """A five-petalled bloom, one outlined mass with a yellow heart (2.8 units across at k = 1:
    petals drawn one by one at a unit each were five pink dots)."""
    petals = [Ell(x + math.cos(math.radians(rot + a)) * 1.55 * k, y + math.sin(math.radians(rot + a)) * 1.55 * k,
                  1.3 * k, 1.3 * k) for a in range(-90, 270, 72)]
    blob(draw, petals + [Ell(x, y, 1.2 * k, 1.2 * k)], THORN_PINK, sh=(0.45 * k, 0.55 * k), lw=0.6)
    Ell(x, y, 0.95 * k, 0.95 * k).draw(draw, fill=THORN_GOLD)


def _tw_wreath(draw, pts, flowers, leaves, k=0.95):
    """The flower crown: a leafy vine along `pts` with blooms and leaves at fractions of it."""
    cel(draw, Limb(pts, 0.85, True, True), THORN_DRESS, sh=None, lw=0.6)
    n = len(pts) - 1
    for (t, ang) in leaves:
        x, y = pts[int(round(t * n))]
        leaf(draw, x, y, ang, 4.0, 2.4, THORN_LEAF, vein=False)
    for t in flowers:
        x, y = pts[int(round(t * n))]
        _tw_flower(draw, x, y, k)


def _tw_staff(draw, xh, yh, k, base_y, top_y, sway):
    """A thorny branch through the hand at (xh, yh), leaning k units sideways for every unit up,
    taller than she is, a bloom on top. The lean is what keeps it off her face: upright in the
    hand it ran straight up through her eye in profile."""
    xb, yb = xh - k * (base_y - 1.0 - yh), base_y - 1.0
    xt, yt = xh + k * (yh - top_y), top_y
    cel(draw, Limb([(xb, yb), (xt, yt)], [1.2, 1.0]), THORN_WOOD, sh=(0.5, 0.0))
    s = 1 if k >= 0 else -1
    for i, t in enumerate((0.16, 0.42, 0.62, 0.8)):
        x, y = xb + (xt - xb) * t, yb + (yt - yb) * t
        o = s if i % 2 == 0 else -s
        cel(draw, Poly([(x + o * 0.5, y + 1.3), (x + o * 3.2, y - 1.7), (x + o * 0.5, y - 1.3)]),
            shade(THORN_WOOD, 0.8), sh=None, lw=0.6)
    bx, by = xt + sway * 0.4, yt - 0.6
    leaf(draw, bx - 0.6, by + 2.2, 138, 4.4, 2.8, THORN_LEAF, vein=False)
    leaf(draw, bx + 0.6, by + 2.2, 42, 4.4, 2.8, THORN_DRESS, vein=False)
    _tw_flower(draw, bx, by, 1.5, sway * 8.0)


def draw_thornweaver(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=0.94)
    d = r.d
    cx = r.cx
    st_side = _hold_side(r)
    sway = [0.0, 1.0, 0.0, -1.0][frame]
    # the staff hand: out at her side head-on; in profile held forward and steady, not swinging
    reach, out = (0.5, 3.0 + r.ph * 2.4) if d else (0.0, 2.4)

    def held(side):
        return (reach, out) if side == st_side else (0.0, 0.0)

    if d:
        rig_arms(r, draw, THORN_DRESS, SKIN, layer="far")
    rig_legs(r, draw, shade(THORN_DRESS, 1.0), THORN_WOOD)
    # the dress stops at the shin so her boots show the stride under the leaves
    hem = r.base_y - 6.4
    rig_robe(r, draw, THORN_DRESS, flare=3.6, hem=hem)
    # a hem of leaves, light and dark by turns
    us = (-9.0, -6.0, -3.0, 0.0, 3.0, 6.0, 9.0) if not d else (-d * 6.6, -d * 3.4, -d * 0.2, d * 3.0, d * 5.8)
    for i, u in enumerate(us):
        x, y = cx + u + r.ph * 0.8 * (1 if not d else -d), hem - 1.4
        tip = 4.4 + (i % 2) * 0.8
        cel(draw, Poly([(x - 1.9, y), (x - 1.7, y + tip * 0.45), (x, y + tip), (x + 1.7, y + tip * 0.45), (x + 1.9, y)]),
            THORN_LEAF if i % 2 == 0 else mix(THORN_LEAF, THORN_DRESS, 0.6), sh=(0.7, 0.0), lw=0.7)
    # a vine for a belt, a bloom for its buckle
    by = r.waist_y + 1.0
    cel(draw, RRect(cx - r.hip_w - 0.6, by, cx + r.hip_w + 0.6, by + 2.2, 0.6) if not d else
        RRect(cx - 4.5, by, cx + 4.6, by + 2.2, 0.6), THORN_WOOD, sh=(0.0, 0.6))
    if not r.back:
        _tw_flower(draw, cx + d * 3.4, by + 1.1, 0.72)
    for side in _sides(r):
        rc, oc = held(side)
        rig_arms(r, draw, THORN_DRESS, SKIN, layer="near", sides=(side,), hands=False, reach=rc, out=oc)
        # a vine bracelet above the wrist
        _, e, w, _ = arm_pts(r, side, rc, 0.0, oc)
        px, py = e[0] + (w[0] - e[0]) * 0.6, e[1] + (w[1] - e[1]) * 0.6
        stroke(draw, [(px - 1.4, py - 0.3), (px + 1.4, py + 0.3)], 0.9, THORN_WOOD)
    rig_head(r, draw, SKIN, hair=THORN_HAIR, style="bob", eye_color=(60, 150, 70), expression="smile", hat=True,
             brows=False)
    # the flower crown: worn tipped back, so it arches over the brow and dips across the back
    hx, hy, rx = r.hx, r.head_cy, r.head_rx
    if r.back:
        band = arc_pts(hx, hy - 9.0, rx + 1.7, 6.0, 158, 22, 18)
        # where it is tied: two vine ends down the back of her hair, a leaf on each
        kx, ky = band[9]
        for s2 in (-1, 1):
            end = (kx + s2 * 2.6 + sway * 0.7, ky + 6.4 + (0.8 if s2 > 0 else 0.0))
            cel(draw, Limb([(kx, ky), (kx + s2 * 1.6, ky + 3.0), end], 0.7), THORN_DRESS, sh=None, lw=0.6)
            leaf(draw, end[0], end[1] - 0.4, 90 - s2 * 28, 4.2, 2.6, THORN_LEAF, vein=False)
        _tw_wreath(draw, band, (0.2, 0.5, 0.8), ((0.34, 110), (0.66, 70)))
    elif d:
        band = [(hx + d * u, hy + v) for (u, v) in bez((12.9, -5.0), (0.0, -11.6), (-13.8, -2.2), 18)]
        end = (band[-1][0] - d * 1.6 + sway * 0.6, band[-1][1] + 5.6)
        cel(draw, Limb([band[-1], end], 0.7), THORN_DRESS, sh=None, lw=0.6)
        leaf(draw, end[0], end[1] - 0.4, 90 + d * 24, 4.2, 2.6, THORN_LEAF, vein=False)
        _tw_wreath(draw, band, (0.2, 0.62), ((0.42, -90 - d * 14), (0.84, -90 - d * 40)))
    else:
        band = arc_pts(hx, hy - 2.2, rx + 1.7, 6.2, 198, 342, 18)
        _tw_wreath(draw, band, (0.14, 0.5, 0.86), ((0.32, -108), (0.68, -72)))
    # the staff, in every facing: leaning out past her hair head-on, forward of her face in profile
    h = hand_at(r, st_side, reach, 0.0, out)
    if d:
        _tw_staff(draw, h[0], h[1], d * 0.466, r.base_y, h[1] - 29.0, sway)
    else:
        _tw_staff(draw, h[0], h[1], st_side * 0.141, r.base_y, r.base_y - 46.6, sway)
    for side in _sides(r):
        rc, oc = held(side)
        rig_hand(r, draw, side, SKIN, reach=rc, out=oc)


# ===================================================================
# CLOUDRUNNER (21) -- cloud-puff hair, riding a little cloud
# ===================================================================

CLOUD_TUNIC = (104, 176, 242)
CLOUD_WHITE = (250, 252, 255)
CLOUD_SCARF = (255, 206, 72)
CLOUD_SH = (164, 192, 236)      # a cloud's underside: what holds the white on snow and sand
CLOUD_LINE = (96, 128, 190)     # cloud is lined in sky blue: in the kit's slate, white reads as a wig

from sprite_base import limb_pts  # noqa: E402  (the scarf's swallowtail)


def _cr_lens(x, y, rx, ry, dx, dy, n=40):
    """The part of a round lump its own copy shifted by (dx, dy) still covers: its lit face."""
    def inside(p, ex, ey):
        return ((p[0] - ex) / rx) ** 2 + ((p[1] - ey) / ry) ** 2 <= 1.0
    a = [p for p in arc_pts(x, y, rx, ry, 0, 360, n)[:-1] if inside(p, x + dx, y + dy)]
    b = [p for p in arc_pts(x + dx, y + dy, rx, ry, 0, 360, n)[:-1] if inside(p, x, y)]
    mx, my = x + dx / 2.0, y + dy / 2.0
    return Poly(sorted(a + b, key=lambda p: math.atan2(p[1] - my, p[0] - mx)))


def _cr_puffs(draw, parts, sh=(1.2, 1.5), lw=0.9):
    """Cumulus: round lumps as one mass with one line, each lump keeping the shadow under its
    own curve, so the mass reads as heaped cloud and not as a white ball. `parts` go back to
    front: (x, y, rx, ry) is a lump, a Poly a flat lit piece."""
    shapes, regions = [], []
    for p in parts:
        if isinstance(p, Poly):
            shapes.append(p)
            regions.append((p, CLOUD_WHITE))
            continue
        x, y, rx, ry = p
        k = min(1.0, min(rx, ry) / 3.0)
        shapes.append(Ell(x, y, rx, ry))
        regions.append((shapes[-1], CLOUD_SH))
        regions.append((_cr_lens(x, y, rx, ry, -sh[0] * k, -sh[1] * k), CLOUD_WHITE))
    blob(draw, shapes, CLOUD_WHITE, sh=None, line_color=CLOUD_LINE, lw=lw, regions=regions)


def _cr_hair(draw, r):
    """Hair that is a cloud: round puffs heaped on the crown, a scalloped fringe, and the whole
    back of the head covered (the kit's curly style left a wedge of bare skull in profile)."""
    hx, hy, rx, ry, d = r.hx, r.head_cy, r.head_rx, r.head_ry, r.d
    top = [(-6.4, -9.6, 5.4, 4.8), (6.8, -9.2, 5.0, 4.4), (0.4, -12.0, 6.4, 5.4)]
    sides = [(-12.6, -3.0, 4.0, 4.6), (12.6, -3.0, 4.0, 4.6), (-12.2, 2.8, 3.0, 3.4), (12.2, 2.8, 3.0, 3.4)]
    if r.back:
        local = top + sides + [Ell(0.0, -1.0, rx + 1.4, ry + 0.4),
                               (-8.2, 6.4, 4.0, 3.8), (8.4, 6.2, 4.0, 3.8), (-2.8, 8.2, 4.2, 3.6), (3.0, 8.0, 4.2, 3.6),
                               (-5.2, -4.2, 6.6, 5.6), (5.6, -1.6, 6.0, 5.2), (-0.6, 3.4, 6.4, 4.6)]
        k = 1
    elif d:
        cap = arc_pts(-0.9, -0.8, rx + 1.4, ry + 1.6, 342, 150, 30) + \
            [(-9.6, 8.4), (-5.4, 7.6), (-3.6, 3.0), (-1.8, -1.6), (2.0, -4.2), (rx + 0.3, -4.6)]
        local = [(-5.6, -10.2, 5.6, 4.8), (4.6, -10.6, 5.4, 4.6), (-0.6, -12.4, 6.2, 5.2),
                 (-12.4, -3.6, 4.4, 4.8), (-12.0, 2.6, 3.8, 4.2), Poly(cap), (-8.6, 6.6, 3.8, 3.4),
                 (-6.4, -2.2, 6.2, 5.4), (-3.6, 1.4, 3.0, 3.6), (4.8, -5.3, 3.3, 3.1), (9.4, -5.5, 3.0, 2.8)]
        k = d
    else:
        fringe = [(-9.0, -4.8, 3.0, 2.9), (-4.6, -5.2, 3.5, 3.3), (0.0, -5.5, 3.3, 3.1), (4.6, -5.1, 3.6, 3.4),
                  (9.0, -4.7, 3.0, 2.9)]
        # the fringe's shadow on the brow
        clipped(draw, Ell(hx, hy, rx, ry), lambda l: [Ell(hx + u + 0.3, hy + v + 1.1, a, b).draw(l, fill=shade(SKIN))
                                                      for (u, v, a, b) in fringe])
        local = top + sides + [Poly(arc_pts(0.0, -0.8, rx + 1.4, ry + 1.6, 198, 342, 30))] + fringe
        k = 1
    parts = []
    for p in local:
        if isinstance(p, Poly):
            parts.append(Poly([(hx + k * u, hy + v) for (u, v) in p.pts]))
        else:
            parts.append((hx + k * p[0], hy + p[1], p[2], p[3]))
    _cr_puffs(draw, parts)


def _cr_tail(draw, pts, w0=1.5, w1=1.7):
    """A scarf's tail along `pts`, cut to a swallowtail so it is cloth and not a stick."""
    n = len(pts)
    edge = limb_pts(pts, [w0 + (w1 - w0) * i / float(n - 1) for i in range(n)], cap=False)
    (ax, ay), (bx, by) = pts[-2], pts[-1]
    ln = math.hypot(bx - ax, by - ay) or 1.0
    notch = (bx - (bx - ax) / ln * 2.0, by - (by - ay) / ln * 2.0)
    cel(draw, Poly(edge[:n] + [notch] + edge[n:]), CLOUD_SCARF, sh=(0.0, 0.7))


def _cr_stream(bx, y0, s, frame, length=13.6):
    """The tail on the wind: a wave that travels down it, so no frame is a straight line."""
    pts = [(bx, y0)]
    for i in range(1, 5):
        a = (0.45 + 0.3 * i) * math.sin(math.radians(100.0 * i - 90.0 * frame))
        pts.append((bx + s * length * i / 4.0, y0 - 0.3 * i + a))
    return pts


def _cr_arm(r, draw, side, lift, out):
    """An arm held out for balance: he is surfing, not standing in a queue."""
    s, e, w, h = arm_pts(r, side, 0.0, lift, out)
    near = r.near(side)
    cel(draw, Limb([s, e, w], [2.2, 2.0, 1.8]), CLOUD_TUNIC if near else shade(CLOUD_TUNIC, 0.6), sh=(0.8, 0.4))
    cel(draw, Ell(h[0], h[1], 2.2, 2.2), SKIN if near else shade(SKIN, 0.5), sh=(0.6, 0.6))


def _cr_cloud(draw, cx, by, d, frame):
    """The cloud he rides: wider than he is, heaped in front of his shins, its base no lower
    than his soles would be. In profile it sheds puffs behind that drift back and thin out."""
    k = d if d else 1
    wob = [0.0, 0.5, 0.0, -0.5][frame]
    parts = [(cx, by - 1.3, 12.4, 2.4),
             (cx - k * 8.0, by - 3.0 + wob, 4.0, 3.0), (cx + k * 9.4, by - 2.5 - wob, 3.2, 2.5),
             (cx - k * 2.0, by - 3.6 - wob, 4.8, 3.1), (cx + k * 4.4, by - 3.3 + wob, 4.4, 3.0)]
    if d:
        # one wisp at a time: it buds off the back, drifts and thins, and is gone by the idle frame
        dist, rise, rad = ((11.4, 1.2, 2.8), (15.8, 1.8, 2.6), (19.0, 2.6, 2.2), (21.2, 3.4, 1.8))[frame]
        wx, wy = cx - d * dist, by - rise
        wisp = [(wx, wy, rad * 1.15, rad * 0.8), (wx - d * rad * 0.95, wy + 0.4, rad * 0.75, rad * 0.58)]
        if frame:
            _cr_puffs(draw, wisp, sh=(0.5, 0.7), lw=0.6)
        else:
            parts.insert(0, wisp[0])
    _cr_puffs(draw, parts, sh=(0.9, 1.1))


def draw_cloudrunner(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, bob=[-1, -2, -2, -1][frame], step=0)
    d = r.d
    cx = r.cx
    wob = [0.0, 1.0, 0.0, -1.0][frame]
    y0 = r.sh_y - 1.6
    # the scarf's tail streaming behind
    if not r.back:
        s = -1 if not d else -d
        _cr_tail(draw, _cr_stream(cx + (s * 3.4 if not d else -d * 2.6), y0, s, frame))
    if d:
        _cr_arm(r, draw, -d, 2.6 - wob * 0.8, -7.0)
    rig_legs(r, draw, shade(CLOUD_TUNIC, 1.2), CLOUD_WHITE, bare=True)
    rig_torso(r, draw, CLOUD_TUNIC, bottom=r.hip_y + 2.0)
    cel(draw, Poly([(cx - r.hip_w - 0.4, r.hip_y + 0.2), (cx + r.hip_w + 0.4, r.hip_y + 0.2),
                    (cx + r.hip_w + 0.6, r.hip_y + 2.2), (cx - r.hip_w - 0.6, r.hip_y + 2.2)]) if not d else
        Poly([(cx - 4.6, r.hip_y + 0.2), (cx + 4.8, r.hip_y + 0.2), (cx + 5.0, r.hip_y + 2.2), (cx - 4.8, r.hip_y + 2.2)]),
        CLOUD_WHITE, sh=None)
    rig_belt(r, draw, CLOUD_SCARF, buckle=CLOUD_WHITE)
    # arms out for balance, see-sawing as he rides
    for side in _sides(r):
        if d:
            _cr_arm(r, draw, side, 2.6 + wob * 0.8, 3.6)
        else:
            _cr_arm(r, draw, side, 2.4 + side * wob * 1.2, 2.6)
    # scarf round the neck
    cel(draw, RRect(cx - 6.8, y0 - 1.4, cx + 6.8, y0 + 1.8, 1.6) if not d else RRect(cx - 5.0, y0 - 1.4, cx + 5.4, y0 + 1.8, 1.6),
        CLOUD_SCARF, sh=(0.6, 0.6))
    rig_head(r, draw, SKIN, eye_color=(70, 150, 230), expression="smile", ears=False, brows=False)
    _cr_hair(draw, r)
    if r.back:
        # from behind: the knot at the nape and both tails down his back
        ky = y0 + 1.4
        for s2, ln in ((-1, 10.0), (1, 8.4)):
            _cr_tail(draw, [(cx + s2 * 0.6, ky), (cx + s2 * 2.4 + wob * 0.7, ky + ln * 0.5),
                            (cx + s2 * 3.6 - wob * 0.9, ky + ln)], 1.4, 1.6)
        cel(draw, Ell(cx, ky, 2.4, 1.9), CLOUD_SCARF, sh=(0.5, 0.5))
    # the cloud he rides on, hiding his feet
    _cr_cloud(draw, cx, r.base_y, d, frame)


# ===================================================================
# INFERNO (22) -- a living flame
# ===================================================================

INF_COLORS = ((226, 62, 38), (255, 142, 44), (255, 226, 110))


def draw_inferno(draw, ox, oy, direction, frame):
    """A tall flame-person: a head that is a flame with a face in it, shoulders, long arms with
    a fire in each fist, and a body that tapers to a wisp where legs would be. From behind it is
    the flame's cooler outside: redder, no face, no hot core."""
    bob = [0, -1, -2, -1][frame]
    base = oy + 54 + bob
    cx = ox + 32
    d = {DOWN: 0, UP: 0, LEFT: -1, RIGHT: 1}[direction]
    back = direction == UP
    sway = [0.0, 1.2, 0.0, -1.2][frame]
    flick = [0.0, 1.0, 0.0, 1.0][frame]
    red, orange, yellow = INF_COLORS
    back_fire = (red, mix(red, orange, 0.45), mix(red, orange, 0.8))

    def arm(side, near):
        col = red if near else shade(red, 0.5)
        if d:
            # the near arm reaches ahead, the far one trails: a figure moving, not a dog begging
            fwd = d if near else -d
            sx, sy = cx + d * (1.0 if near else -1.6), base - 20.4
            ex, ey = cx + fwd * (9.4 if near else 8.4), base - 14.2 + (sway if near else -sway) * 0.7
            mid = ((sx + ex) / 2.0, (sy + ey) / 2.0 + 1.3)
        else:
            sx, sy = cx + side * 7.4, base - 20.4
            ex, ey = cx + side * 13.0, base - 13.4 + side * sway * 0.7
            mid = ((sx + ex) / 2.0 + side * 1.5, (sy + ey) / 2.0 - 0.6)
        cel(draw, Limb([(sx, sy), mid, (ex, ey)], [2.9, 2.5, 2.1]), col, sh=None)
        flame(draw, ex, ey + 2.6, 6.4, 8.6 + flick, INF_COLORS if not back else back_fire,
              sway=(side if not d else (1 if ex > cx else -1)) * 1.2 + sway * 0.4)

    # the wisp it tapers to instead of legs, trailing behind in profile
    tail = [(cx - d * 0.6, base - 12.0), (cx - d * 2.4 - sway * 1.0, base - 6.4),
            (cx - d * 6.4 + sway * 1.6 + (0.0 if d else 2.2), base - 1.5)]
    cel(draw, Limb(tail, [5.0, 3.4, 0.5]), red, sh=None)
    Limb([(tail[0][0], tail[0][1] + 1.0), (tail[1][0], tail[1][1] + 0.6)], [2.8, 1.0]).draw(
        draw, fill=orange if not back else back_fire[1])
    if d:
        arm(-d, False)
    else:
        arm(-1, True)
        arm(1, True)
    # the torso: wide at the shoulders, narrow at the waist
    if d:
        pts = [(cx - d * 6.0, base - 21.4), (cx + d * 5.0, base - 22.6), (cx + d * 7.6, base - 18.6), (cx + d * 5.4, base - 12.4),
               (cx + d * 3.6, base - 9.8), (cx - d * 4.2, base - 9.8), (cx - d * 6.2, base - 13.0), (cx - d * 7.8, base - 18.0)]
    else:
        pts = [(cx - 5.0, base - 23.0), (cx + 5.0, base - 23.0), (cx + 8.8, base - 21.2), (cx + 9.6, base - 18.0),
               (cx + 6.2, base - 12.6), (cx + 4.6, base - 9.8), (cx - 4.6, base - 9.8), (cx - 6.2, base - 12.6),
               (cx - 9.6, base - 18.0), (cx - 8.8, base - 21.2)]
    torso = Poly(pts)
    cel(draw, torso, red, sh=None)
    torso.scaled(0.6 if not back else 0.42, cx + d * 0.8, base - 16.4).draw(draw, fill=orange if not back else back_fire[1])
    if not back:
        # the hot core, a small flame in the chest: smaller and dimmer than the face
        Poly(flame_pts(cx + d * 1.8, base - 12.6, 4.6, 6.4, sway * 0.3)).draw(draw, fill=yellow)
    if d:
        arm(d, True)
    # the head: a tall flame, taller now there is room over it, with a face in it
    hy = base - 30.0
    hx = cx + d * 1.6
    crest = (hx - d * 0.8, hy + 10.0, 22.0, 34.0 + flick * 1.6, sway * 1.6 - d * 5.0)
    flame(draw, crest[0], crest[1], crest[2], crest[3], INF_COLORS if not back else back_fire, sway=crest[4])
    if back:
        return
    # the face, kept inside the flame's own outline (it used to bulge past it at the chin)
    fx = hx
    clipped(draw, Poly(flame_pts(*crest)).scaled(0.92, crest[0], hy + 3.0),
            lambda l: Ell(fx, hy + 2.4, 8.6 if not d else 8.2, 7.8).draw(l, fill=orange))
    e1, e2 = (fx - 3.8, fx + 3.8) if not d else (fx + d * 0.2, fx + d * 5.2)
    for side, ex in ((-1, e1), (1, e2)):
        w = 1.0 if not d or (ex - fx) * d < 3 else 0.72
        Poly([(ex - side * 2.6 * w, hy + 1.9), (ex - side * 0.8 * w, hy - 1.1), (ex + side * 2.6 * w, hy - 1.4),
              (ex + side * 1.9 * w, hy + 2.3), (ex - side * 0.9 * w, hy + 3.0)]).draw(draw, fill=(90, 20, 22))
        Ell(ex + side * 0.3, hy + 0.8, 1.1 * w, 1.1).draw(draw, fill=(255, 250, 220))
    mx = fx + d * 2.6
    Poly([(mx - 3.2, hy + 4.9), (mx + 3.2, hy + 4.9), (mx + 1.9, hy + 7.3), (mx - 1.9, hy + 7.3)]).draw(draw, fill=(90, 20, 22))
    for k in (-1.7, 0.0, 1.7):
        Poly([(mx + k - 0.6, hy + 4.9), (mx + k + 0.6, hy + 4.9), (mx + k, hy + 6.1)]).draw(draw, fill=(255, 240, 200))


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
GL_SHARD = (100, 198, 250)      # the crystals: a deeper cyan than the ice they grow from (in the
#                                 body's own pale blue they were nicks in his outline)
GL_BODY = (147, 202, 240)       # the trunk, a step deeper than the head and the fists
GL_SOCKET = (62, 118, 196)      # what the core sits in, so its glow has something dark round it
GL_MOUTH = (40, 78, 150)
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
        ly = base - (1.8 if fwd < 0 and not d else 0.0)
        _ice(draw, RRect(lx - 3.8, ly - 8.0, lx + 3.8, ly + 0.4, 2.0), GL_DEEP if near else shade(GL_DEEP, 0.6))

    def arm(s, near):
        col = GL_ICE if near else shade(GL_ICE, 0.6)
        fwd = -ph * s if not d else (-ph if near else ph)
        sx = cx + (s * 11.6 if not d else -d * 1.4 + s * 0.4)
        sy = base - (22.4 if not d else 20.4)
        fx = sx + (s * 2.8 if not d else d * (fwd * 3.6 + 0.6))
        fy = base - 12.2 - fwd * 1.8
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
    _ice(draw, body, GL_BODY)
    if not back:
        kx, ky = cx + d * 5.6, base - 16.6
        glow = GL_CORE if frame % 2 == 0 else lit(GL_CORE, 0.6)
        cel(draw, _gem_facet(kx, ky, 4.7 if not d else 3.5, 4.9, 6, 0.0), GL_SOCKET, sh=None, lw=0.7)
        cel(draw, _gem_facet(kx, ky, 3.2 if not d else 2.3, 3.4, 6, 0.0), glow, sh=None, line_color=GL_DEEP, lw=0.6)
        sparkle(draw, kx - 0.6, ky - 0.8, 1.7, (255, 255, 255))
    else:
        # a crack down his back, wide enough to survive 77px (a 0.6 hairline did not)
        stroke(draw, [(cx - 3.4, base - 22.4), (cx - 0.2, base - 17.0), (cx - 2.4, base - 13.6), (cx - 0.6, base - 10.0)], 1.2,
               GL_DEEP)
    if d > 0 or back:
        cluster(True)
    # the head: a rounded block of ice with snow lying on it
    _ice(draw, _squircle(hx, hy, hrx, hry), GL_ICE)
    k = -d * 1.2
    _snow(draw, [Ell(hx + k + 0.6, hy - hry + 0.8, 7.4 if not d else 6.4, 2.6), Ell(hx + k - 2.6, hy - hry - 0.6, 3.6, 2.6),
                 Ell(hx + k + 2.6, hy - hry - 1.2, 3.8, 2.8), Ell(hx + k - 5.6, hy - hry + 2.0, 1.4, 1.8),
                 Ell(hx + k + 6.4, hy - hry + 2.2, 1.3, 1.6), Ell(hx + k + 0.8, hy - hry + 2.6, 1.4, 1.6)])
    if back:
        _shine(draw, hx - hrx + 1.8, hy - 3.4, 0.8)
        stroke(draw, [(hx + 2.6, hy + 0.2), (hx + 5.2, hy + 3.6), (hx + 3.4, hy + 6.8)], 1.2, GL_DEEP)
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
        # his mouth is ice too: the kit's red "o" was a drop of blood on the rim of his face
        if frame % 2:
            Ell(hx + d * 5.8, hy + 7.0, 1.0, 1.1).draw(draw, fill=GL_MOUTH)
        else:
            stroke(draw, [(hx + d * 5.8 - 1.0, hy + 6.8), (hx + d * 5.8 + 1.0, hy + 6.8)], 0.85, GL_MOUTH)
    for s in ((-1, 1) if not d else (d,)):
        arm(s, True)


# ===================================================================
# MUDSLINGER (24) -- a swamp frog-kid: eyes bulging up in front of a
# lily-pad hat with a lotus on it, a wide grin, mud-spattered dungarees,
# webbed feet and a dripping mud ball
# ===================================================================

MUD_SKIN = (124, 194, 88)
MUD_BACK = (104, 140, 58)       # a frog's back is darker than its front: an olive, so that seen from behind
#                                 he is not a disc of the lawn's own green with a line round it
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
    rig_torso(r, draw, MUD_BELLY if r.front else (MUD_BACK if r.back else MUD_SKIN))
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
    # (from behind the hat is turned round with him: its cut on the far edge, its tilt and its lotus the other way)
    if r.back:
        _lily_pad(draw, hx, hat_y, rx + 2.4, 3.8, 292, 7)
    else:
        _lily_pad(draw, hx - d * 1.6, hat_y, rx + 2.4, 3.8, 112 if not d else 90 + d * 30, -7 if not d else d * 6)
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
            cel(draw, Ell(ex, dy, 4.6 * w, 4.2), MUD_BACK if r.back else (MUD_SKIN if not far else shade(MUD_SKIN, 0.3)),
                sh=(0.5, 0.6))
            if not r.back:
                ms_eye(draw, ex, dy + 0.4, 5.4 * w, 6.2, (64, 44, 30), (float(d), 0.0), "bright", skin=MUD_SKIN,
                       side=side if not d else -side, lash=(40, 70, 30))

    if r.back:
        bulges()
        # the back of his head in the darker green of his back, down to a scalloped edge over the pale jaw
        dorsal = [(Poly([(hx - rx - 2, hy - ry - 2), (hx + rx + 2, hy - ry - 2), (hx + rx + 2, hy + 2.6), (hx + 7.4, hy + 5.6),
                         (hx + 3.8, hy + 4.2), (hx, hy + 6.2), (hx - 3.8, hy + 4.2), (hx - 7.4, hy + 5.6), (hx - rx - 2, hy + 2.6)]),
                   MUD_BACK)]
    elif d:
        # ...which in profile shows down the back of his skull
        dorsal = [(Ell(hx - d * (rx + 3.4), hy - 1.0, 11.0, 13.0), MUD_BACK)]
    else:
        dorsal = []
    cel(draw, head, MUD_SKIN, sh=(0.9, 0.8), regions=dorsal)
    if not r.back:
        cel(draw, Ell(hx + d * 2.6, hy + 6.0, rx * 0.7 if not d else rx * 0.56, 3.6), MUD_BELLY, sh=None, line=False)
        bulges()
    if r.back:
        _lotus(draw, hx - 5.0, hat_y - 1.2)
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
        held = side == ball_side
        if held:
            # the mud ball sits in his hand, the fingers closed under it and the mud running between
            # them (it floated over the hand, and from behind the raised hand held nothing)
            h = hand_at(r, side, 0.3)
            bx, by = h[0] + (side * 1.0 if not d else d * 1.8), h[1] - 2.7
            blob(draw, [Ell(bx, by, 3.6, 3.4), Ell(bx - 1.8, by + 2.8, 1.0, 1.8), Ell(bx + 1.6, by + 2.6, 0.8, 1.5),
                        Ell(bx - 1.8, by + 4.6, 0.8, 0.8)], MUD, sh=(0.9, 0.9), tone=MUD_WET)
            Ell(bx - 1.2, by - 1.3, 1.0, 0.8).draw(draw, fill=lit(MUD, 1.4))
        rig_hand(r, draw, side, MUD_SKIN, reach=0.3 if held else 0.0)


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
    # embers it sheds: two motes that rise off the flame and shrink, lined so they show on sand
    # and snow (three pale twinkles circling were a stray pixel beside it)
    for (u, v, t) in ((-12.4, -15.0, frame), (10.6, -19.0, (frame + 2) % 4)):
        rad = 1.7 - t * 0.3
        cel(draw, Ell(cx + u + (t % 2) * 0.8 - d * 2.4, by + v - t * 2.2, rad, rad), EMB_COLORS[2], sh=None,
            line_color=EMB_COLORS[0], lw=0.7)
    if d:
        # the far hand, behind the body
        flame(draw, cx - d * 10.2, by + 6.4 + sway * 0.6, 4.4, 5.6, (shade(EMB_COLORS[0], 0.5),) + EMB_COLORS[1:], sway=-d * 1.2)
    # a round flame body, its tip curling up into a lick
    flame(draw, cx - d * 1.0, base - 3.0, 26.0, 36.0, EMB_COLORS, sway=sway * 1.6 - d * 4.0)
    cel(draw, Ell(cx, by, 10.4, 9.6), EMB_COLORS[1], sh=None, line=False)
    if back:
        # its back: the flame's own tongues, no face plate
        Poly(flame_pts(cx, by + 8.6, 13.0, 16.0, sway * 0.8)).draw(draw, fill=mix(EMB_COLORS[0], EMB_COLORS[1], 0.45))
        Poly(flame_pts(cx + 0.6, by + 8.6, 6.4, 8.6, sway * 0.4, -1.0)).draw(draw, fill=EMB_COLORS[1])
    else:
        cel(draw, Ell(cx + d * 2.4, by + 1.0, 6.8 if not d else 6.4, 6.0), EMB_COLORS[2], sh=None, line=False)
    # little flame hands
    for side in ((-1, 1) if not d else (d,)):
        hx = cx + side * 11.6 if not d else cx + d * 11.2
        hy2 = (by + 3.0 if not d else by + 5.4) + (sway if side > 0 else -sway) * 0.6
        flame(draw, hx, hy2 + 2.4, 4.4, 5.6, EMB_COLORS, sway=(side if not d else d) * 1.2)
    # tiny feet, pattering under the hop
    step = [0.0, 1.0, 0.0, -1.0][frame]
    for side in (-1, 1):
        cel(draw, Ell(cx + side * 3.4 + d * side * step * 1.2, base - 2.6 + side * step * 0.7, 2.2, 1.4), EMB_COLORS[0],
            sh=None)
    if back:
        return
    # big bright eyes and a little smile; in profile the far eye is the leading one, and narrower
    if d:
        eyes = ((cx + d * 6.6, 0.7, d), (cx + d * 1.0, 1.0, -d))
    else:
        eyes = ((cx - 4.0, 1.0, -1), (cx + 4.0, 1.0, 1))
    for ex, w, side in eyes:
        ms_eye(draw, ex, by - 0.6, 4.0 * w, 5.2, (150, 60, 30), (float(d), 0.0), "bright", skin=EMB_COLORS[2],
               side=side, lash=(120, 40, 30))
    ms_mouth(draw, cx + d * 4.4, by + 4.2, "open" if frame % 2 else "smile", EMB_COLORS[2], 0.9)
    for side in ((-1, 1) if not d else (-d,)):
        Ell(cx + side * 7.4 + d * 4.6, by + 2.6, 1.6, 0.8).draw(draw, fill=(255, 140, 110))


# ===================================================================
# AVALANCHE (26) -- a big fluffy yeti: a sky-blue face, a toothy grin,
# little horns, and blue hands and feet out of a snowdrift of fur
# ===================================================================

YETI_FUR = (244, 247, 253)
YETI_SH = (170, 192, 232)       # the fur's shadow side: deep enough to be darker than snow, so the
#                                 white has a form on white ground (at 198, 212, 238 the grade erased it)
YETI_SOFT = (150, 164, 204)     # the line where fur lies over fur or over the face: not the silhouette's slate
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
             col, sh=(1.6, 1.2), tone=YETI_SH if near else shade(YETI_SH, 0.6))
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
         YETI_FUR, sh=(2.6, 2.4), tone=YETI_SH)
    if not back:
        # the shadow of the head on the chest, fringed like the fur
        bx0 = cx + d * 2.4
        blob(draw, [Ell(bx0, by - 7.6, bw * 0.62 if not d else 5.6, 3.6)] +
             [Ell(bx0 + u, by - 4.4 + (0.8 if i % 2 else 0.0), 1.8, 1.8) for i, u in enumerate((-5.6, -2.8, 0.0, 2.8, 5.6) if not d else (-3.6, -1.0, 1.6, 4.2))],
             mix(YETI_FUR, YETI_SH, 0.6), sh=None, line=False)
    else:
        # his back: the head's shadow fringed across the shoulders as on the chest, two shaggy
        # locks lower down, and a tuft of a tail (a plain inked circle there read as a hole)
        blob(draw, [Ell(cx, by - 7.6, bw * 0.66, 3.6)] +
             [Ell(cx + u, by - 4.2 + (0.8 if i % 2 else 0.0), 1.9, 1.9) for i, u in enumerate((-6.4, -3.2, 0.0, 3.2, 6.4))],
             mix(YETI_FUR, YETI_SH, 0.6), sh=None, line=False)
        for (u, v) in ((-6.2, 0.6), (5.4, 3.4)):
            blob(draw, [Ell(cx + u - 1.7, by + v, 2.1, 1.6), Ell(cx + u + 1.7, by + v + 0.7, 2.1, 1.6)],
                 mix(YETI_FUR, YETI_SH, 0.6), sh=None, line=False)
        blob(draw, _fluff(cx, base - 8.6, 2.6, 2.2, 1.3, 0.0, 360.0, 60.0), YETI_FUR, sh=(1.0, 1.0), tone=YETI_SH,
             line_color=YETI_SOFT, lw=0.8)
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
         YETI_FUR, sh=(2.0, 1.8), tone=YETI_SH)
    if back:
        # the back of his head: two shaggy locks, so it is fur and not a ball
        for (u, v) in ((-4.2, -1.6), (3.6, 2.8)):
            blob(draw, [Ell(hx + u - 1.7, hy + v, 2.2, 1.7), Ell(hx + u + 1.7, hy + v + 0.7, 2.2, 1.7)],
                 mix(YETI_FUR, YETI_SH, 0.6), sh=None, line=False)
    if not back:
        # a sky-blue face
        fx0 = hx + d * 3.2
        fw = 7.8 if not d else 6.0
        cel(draw, Poly(arc_pts(fx0, hy + 1.4, fw, 6.8, 0, 360, 32)[:-1]), YETI_FACE, sh=(0.8, 0.8))
        # bangs of fur over the brow, lined softly: in the silhouette's slate they were a headband
        blob(draw, [Ell(fx0 - fw * 0.55, hy - 4.6, 3.0, 2.4), Ell(fx0, hy - 5.2, 3.2, 2.6), Ell(fx0 + fw * 0.55, hy - 4.6, 3.0, 2.4)],
             YETI_FUR, sh=(0.0, 0.9), tone=YETI_SH, line_color=YETI_SOFT, lw=0.8)
        ey = hy + 0.8
        if d:
            ms_eye(draw, fx0 + d * 3.0, ey, 2.8, 4.4, (40, 66, 120), (float(d), 0.0), "bright", skin=YETI_FACE, side=d, lash=(30, 44, 90))
            ms_eye(draw, fx0 - d * 1.2, ey, 3.6, 4.8, (40, 66, 120), (float(d), 0.0), "bright", skin=YETI_FACE, side=-d, lash=(30, 44, 90))
        else:
            for s in (-1, 1):
                ms_eye(draw, fx0 + s * 3.4, ey, 3.8, 5.0, (40, 66, 120), (0.0, 0.0), "bright", skin=YETI_FACE, side=s, lash=(30, 44, 90))
        # a big grin with two fangs, kept on the face (in profile it hung off the chin onto the fur)
        mx, my = fx0 + d * 0.9, hy + (4.4 if not d else 3.8)
        mw = 3.8 if not d else 2.6
        md = 3.2 if not d else 2.6
        Poly([(mx - mw, my), (mx + mw, my)] + arc_pts(mx, my, mw, md, 0, 180, 12)[1:-1]).draw(draw, fill=YETI_MOUTH)
        Ell(mx + d * 0.3, my + md * 0.66, mw * 0.55, 0.8).draw(draw, fill=(238, 110, 140))
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
