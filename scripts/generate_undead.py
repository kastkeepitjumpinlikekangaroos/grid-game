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
    rig_robe, rig_torso, shade, sparkle, star, stroke, xform, arm_pts, face_anchor, bez,
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

from sprite_base import CLEAR, clipped, crystal, hood_shape  # kit pieces the import above leaves out

NEC_ROBE = (88, 54, 124)
NEC_SOUL = (132, 255, 150)
NEC_SKIN = (232, 226, 236)
SOUL_FIRE = ((70, 200, 110), (132, 255, 150), (226, 255, 226))
NEC_TRIM = (86, 196, 120)
NEC_STAFF = (214, 204, 178)


# -- shared by the three hooded ones (Necromancer, Lich, Reaper) --------------

def _hood_pts(r, peak=0.0):
    """sprite_base.hood_shape, with the drape the right way round facing left.
    (The kit lists the drape's points front to back after an arc that, facing
    left, ends at the back: the outline crosses itself and the cowl under the
    chin comes out a bow tie.)"""
    pts = list(hood_shape(r, peak, drape=False).pts)
    hx, y, d = r.hx, r.sh_y, r.d
    if d > 0:
        pts += [(hx + 5.4, y + 0.6), (hx - 2.0, y + 2.4), (hx - 7.6, y + 1.0)]
    elif d < 0:
        pts += [(hx + 7.6, y + 1.0), (hx + 2.0, y + 2.4), (hx - 5.4, y + 0.6)]
    else:
        sw = r.sh_w + 1.9
        pts += [(hx + sw, y + 1.4), (hx + 3.0, y + 2.6), (hx - 3.0, y + 2.6), (hx - sw, y + 1.4)]
    return Poly(pts)


def _hood(r, draw, cloth, peak=0.0, opening=1.0, lining=None):
    """rig_hood over _hood_pts; returns the hood's shape."""
    if lining is None:
        lining = shade(cloth, 1.4)
    shape = _hood_pts(r, peak)
    layer = draw.sub()
    cel(layer, shape, cloth, sh=(1.5, 1.2), line=False)
    if not r.back:
        hole = hood_opening(r, opening)
        hole.scaled(1.12, r.hx + r.d * 2.8, r.head_cy + 2.0).draw(layer, fill=lining)
        hole.draw(layer, fill=CLEAR)
        shape.draw(layer, fill=None, outline=ink(cloth), width=1.0)
        hole.draw(layer, fill=None, outline=ink(lining), width=0.8)
    else:
        shape.draw(layer, fill=None, outline=ink(cloth), width=1.0)
    draw.merge(layer)
    return shape


def _cowl_band(r, draw, color, w=1.9):
    """A trim band along the bottom of a hood's drape: what parts hood from
    robe when the two are one cloth, from behind most of all."""
    hx, y, d = r.hx, r.sh_y, r.d
    if d:
        edge = [(hx + d * 5.4, y + 0.6), (hx - d * 2.0, y + 2.4), (hx - d * 7.6, y + 1.0)]
    else:
        sw = r.sh_w + 1.9
        edge = [(hx - sw, y + 1.4), (hx - 3.0, y + 2.6), (hx + 3.0, y + 2.6), (hx + sw, y + 1.4)]
    cel(draw, Poly(edge + [(px, py - w) for (px, py) in edge[::-1]]), color, sh=None, lw=0.8)


def _hem_band(draw, robe, y0, color, cloth):
    """A trim band round the hem of the robe rig_robe returned, in its own
    colour. (rig_robe's own trim is 1.6 units, most of it shadow tone and ink
    line: gold comes out brown and bone mauve.)"""
    xs = [p[0] for p in robe.pts]
    x0, x1, y1 = min(xs) - 1.0, max(xs) + 1.0, max(p[1] for p in robe.pts) + 1.0
    clipped(draw, robe, lambda l: Poly([(x0, y0), (x1, y0), (x1, y1), (x0, y1)]).draw(l, fill=color))
    clipped(draw, robe, lambda l: Poly([((x0 + x1) / 2.0 + 2.0, y0), (x1, y0), (x1, y1), ((x0 + x1) / 2.0 + 3.0, y1)]).draw(
        l, fill=shade(color, 0.8)))
    clipped(draw, robe, lambda l: stroke(l, [(x0, y0), (x1, y0)], 0.7, ink(color)))
    robe.draw(draw, fill=None, outline=ink(cloth), width=1.0)


def _hood_seam(r, draw, cloth, peak=0.0):
    """From behind: the seam down the back of a hood."""
    hx, hy, ry = r.hx, r.head_cy, r.head_ry
    stroke(draw, [(hx, hy - ry - 0.4 - peak * 0.5), (hx + 0.4, hy), (hx, r.sh_y - 0.2)], 0.9, shade(cloth, 1.7))


def _carry(r, top, reach=0.0):
    """In profile a staff is carried on the near shoulder: its butt in front
    of the hip, the shaft up across the body and on behind the hood to `top`,
    so the whole of it shows and none of it crosses the face. Returns the
    butt, where the near hand grips it, and the `out` that takes the arm
    there."""
    d = r.d
    gy = r.hip_y - 1.4 - reach * 3.2
    butt = (r.cx + d * 7.4, r.hip_y + 4.4)
    k = (gy - butt[1]) / (top[1] - butt[1])
    gx = butt[0] + (top[0] - butt[0]) * k
    return butt, (gx, gy), (gx - r.cx) * d - 0.6 - reach * 4.0


def _held_arm(r, draw, sleeve, side, out, reach=0.0):
    """The arm that carries the staff in profile: it does not swing with the
    walk (the staff slid across the body when it did)."""
    ph, r.ph = r.ph, 0.0
    rig_arms(r, draw, sleeve, sleeve, sides=(side,), hands=False, reach=reach, out=out)
    r.ph = ph


def _nec_soul(draw, x, y, lean):
    """A soul at his call: a ball of soul fire with a tail curling under it."""
    blob(draw, [Ell(x, y, 2.6, 2.5), Limb([(x - lean * 0.4, y + 0.6), (x + lean * 1.8, y + 3.4), (x + lean * 0.8, y + 6.0)],
                                           [2.1, 1.3, 0.6])], SOUL_FIRE[1], sh=None, line_color=ink(SOUL_FIRE[0]), lw=0.8)
    Ell(x - 0.5, y - 0.5, 1.3, 1.2).draw(draw, fill=SOUL_FIRE[2])


def draw_necromancer(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame)
    d = r.d
    # The staff is in his left hand, upright at his side: screen right
    # head-on, screen left from behind. In profile it is carried on the near
    # shoulder, so the skull and its fire stand behind the hood and nothing
    # crosses his face.
    st_side = (1 if not r.back else -1) if not d else d
    flick = [0, 1, 0, 1][frame]
    if d:
        top = (r.cx - d * 15.0, r.head_cy - 6.4 + [0.0, 0.6, 0.0, -0.6][frame])
        bot, grip, out = _carry(r, top)
    else:
        out = 3.4
        grip = hand_at(r, st_side, 0.0, 0.0, out)
        bot = (grip[0] - st_side * 0.5, r.base_y - 1.0)
        top = (grip[0] + st_side * 1.5, r.head_cy - 7.4)

    def staff():
        cel(draw, Limb([bot, top], [1.15, 1.0]), NEC_STAFF, sh=(0.5, 0.0))
        ln = math.hypot(top[0] - bot[0], top[1] - bot[1])
        sx = top[0] + (top[0] - bot[0]) / ln * 2.2
        sy = top[1] + (top[1] - bot[1]) / ln * 2.2
        flame(draw, sx, sy - 2.6, 8.4, 9.4 + flick, SOUL_FIRE, sway=[0.0, 1.2, 0.0, -1.2][frame])
        skull(draw, sx, sy, 4.5, 4.3, glow=NEC_SOUL, teeth=False)

    if r.back:
        staff()
    if d:
        rig_arms(r, draw, NEC_ROBE, NEC_SKIN, layer="far")
    rig_legs(r, draw, shade(NEC_ROBE, 1.4), (60, 44, 70))
    robe = rig_robe(r, draw, NEC_ROBE, flare=3.4)
    _hem_band(draw, robe, r.base_y - 5.0, NEC_TRIM, NEC_ROBE)
    rig_belt(r, draw, shade(NEC_ROBE, 1.8), buckle=NEC_SOUL)
    if d:
        _held_arm(r, draw, NEC_ROBE, d, out)
        staff()
    else:
        for side in (-1, 1):
            rig_arms(r, draw, NEC_ROBE, NEC_SKIN, sides=(side,), hands=False, out=out if side == st_side else 0.0)
    rig_head(r, draw, NEC_SKIN, hair=(62, 50, 74), style="swept", eye_color=(60, 200, 110), expression="smirk",
             mood="sharp", blush=False)
    if not r.back:
        e1, e2, ey, _, _ = face_anchor(r)
        for ex in ((e1, e2) if not d else (e1,)):
            stroke(draw, [(ex - 1.8, ey + 3.4), (ex + 1.8, ey + 3.4)], 0.5, shade(NEC_SKIN, 1.4))
    _hood(r, draw, NEC_ROBE, peak=2.4)
    _cowl_band(r, draw, NEC_TRIM)
    if r.back:
        _hood_seam(r, draw, NEC_ROBE, 2.4)
    elif not d:
        # a skull for a clasp, where the cowl closes
        cel(draw, Ell(r.cx, r.sh_y + 1.5, 2.7, 2.5), BONE, sh=(0.5, 0.5), lw=0.8)
        for s in (-1, 1):
            Ell(r.cx + s * 1.05, r.sh_y + 1.7, 0.75, 0.9).draw(draw, fill=VOID)
        staff()
    if d:
        cel(draw, Ell(grip[0], grip[1], 2.2, 2.2), NEC_SKIN, sh=(0.6, 0.6))
    else:
        for side in (-1, 1):
            rig_hand(r, draw, side, NEC_SKIN, out=out if side == st_side else 0.0)
    # one soul keeps him company, on the side away from the staff: ahead of
    # him in profile
    bobw = [0.0, -1.2, 0.4, 1.2][frame]
    if d:
        _nec_soul(draw, r.cx + d * 15.0, r.sh_y + 2.6 + bobw, -d)
    else:
        _nec_soul(draw, r.cx - st_side * 16.0, r.sh_y + 2.0 + bobw, -st_side)


# ===================================================================
# SKELETON KING (28) -- crown, ermine-trimmed cape, bone axe
# ===================================================================

SK_CAPE = (178, 34, 48)
SK_ERMINE = (250, 248, 244)
SK_GLOW = (255, 64, 60)
SK_FUR_SH = (206, 208, 226)    # the ermine's shadow side
SK_FAR = (200, 198, 204)       # the far arm and leg: a cool grey, lined slate like the near ones
SK_STEEL = (140, 152, 188)     # the axe's blade: the one cold thing on him, a step darker than his bones
SK_EDGE = (228, 234, 248)      # its ground edge

from sprite_base import CLEAR, clipped  # noqa: E402  (the file's import list belongs to both halves of it)


def _sk_bone(draw, a, b, far=False, width=1.15):
    """One long bone with a knob at each end, as one shape with one line round it. (The far
    limbs were shade(BONE), which is just saturated enough for the kit to line it in brown:
    a wooden leg. SK_FAR is a grey, lined slate like the near bones.)"""
    k = width * 1.4
    blob(draw, [Limb([a, b], [width, width]), Ell(a[0], a[1], k, k), Ell(b[0], b[1], k, k)],
         SK_FAR if far else BONE, sh=None, lw=0.7)


def _sk_skull(draw, x, y, d, back):
    """The file's chibi skull, as his own: in profile the far socket is cut off by the cheek
    instead of standing out past it (goggles), and from behind there is a crack down it."""
    rx, ry = 10.8, 10.4
    cran = Ell(x, y - ry * 0.1, rx, ry * 0.92)
    jaw = RRect(x - rx * 0.62 + d * 1.4, y + ry * 0.35, x + rx * 0.62 + d * 1.4, y + ry * 1.02, rx * 0.3)
    blob(draw, [cran, jaw], BONE, sh=(1.2, 1.0))
    dark = shade(BONE, 1.8)
    if back:
        stroke(draw, [(x + 3.0, y - 4.6), (x + 1.2, y - 1.6), (x + 3.4, y + 0.8), (x + 2.0, y + 4.0)], 0.8, dark)
        stroke(draw, [(x + 1.2, y - 1.6), (x - 1.4, y - 0.4)], 0.7, dark)
        return
    if d:
        sockets = ((x + d * 3.0, 3.0, x + d * 3.4), (x + d * 8.7, 1.9, x + d * 8.1))
    else:
        sockets = tuple((x + s * rx * 0.4, rx * 0.3, x + s * rx * 0.4) for s in (-1, 1))
    sy = y + ry * 0.1

    def holes(l):
        for (ex, w, _) in sockets:
            Ell(ex, sy, w, ry * 0.34).draw(l, fill=VOID)

    clipped(draw, Ell(x, y - ry * 0.1, rx - 1.15, ry * 0.92 - 1.15), holes)
    for (ex, w, gx) in sockets:
        k = min(1.0, w / 3.0 + 0.1)
        Ell(gx, sy + 0.4, 1.6 * k, 1.45).draw(draw, fill=SK_GLOW)
        Ell(gx - 0.4 * k, sy - 0.2, 0.5, 0.5).draw(draw, fill=(255, 255, 255))
    nx = x + d * rx * 0.6
    Poly([(nx - 0.9, y + ry * 0.56), (nx + 0.9, y + ry * 0.56), (nx, y + ry * 0.4)]).draw(draw, fill=VOID)
    jx = x + d * 3.6
    n = 4 if not d else 3
    for i in range(n):
        tx = jx - (n - 1) * 1.1 + i * 2.2
        stroke(draw, [(tx, y + ry * 0.72), (tx, y + ry * 0.98)], 0.5, dark)
    stroke(draw, [(jx - n * 1.1, y + ry * 0.74), (jx + n * 1.1, y + ry * 0.74)], 0.5, dark)


def _sk_crown(draw, x, y, d, back):
    """The crown, on a skull centred on (x, y): wider than the skull it sits on, five fat
    points with a ball on each, a ruby in the band. (It was 16 units across on a 21.6-unit
    skull, and its points were so thin they came out all shadow tone and line: brown.)"""
    y0 = y - 6.4
    tips = ((-11.4, -14.0), (-5.7, -15.6), (0.0, -17.8), (5.7, -15.6), (11.4, -14.0))
    pts = [(x - 10.6, y - 9.8)]
    for i, (u, v) in enumerate(tips):
        pts.append((x + u, y + v))
        if i < 4:
            pts.append((x + (u + tips[i + 1][0]) / 2.0, y - 10.6))
    pts.append((x + 10.6, y - 9.8))
    shape = Poly(pts + bez((x + 9.7, y0), (x, y0 + 2.6), (x - 9.7, y0), 8))
    cel(draw, shape, GOLD, sh=(1.1, 0.5), hi=(0.6, 0.5))
    clipped(draw, shape, lambda l: stroke(l, bez((x - 10.6, y - 10.3), (x, y - 7.7), (x + 10.6, y - 10.3), 8), 0.6,
                                          shade(GOLD, 1.8)))
    for (u, v) in tips:
        cel(draw, Ell(x + u, y + v - 0.2, 1.5, 1.5), GOLD, sh=None, lw=0.6)
    if not back:
        gem(draw, x + d * 5.0, y - 7.8 + (0.0 if d else 0.5), 2.0, SK_GLOW)


def _sk_axe(draw, grip, ang, flip, up, down):
    """His bone axe: a thigh bone for a haft, knuckles at its foot, and a great crescent of a
    blade bound on with gold, as tall as his skull. `up` and `down` are how far the haft runs
    past the grip each way, `flip` (+1/-1) the side the blade is on.

    (It was a small rounded pale blob on a stick, the same in every facing: a spatula.)"""
    def at(pts):
        return xform(pts, grip[0], grip[1], ang, 1.0, flip)

    foot = at([(-down, -1.0), (-down, 1.0), (up + 0.2, 0.0)])
    blob(draw, [Limb(at([(-down, 0.0), (up, 0.0)]), [1.3, 1.15]), Ell(foot[0][0], foot[0][1], 1.45, 1.45),
                Ell(foot[1][0], foot[1][1], 1.45, 1.45), Ell(foot[2][0], foot[2][1], 1.45, 1.45)], BONE, sh=(0.5, 0.3), lw=0.7)
    a, b = up - 9.4, up - 3.2
    m = (a + b) / 2.0
    edge = bez((a - 3.5, 7.6), (m, 11.2), (b + 3.5, 7.6), 10)
    body = [(a, 0.4)] + bez((a, 0.4), (a - 0.2, 4.4), (a - 3.5, 7.6), 5)[1:] + edge[1:] + \
        bez((b + 3.5, 7.6), (b + 0.2, 4.4), (b, 0.4), 5)[1:]
    bevel = edge + bez((b + 2.5, 6.8), (m, 9.4), (a - 2.5, 6.8), 10)
    low = [(a - 6.0, 0.0), (m, 0.0), (m, 14.0), (a - 6.0, 14.0)]
    cel(draw, Poly(at([(a + 0.6, -0.6), (m - 1.0, -3.2), (m + 1.0, -3.2), (b - 0.6, -0.6)])), SK_STEEL, sh=None, lw=0.8)
    cel(draw, Poly(at(body)), SK_STEEL, sh=None,
        regions=[(Poly(at(low)), shade(SK_STEEL, 0.9)), (Poly(at(bevel)), SK_EDGE)])
    cel(draw, Poly(at([(a - 0.9, -1.8), (b + 0.9, -1.8), (b + 0.9, 1.8), (a - 0.9, 1.8)])), GOLD, sh=None, lw=0.8)


def _sk_spots(draw, pts, k=1.25):
    """The black tails in ermine."""
    for (x, y) in pts:
        Poly([(x, y - 0.9 * k), (x + 0.8 * k, y + 1.0 * k), (x - 0.8 * k, y + 1.0 * k)]).draw(draw, fill=VOID)


def _sk_cape(r, draw, layer):
    """The king's cape: the kit's, cut fuller, with a band of ermine round the hem of its
    outside, so from behind he is a king and not a light bulb on a red trapezoid."""
    if (layer == "under" and r.back) or (layer == "over" and not r.back):
        return
    cx, d = r.cx, r.d
    sway = r.ph * 1.2
    bottom = r.base_y - 1.8
    lining = shade(SK_CAPE, 1.3)
    w = r.sh_w * 1.15
    if d:
        back = 8.6 + abs(sway) * 1.4
        cel(draw, Poly([(cx - d * 1.0, r.sh_y - 2.6), (cx - d * 4.6, r.sh_y - 1.0), (cx - d * back, bottom),
                        (cx - d * 1.2, bottom - 1.0), (cx + d * 1.0, r.hip_y - 2)]), lining, sh=(0.0, 0.0))
        outer = [(cx - d * 1.2, r.sh_y - 2.4), (cx - d * 4.6, r.sh_y - 0.8), (cx - d * back, bottom),
                 (cx - d * (back - 5.4), bottom - 0.7)]
        cel(draw, Poly(outer), SK_CAPE, sh=(0.8, 0.0))
        # the ermine at its hem, a shape of its own so it is lined in the fur's slate, not the cape's red
        k = 3.3 / (bottom - (r.sh_y - 0.8))
        cel(draw, Poly([(outer[2][0] + (outer[1][0] - outer[2][0]) * k - d * 0.3, bottom - 3.3), outer[2], outer[3],
                        (outer[3][0] + (outer[0][0] - outer[3][0]) * k, bottom - 3.6)]), SK_ERMINE, sh=None, lw=0.8)
        _sk_spots(draw, [(cx - d * (back - 2.6), bottom - 1.9)])
        return
    if not r.back:
        cel(draw, Poly([(cx - w + 0.4, r.sh_y - 2.8), (cx + w - 0.4, r.sh_y - 2.8), (cx + w + 3.0 + sway, bottom),
                        (cx - w - 3.0 + sway, bottom)]), lining, sh=(1.2, 0.0))
        return
    x0, x1 = cx - w - 2.4 + sway, cx + w + 2.4 + sway
    top = r.sh_y - 2.8
    cel(draw, Poly([(cx - w + 0.6, top), (cx + w - 0.6, top), (x1, bottom), (x0, bottom)]), SK_CAPE, sh=(1.8, 0.0),
        regions=[(Poly([(cx - 0.9, r.sh_y), (cx + 0.9, r.sh_y), (cx + 1.6 + sway, bottom), (cx - 1.6 + sway, bottom)]),
                  shade(SK_CAPE, 0.8))])
    # the ermine round its hem: a shape of its own, so it is lined in the fur's slate and not the
    # cape's red (which made a heavy dark corner of it)
    band = bottom - 3.4
    k = (band - top) / (bottom - top)
    b0, b1 = cx - w + 0.6 + (x0 - (cx - w + 0.6)) * k, cx + w - 0.6 + (x1 - (cx + w - 0.6)) * k
    cel(draw, Poly([(b0 - 0.5, band), (b1 + 0.5, band), (x1 + 0.6, bottom - 0.6), (x1 - 0.2, bottom + 0.3),
                    (x0 + 0.2, bottom + 0.3), (x0 - 0.6, bottom - 0.6)]), SK_ERMINE, sh=(1.6, 0.0), tone=SK_FUR_SH, lw=0.8)
    _sk_spots(draw, [(x0 + (x1 - x0) * t, band + 1.9) for t in (0.14, 0.38, 0.62, 0.86)])


def draw_skeletonking(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=0.92)
    d = r.d
    cx = r.cx
    ax_side = _hold_side(r)
    hx, hy = r.hx, r.head_cy + 0.6
    # The axe. Head-on and from behind it stands at his side in his left hand, as tall as he
    # is, its blade turned out; in profile it is carried on the shoulder, the blade up behind
    # his skull, so nothing crosses his face or his ribs but the arm that holds it.
    ph = r.ph
    r.ph = ph * 0.25
    if d:
        grip = (cx + d * 6.4, r.hip_y - 2.2)
        top = (cx - d * 17.0, hy - 5.4 + [0.0, 0.5, 0.0, -0.5][frame])
        axe = (grip, math.degrees(math.atan2(top[1] - grip[1], top[0] - grip[0])), -d,
               math.hypot(top[0] - grip[0], top[1] - grip[1]), 2.8)
    else:
        grip = arm_pts(r, ax_side, 0.0, 0.0, 2.6)[3]
        axe = (grip, -90 + ax_side * 9, ax_side, grip[1] - (hy - 11.4), r.base_y - 1.8 - grip[1])
    r.ph = ph

    _sk_cape(r, draw, "under")
    # bone legs and arms
    for side in ((-1, 1) if not d else (-d, d)):
        hx0, hy0 = r.hip(side)
        fx, fy = r.foot(side)
        far = not r.near(side)
        _sk_bone(draw, (hx0, hy0 + 1.0), (fx, fy - 1.4), far)
        cel(draw, Ell(fx + d * 0.8, fy, 2.4, 1.4), SK_FAR if far else BONE, sh=None, lw=0.8)
    if d:
        s, e, w, h = arm_pts(r, -d)
        _sk_bone(draw, s, h, True)
    # ribcage and pelvis
    cel(draw, RRect(cx - 4.4, r.hip_y - 3.0, cx + 4.4, r.hip_y + 0.6, 1.6), BONE, sh=(0.6, 0.6))
    top_y, bot_y = r.sh_y - 2.4, r.waist_y + 1.0
    if d:
        cel(draw, RRect(cx - 3.9 + d * 0.3, top_y, cx + 3.9 + d * 0.3, bot_y, 2.2), BONE, sh=(0.8, 0.6))
        RRect(cx - 2.8 + d * 0.4, top_y + 1.2, cx + 2.8 + d * 0.4, bot_y - 0.9, 1.6).draw(draw, fill=VOID)
        for i in range(3):
            yy = top_y + 2.3 + i * 2.5
            stroke(draw, [(cx - d * 2.6, yy), (cx + d * 0.6, yy + 0.4), (cx + d * 3.4, yy + 1.5)], 0.9, BONE)
    else:
        cel(draw, RRect(cx - 5.4, top_y, cx + 5.4, bot_y, 2.6), BONE, sh=(0.8, 0.6))
        if not r.back:
            ribcage(draw, cx, r.sh_y - 1.4, 4.0, 6.4)
    # a gold belt with a ruby
    if not r.back:
        cel(draw, RRect(cx - 5.4, r.waist_y + 0.5, cx + 5.4, r.waist_y + 3.1, 0.8) if not d else
            RRect(cx - 4.0, r.waist_y + 0.5, cx + 4.4, r.waist_y + 3.1, 0.8), GOLD, sh=None, lw=0.8)
        gem(draw, cx + d * 3.6, r.waist_y + 1.8, 1.5, SK_GLOW)
    if d:
        # the arm that carries the axe, bent at the elbow; then the haft, up over the shoulder
        s = r.shoulder(d)
        el = (cx + d * 1.6, r.waist_y + 0.4)
        _sk_bone(draw, s, el)
        _sk_bone(draw, el, grip)
        _sk_axe(draw, *axe)
    else:
        for side in (-1, 1):
            if side == ax_side:
                _sk_bone(draw, r.shoulder(side), grip)
            else:
                s, e, w, h = arm_pts(r, side)
                _sk_bone(draw, s, h)
    _sk_cape(r, draw, "over")
    # the skull and the crown, and the ermine mantle on his shoulders: under the jaw from the
    # front, over the cape and the nape from behind
    def mantle():
        if d:
            blob(draw, [Ell(cx - d * 0.4, r.sh_y - 1.5, 6.4, 3.0)], SK_ERMINE, sh=(0.6, 0.8), tone=SK_FUR_SH)
            _sk_spots(draw, [(cx + d * 2.2, r.sh_y - 1.2), (cx - d * 2.6, r.sh_y - 1.0)])
        else:
            lift = 0.6 if r.back else 0.0
            blob(draw, [Ell(cx - 5.2, r.sh_y - 1.6 - lift, 4.6, 2.9), Ell(cx, r.sh_y - 1.0 - lift, 4.9, 2.8 + lift),
                        Ell(cx + 5.2, r.sh_y - 1.6 - lift, 4.6, 2.9)], SK_ERMINE, sh=(0.6, 0.8), tone=SK_FUR_SH)
            _sk_spots(draw, [(cx - 5.4, r.sh_y - 1.6 - lift), (cx, r.sh_y - 0.8 - lift), (cx + 5.4, r.sh_y - 1.6 - lift)])

    if not r.back:
        mantle()
    _sk_skull(draw, hx, hy, d, r.back)
    _sk_crown(draw, hx, hy, d, r.back)
    if r.back:
        mantle()
    if not d:
        _sk_axe(draw, *axe)
    cel(draw, Ell(grip[0], grip[1], 2.0, 2.0), BONE, sh=None, lw=0.8)


# ===================================================================
# BANSHEE (29) -- a wailing ghost woman with streaming hair
# ===================================================================

BAN_BODY = (140, 144, 204)
BAN_HAIR = (244, 244, 255)
BAN_HAIR_SH = (190, 198, 238)
BAN_GLOW = (170, 220, 255)
BAN_FACE = (190, 198, 238)
BAN_EYE = (50, 52, 110)
BAN_TEAR = (104, 176, 255)
BAN_HAND = (202, 210, 244)


def _ban_curtain(hx, hy, rx, ph, back):
    """Her long hair head-on, as one mass: it falls from the head and floats
    out a little toward the tips, three locks a side, every one of them
    hanging down. (Six separate locks spreading from the head were a spider's
    legs.) From behind it parts below the waist, over the gown."""
    def half(s):
        w = [math.sin(ph + s * 0.8 + i * 1.4) for i in range(3)]
        t1 = (hx + s * (rx + 6.0) + w[0] * 0.9, hy + 25.6)
        n1 = (hx + s * (rx + 1.4), hy + 21.4)
        t2 = (hx + s * (rx - 1.0) + w[1] * 1.0, hy + 28.2)
        n2 = (hx + s * (rx - 5.6), hy + 23.4)
        t3 = (hx + s * (rx - (6.6 if back else 7.8)) + w[2] * 0.8, hy + 26.8)
        mid = (hx + s * (0.3 if back else 1.0), hy + (13.6 if back else 21.0))
        p = bez((hx + s * (rx + 0.6), hy - 3.0), (hx + s * (rx + 3.4), hy + 4.0), (hx + s * (rx + 2.0), hy + 10.5), 6)
        p += bez(p[-1], (hx + s * (rx + 2.2), hy + 16.0), (hx + s * (rx + 6.4), hy + 19.6), 5)[1:]
        p += bez(p[-1], (hx + s * (rx + 8.0), hy + 23.0), t1, 4)[1:]
        p += bez(t1, (hx + s * (rx + 3.0), hy + 25.6), n1, 4)[1:]
        p += bez(n1, (hx + s * (rx + 1.6), hy + 27.4), t2, 4)[1:]
        p += bez(t2, (hx + s * (rx - 4.0), hy + 27.6), n2, 4)[1:]
        p += bez(n2, (hx + s * (rx - 5.6), hy + 26.6), t3, 3)[1:]
        p += bez(t3, (hx + s * (rx - (7.6 if back else 9.6)), hy + (21.0 if back else 25.6)), mid, 4)[1:]
        return p, (n1, n2)

    right, rn = half(1)
    left, ln = half(-1)
    return Poly(right + left[::-1]), rn + ln


def _ban_stream(hx, hy, rx, ry, ph, d):
    """The same hair in profile: one mass streaming out behind her."""
    w = [math.sin(ph + i * 1.4) for i in range(3)]

    def b(u, y):
        return (hx - d * u, y)

    t1 = b(rx + 9.4 + w[0] * 0.8, hy + 18.4 + w[0] * 0.6)
    n1 = b(rx + 3.6, hy + 16.6)
    t2 = b(rx + 4.6 + w[1] * 0.8, hy + 23.6 + w[1] * 0.5)
    n2 = b(rx - 1.6, hy + 19.6)
    t3 = b(rx - 4.4 + w[2] * 0.8, hy + 25.4)
    p = bez(b(-1.0, hy - ry - 0.6), b(rx + 3.4, hy - ry * 0.7), b(rx + 3.0, hy + 2.0), 6)
    p += bez(p[-1], b(rx + 3.0, hy + 9.0), b(rx + 6.6, hy + 12.4), 4)[1:]
    p += bez(p[-1], b(rx + 9.6, hy + 14.6), t1, 4)[1:]
    p += bez(t1, b(rx + 5.6, hy + 19.0), n1, 4)[1:]
    p += bez(n1, b(rx + 5.2, hy + 21.6), t2, 4)[1:]
    p += bez(t2, b(rx + 0.6, hy + 23.4), n2, 4)[1:]
    p += bez(n2, b(rx - 1.4, hy + 24.0), t3, 3)[1:]
    p += [b(3.6, hy + 17.0), b(1.0, hy + 6.0)]
    return Poly(p), (n1, n2)


def _ban_hand(draw, x, y, lean, far=False):
    """A limp hand hanging from the wrist, the way a ghost carries them."""
    col = shade(BAN_HAND, 0.8) if far else BAN_HAND
    cel(draw, Limb([(x, y), (x + lean * 0.5, y + 1.8), (x + lean * 0.9, y + 3.4)], [1.5, 1.5, 0.9]), col, sh=(0.5, 0.4), lw=0.8)


def _ban_face(r, draw, frame):
    """Hollow eyes drooping at the outer corners, tears, and the wail: three
    dark shapes on a pale face read as a ghost at any size."""
    d = r.d
    e1, e2, ey, mx, my = face_anchor(r)
    k = r.head_rx / 12.4
    ey -= 0.4
    eyes = ((e1, 1.0, -1), (e2, 1.0, 1)) if not d else ((e2, 0.62, d), (e1, 1.0, -d))
    drip = [0.0, 0.8, 1.6, 0.8][frame]
    for ex, w, side in eyes:
        erx, ery = 2.2 * w, 2.9
        if not d or side == -d:
            # the tear first, so the eye sits on its top
            cel(draw, Limb([(ex, ey + ery - 0.8), (ex + side * 0.3, ey + ery + 1.6 + drip)], [0.8, 1.05]), BAN_TEAR,
                sh=None, lw=0.6)
            Ell(ex - 0.2, ey + ery + 1.2 + drip, 0.4, 0.5).draw(draw, fill=(232, 246, 255))
        Ell(ex, ey, erx, ery).draw(draw, fill=BAN_EYE)
        top = ey - ery - 0.5
        x_in, x_out = ex - side * (erx + 0.4), ex + side * (erx + 0.5)
        Poly([(x_in, top), (x_out, top), (x_out, ey - ery * 0.1)]).draw(draw, fill=BAN_FACE)
        stroke(draw, [(x_in + side * 0.3, top + 0.5), (x_out - side * 0.2, ey - ery * 0.1)], 0.9, BAN_EYE)
        Ell(ex - 0.5 * w, ey - 0.3, 0.9 * max(w, 0.8), 0.9).draw(draw, fill=(255, 255, 255))
        Ell(ex + 0.8 * w, ey + 1.3, 0.45, 0.45).draw(draw, fill=(214, 226, 255))
        bx_in, bx_out = ex - side * 1.7 * w, ex + side * 1.9 * w
        stroke(draw, [(bx_in, ey - 4.7 * k), (ex, ey - 4.3 * k), (bx_out, ey - 3.2 * k)], 0.8, (124, 132, 192))
    Ell(mx, my + 0.1, 2.0 if not d else 1.5, 2.5).draw(draw, fill=(60, 40, 96))
    Ell(mx, my + 1.2, 1.2 if not d else 0.9, 1.0).draw(draw, fill=(150, 104, 176))


def draw_banshee(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, bob=[0, -1, -2, -1][frame], step=0, head=0.9)
    d = r.d
    wave = [0.0, 1.0, 0.0, -1.0][frame]
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    ph = frame * math.pi / 2.0
    cx = r.cx
    top = r.sh_y - 2.4
    sway = wave * 1.4
    tail_y = r.base_y - 1.2

    def strands(notches, sides):
        # a line down to each notch, so the mass reads as locks of hair
        for i, n in enumerate(notches):
            s = sides[i]
            a = (hx + s * (rx - 1.0 - (i % 2) * 4.0), hy + 4.0 + (i % 2) * 5.0)
            stroke(draw, bez(a, ((a[0] + n[0]) / 2.0 + s * 2.2, (a[1] + n[1]) / 2.0), n, 6), 0.9, BAN_HAIR_SH)

    if d:
        mass, notches = _ban_stream(hx, hy, rx, ry, ph, d)
        blob(draw, [mass], BAN_HAIR, sh=(1.0, 1.3), tone=BAN_HAIR_SH)
        for i, n in enumerate(notches):
            a = (hx - d * (rx - 1.0 - i * 4.0), hy + 3.0 + i * 5.0)
            stroke(draw, bez(a, ((a[0] + n[0]) / 2.0 - d * 1.6, (a[1] + n[1]) / 2.0 - 1.0), n, 6), 0.9, BAN_HAIR_SH)
    elif not r.back:
        mass, notches = _ban_curtain(hx, hy, rx, ph, False)
        blob(draw, [mass], BAN_HAIR, sh=(1.2, 1.3), tone=BAN_HAIR_SH)
        strands(notches, (1, 1, -1, -1))

    # a gown that frays into a wisp instead of legs, the wisp trailing
    fade = lit(BAN_BODY, 0.9)
    if d:
        tip = (cx - d * 10.4 - d * abs(sway) * 0.6, tail_y - 2.2 + sway * 0.5)
        pts = [(cx - d * 3.4, top), (cx + d * 3.2, top), (cx + d * 4.8, r.sh_y + 2.6), (cx + d * 4.2, r.waist_y + 0.6),
               (cx + d * 5.2, r.hip_y + 1.0)]
        pts += bez(pts[-1], (cx + d * 4.4, tail_y - 1.0), (cx - d * 2.6, tail_y), 5)[1:]
        pts += bez(pts[-1], (cx - d * 7.6, tail_y + 0.6), tip, 4)[1:]
        pts += bez(tip, (cx - d * 7.6, tail_y - 3.4), (cx - d * 5.2, tail_y - 5.4), 3)[1:]
        pts += [(cx - d * 8.2, r.hip_y + 2.8 + sway * 0.4), (cx - d * 5.6, r.hip_y - 1.0), (cx - d * 4.4, r.waist_y),
                (cx - d * 4.8, r.sh_y + 1.0)]
        gown = Poly(pts)
        fade_reg = Poly([(cx - d * 14.0, tail_y - 4.6), (cx - d * 5.0, tail_y - 4.6), (cx - d * 3.0, tail_y + 3.0),
                         (cx - d * 14.0, tail_y + 3.0)])
    else:
        tip = (cx - 3.0 + sway * 1.8, tail_y)
        pts = [(cx - 4.4, top), (cx + 4.4, top), (cx + 5.6, r.sh_y + 0.6), (cx + 4.4, r.waist_y),
               (cx + 6.8, r.hip_y + 0.6), (cx + 8.0 + sway * 0.5, r.hip_y + 3.8), (cx + 5.0 + sway * 0.6, r.hip_y + 2.4)]
        pts += bez(pts[-1], (cx + 4.4 + sway, tail_y - 0.6), tip, 5)[1:]
        pts += bez(tip, (cx - 0.6 + sway, tail_y - 3.4), (cx - 4.8 + sway * 0.6, r.hip_y + 2.6), 4)[1:]
        pts += [(cx - 8.0 + sway * 0.5, r.hip_y + 3.8), (cx - 6.8, r.hip_y + 0.6), (cx - 4.4, r.waist_y),
                (cx - 5.6, r.sh_y + 0.6)]
        gown = Poly(pts)
        fade_reg = Poly([(cx - 12.0, tail_y - 2.6), (cx + 12.0, tail_y - 2.6), (cx + 12.0, tail_y + 3.0),
                         (cx - 12.0, tail_y + 3.0)])

    def sleeve(side, far=False):
        """A bell sleeve off a raised forearm, ragged at the cuff, and the hand
        hanging limp out of it."""
        col = shade(BAN_BODY, 0.7) if far else BAN_BODY
        lift = wave * (0.5 if side > 0 else -0.5)
        if d:
            fx, fy = (2.0, -2.4) if far else (0.0, 0.0)
            x0, y0 = cx + d * fx, r.sh_y + fy + lift
            shape = Poly([(x0 - d * 1.8, y0 - 0.2), (x0 + d * 6.0, y0 + 1.4), (x0 + d * 8.8, y0 + 0.2), (x0 + d * 9.6, y0 + 4.2),
                          (x0 + d * 7.6, y0 + 9.4), (x0 + d * 5.8, y0 + 6.2), (x0 + d * 3.6, y0 + 5.0), (x0 - d * 0.4, y0 + 4.4),
                          (x0 - d * 2.4, y0 + 2.4)])
            cel(draw, shape, col, sh=(0.8, 1.0), tone=shade(col, 1.2))
            _ban_hand(draw, x0 + d * 10.2, y0 + 2.6, d, far)
        else:
            x0, y0 = cx + side * 3.8, r.sh_y - 0.6
            shape = Poly([(x0, y0), (x0 + side * 2.6, y0 + 0.6), (x0 + side * 7.4, y0 + 5.6 + lift), (x0 + side * 8.6, y0 + 9.4 + lift),
                          (x0 + side * 6.4, y0 + 8.6 + lift), (x0 + side * 5.6, y0 + 11.6 + lift), (x0 + side * 3.8, y0 + 8.2 + lift),
                          (x0 + side * 1.6, y0 + 4.4)])
            cel(draw, shape, col, sh=(0.9, 1.0), tone=shade(col, 1.2))
            _ban_hand(draw, x0 + side * 7.4, y0 + 9.6 + lift, side * 0.5)

    if d:
        sleeve(-d, far=True)
    cel(draw, gown, BAN_BODY, sh=(1.6, 1.0), tone=shade(BAN_BODY, 1.2), regions=[(fade_reg, fade)])
    if not r.back:
        for side in ((-1, 1) if not d else (d,)):
            sleeve(side)
    head_skull(r, draw, BAN_FACE, ears=False)
    if not r.back:
        _ban_face(r, draw, frame)
        rig_hair(r, draw, BAN_HAIR, dict(HAIR_STYLES["long"], side=9.0), skin=BAN_FACE)
        if d:
            # the cap of hair and the stream behind it are one head of hair:
            # paint out the cap's hem where it lies on the stream
            Poly([(hx - d * (rx + 2.0), hy + 6.6), (hx - d * 3.4, hy + 7.4), (hx - d * 4.2, hy + 12.4),
                  (hx - d * (rx + 2.2), hy + 12.2)]).draw(draw, fill=BAN_HAIR)
    else:
        # from behind the hair is everything: the back of her head and the
        # curtain as one mass, a parting, and the gown showing where it parts
        mass, notches = _ban_curtain(hx, hy, rx, ph, True)
        blob(draw, [Ell(hx, hy - 0.6, rx + 1.9, ry + 1.9), mass], BAN_HAIR, sh=(1.3, 1.4), tone=BAN_HAIR_SH)
        strands(notches, (1, 1, -1, -1))
        stroke(draw, bez((hx + 0.4, hy - ry - 0.4), (hx - 0.8, hy - 2.0), (hx + 0.6, hy + 8.0), 6), 0.9, BAN_HAIR_SH)
        for s in (-1, 1):
            stroke(draw, bez((hx + s * 2.6, hy + 9.0), (hx + s * 3.6, hy + 13.0), (hx + s * 1.6, hy + 16.6), 5), 0.9, BAN_HAIR_SH)
    # the wail, rippling off her mouth as she flies
    if d and frame % 2 == 1:
        _, _, _, mx, my = face_anchor(r)
        for rad in (3.0, 5.4):
            pts = arc_pts(mx + d * 3.4, my, rad, rad * 0.8, -42 if d > 0 else 138, 42 if d > 0 else 222, 8)
            stroke(draw, pts, 1.0, BAN_GLOW)


# ===================================================================
# LICH (30) -- a skull under a gem cowl, navy robes, a soul gem staff
# ===================================================================

LICH_ROBE = (44, 58, 104)
LICH_GLOW = (110, 214, 255)
LICH_STAFF = (214, 174, 92)


def _lich_circlet(draw, cx0, top):
    """The gold chevron that stands up off the cowl, and its gem."""
    cel(draw, Poly([(cx0 - 6.8, top + 0.8), (cx0 - 3.6, top - 3.2), (cx0, top - 5.8), (cx0 + 3.6, top - 3.2),
                    (cx0 + 6.8, top + 0.8), (cx0 + 4.2, top + 2.2), (cx0, top - 0.6), (cx0 - 4.2, top + 2.2)]),
        GOLD, sh=(0.5, 0.5))


def draw_lich(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=0.95)
    d = r.d
    # The staff is in his left hand, upright at his side: screen right
    # head-on, screen left from behind. In profile it is carried on the near
    # shoulder: the crystal stands behind the cowl and nothing crosses the
    # skull.
    st_side = (1 if not r.back else -1) if not d else d
    hx, hy, ry = r.hx, r.head_cy, r.head_ry
    if d:
        top = (r.cx - d * 14.2, hy - 8.0 + [0.0, 0.6, 0.0, -0.6][frame])
        bot, grip, out = _carry(r, top)
    else:
        out = 3.4
        grip = hand_at(r, st_side, 0.0, 0.0, out)
        bot = (grip[0] - st_side * 0.5, r.base_y - 1.0)
        top = (grip[0] + st_side * 1.5, hy - 8.4)

    def staff():
        cel(draw, Limb([bot, top], [1.1, 1.0]), LICH_STAFF, sh=(0.5, 0.0))
        ang = math.degrees(math.atan2(top[1] - bot[1], top[0] - bot[0]))
        # gold claws holding the soul gem
        claws = [(-2.4, -1.5), (0.0, -3.9), (3.4, -3.0), (1.6, -1.6), (0.2, 0.0), (1.6, 1.6), (3.4, 3.0), (0.0, 3.9),
                 (-2.4, 1.5)]
        cel(draw, Poly(xform(claws, top[0], top[1], ang)), GOLD, sh=None)
        gx, gy = xform([(0.8, 0.0)], top[0], top[1], ang)[0]
        crystal(draw, gx, gy, ang, 9.0, 5.8, color=LICH_GLOW)
        if frame % 2 == 0:
            tx, ty = xform([(9.8, 0.0)], top[0], top[1], ang)[0]
            sparkle(draw, tx + (st_side if not d else -d) * 3.0, ty + 1.2, 1.9, (226, 250, 255))

    if r.back:
        staff()
    if d:
        rig_arms(r, draw, LICH_ROBE, BONE, layer="far")
    rig_legs(r, draw, shade(LICH_ROBE, 1.0), (40, 40, 60))
    robe = rig_robe(r, draw, LICH_ROBE, flare=3.8)
    if not r.back:
        cx = r.cx + d * 1.8
        cel(draw, Poly([(cx - 2.4, r.sh_y - 1.8), (cx + 2.4, r.sh_y - 1.8), (cx + 3.4, r.base_y - 3.0), (cx - 3.4, r.base_y - 3.0)]),
            (70, 90, 150), sh=(0.6, 0.0))
    _hem_band(draw, robe, r.base_y - 5.2, GOLD, LICH_ROBE)
    if d:
        _held_arm(r, draw, LICH_ROBE, d, out)
        staff()
    else:
        for side in (-1, 1):
            rig_arms(r, draw, LICH_ROBE, BONE, sides=(side,), hands=False, out=out if side == st_side else 0.0)
    if r.back:
        # the chevron's point shows over the top of the cowl
        _lich_circlet(draw, hx, hy - ry)
    else:
        # the dark inside of the cowl, then the skull set back in it
        hood_opening(r, 1.06).draw(draw, fill=VOID)
        skull(draw, hx + d * 1.4, hy + 1.0, 10.0, 9.6, d, glow=LICH_GLOW)
    hood = _hood(r, draw, LICH_ROBE, opening=1.06)
    _cowl_band(r, draw, GOLD)
    if r.back:
        # the circlet runs round the back of the cowl
        band = arc_pts(hx, hy - 12.0, 15.0, 7.4, 20, 160, 12)
        clipped(draw, hood, lambda l: Poly(band + [(x, y + 2.3) for (x, y) in band[::-1]]).draw(l, fill=GOLD))
        clipped(draw, hood, lambda l: stroke(l, [(x, y + 2.4) for (x, y) in band], 0.6, ink(GOLD)))
        clipped(draw, hood, lambda l: stroke(l, band, 0.6, ink(GOLD)))
        hood.draw(draw, fill=None, outline=ink(LICH_ROBE), width=1.0)
    else:
        cx0 = hx + d * 2.0
        _lich_circlet(draw, cx0, hy - ry)
        gem(draw, cx0, hy - ry - 1.6, 2.0, LICH_GLOW)
        # a soul gem for a clasp, where the cowl closes
        gem(draw, hx + d * 4.6, r.sh_y + (1.5 if not d else 0.2), 1.9, LICH_GLOW)
        if not d:
            staff()
    for side in ((-1, 1) if not d else (d,)):
        h = grip if side == st_side else hand_at(r, side)
        bone_arm(draw, (h[0], h[1] - 1.0), (h[0], h[1] + 0.6), BONE, 1.2)


# ===================================================================
# GHOUL (31) -- hunched and lanky, grey-green, two mismatched bulging
# eyes, a grin full of jagged teeth with the tongue lolling out, ragged
# ears, a stitched scalp and patched rags
# ===================================================================

from sprite_base import clipped as _gh_clipped  # noqa: E402  (this block's own: the file's import list is shared)

GH_SKIN = (158, 190, 164)
GH_RAGS = (112, 88, 128)
GH_PATCH = (196, 156, 96)
GH_HAIR = (78, 70, 96)
GH_SCLERA = (252, 246, 186)
GH_PUPIL = (222, 40, 46)
GH_MOUTH = (82, 24, 44)
GH_TONGUE = (196, 96, 150)


def _ghoul_eye(draw, x, y, rx, ry, look):
    """A bulging, staring eye: no iris, a pale yellow ball with a pinprick of
    red, and a bruise of a bag under it."""
    Ell(x, y + ry * 0.62, rx * 1.02, ry * 0.5).draw(draw, fill=shade(GH_SKIN, 2.0))
    cel(draw, Ell(x, y, rx, ry), GH_SCLERA, sh=(0.0, 0.6), line_color=(64, 50, 70), lw=0.7)
    px = x + look * rx * 0.3
    Ell(px, y + 0.3, rx * 0.3, ry * 0.3).draw(draw, fill=GH_PUPIL)
    Ell(px - rx * 0.4, y - ry * 0.42, 0.75, 0.75).draw(draw, fill=(255, 255, 255))


def _claws(draw, h, d, col, near):
    """A big hand with three long claws."""
    cel(draw, Ell(h[0], h[1], 2.6, 2.4), col, sh=(0.5, 0.5))
    for k in ((-1, 0, 1) if not d else (0, 1)):
        x = h[0] + (k * 1.4 if not d else d * (0.6 + k * 1.2))
        Poly([(x - 0.55, h[1] + 1.4), (x + 0.55, h[1] + 1.4), (x + (k * 0.4 if not d else d * 0.8), h[1] + 3.6)]).draw(
            draw, fill=(244, 240, 222) if near else (200, 196, 182))


def draw_ghoul(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=1.0, head=1.0)
    d = r.d
    cx = r.cx
    # hunched: the head hangs low and pushed forward, over a hump of a back
    r.head_cy += 3.0
    r.hx += d * 2.8
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    if d:
        rig_arms(r, draw, GH_SKIN, GH_SKIN, layer="far", reach=0.5, hands=False)
        h = hand_at(r, -d, 0.5)
        _claws(draw, h, d, shade(GH_SKIN, 0.6), False)
    # skinny legs in ragged shorts, big bare feet
    rig_legs(r, draw, GH_SKIN, GH_SKIN, bare=True, width=0.82)
    shorts_y = r.hip_y + 2.6
    if d:
        shorts = Poly([(cx - 4.6, r.waist_y), (cx + 4.8, r.waist_y), (cx + 5.2, shorts_y), (cx + 3.0, shorts_y - 1.4), (cx + 1.0, shorts_y + 0.4),
                       (cx - 1.4, shorts_y - 1.2), (cx - 5.0, shorts_y)])
    else:
        shorts = Poly([(cx - r.hip_w, r.waist_y), (cx + r.hip_w, r.waist_y), (cx + r.hip_w + 0.6, shorts_y), (cx + 4.6, shorts_y - 1.4),
                       (cx + 2.6, shorts_y + 0.2), (cx + 0.6, shorts_y - 2.0), (cx - 1.6, shorts_y), (cx - 3.6, shorts_y - 1.2),
                       (cx - 5.4, shorts_y + 0.4), (cx - r.hip_w - 0.6, shorts_y - 0.4)])
    cel(draw, shorts, shade(GH_RAGS, 0.9), sh=(1.0, 0.0))
    # the shirt: torn rags with a patch, a hump of a back in profile
    top = r.sh_y - 2.4
    bot = r.hip_y - 0.4
    if d:
        # (the hump was drawn, but smaller than the head that hangs in front of it: his back was straight)
        shirt = Poly([(cx + d * 3.4, top + 0.6), (cx + d * 5.0, top + 2.6), (cx + d * 5.2, bot), (cx + d * 3.0, bot - 1.6), (cx + d * 1.0, bot + 0.6),
                      (cx - d * 1.4, bot - 1.0), (cx - d * 4.8, bot)] +
                     bez((cx - d * 5.4, r.sh_y + 5.4), (cx - d * 11.4, r.sh_y - 0.4), (cx - d * 4.0, top - 3.0), 7))
    else:
        w = r.sh_w
        shirt = Poly([(cx - w + 1.6, top), (cx + w - 1.6, top), (cx + w + 0.2, top + 1.4), (cx + r.hip_w + 0.4, bot - 0.6),
                      (cx + 4.8, bot + 1.6), (cx + 2.8, bot - 0.8), (cx + 0.4, bot + 1.8), (cx - 2.0, bot - 0.6), (cx - 4.4, bot + 1.4),
                      (cx - r.hip_w - 0.4, bot - 0.6), (cx - w - 0.2, top + 1.4)])
    cel(draw, shirt, GH_RAGS, sh=(1.4, 0.8), regions=[(Ell(hx, top + 0.6, rx * 0.6, 2.4), shade(GH_RAGS))])
    if not r.back:
        px, py = cx + d * 2.0 - (2.6 if not d else 0.0), r.waist_y - 2.4
        cel(draw, Poly([(px - 1.9, py - 1.7), (px + 1.8, py - 2.0), (px + 2.1, py + 1.6), (px - 1.6, py + 1.9)]), GH_PATCH, sh=None, lw=0.6)
        for k in (-1.0, 0.4):
            stroke(draw, [(px + k - 0.4, py - 2.6), (px + k + 0.4, py - 1.2)], 0.4, shade(GH_PATCH, 2.0))
        stroke(draw, [(cx + d * 2.0 + 3.0, r.sh_y + 1.0), (cx + d * 2.0 + 4.2, r.sh_y + 3.6)], 0.6, shade(GH_RAGS, 1.6))
    else:
        cel(draw, Poly([(cx + 1.0, r.sh_y + 1.0), (cx + 4.4, r.sh_y + 0.6), (cx + 4.6, r.sh_y + 4.0), (cx + 1.2, r.sh_y + 4.2)]), GH_PATCH,
            sh=None, lw=0.6)
    # a rope belt
    # (kept inside the shirt: in profile its end stood out past his back)
    _gh_clipped(draw, shirt, lambda l: stroke(l, [(cx - 9.0, r.waist_y + 0.2), (cx + 9.0, r.waist_y + 0.2)], 1.2, (190, 160, 110)))
    # long arms reaching forward, big clawed hands
    rig_arms(r, draw, GH_SKIN, GH_SKIN, layer="near", reach=0.5, hands=False)
    for side in ((-1, 1) if not d else (d,)):
        _claws(draw, hand_at(r, side, 0.5), d, GH_SKIN, True)
    # ragged ears, behind the head head-on
    for s in ((-1, 1) if not d else ()):
        ex = hx + s * (rx - 1.4)
        ear = Poly([(ex, hy - 2.4), (ex + s * 6.6, hy - 6.0), (ex + s * 5.0, hy - 3.0), (ex + s * 6.8, hy - 1.6),
                    (ex + s * 3.4, hy + 1.6), (ex, hy + 3.0)])
        cel(draw, ear, GH_SKIN, sh=(0.4, 0.4))
        if not r.back:
            stroke(draw, [(ex + s * 1.2, hy + 0.2), (ex + s * 3.8, hy - 3.0)], 0.5, shade(GH_SKIN, 1.4))
    head_skull(r, draw, GH_SKIN, ears=False)
    # a stitched scar across the scalp
    if d:
        scar = [(hx - d * 6.0, hy - 6.4), (hx - d * 0.6, hy - 8.6), (hx + d * 4.0, hy - 8.0)]
    elif r.back:
        scar = [(hx - 6.0, hy - 5.0), (hx - 1.0, hy - 7.0), (hx + 4.0, hy - 6.2)]
    else:
        scar = [(hx + 1.4, hy - 9.8), (hx + 4.6, hy - 7.2), (hx + 6.6, hy - 6.0)]
    stroke(draw, scar, 0.55, shade(GH_SKIN, 2.4))
    for i in range(len(scar) - 1):
        (x0, y0), (x1, y1) = scar[i], scar[i + 1]
        mx_, my_ = (x0 + x1) / 2.0, (y0 + y1) / 2.0
        ln = math.hypot(x1 - x0, y1 - y0) or 1.0
        nx, ny = -(y1 - y0) / ln, (x1 - x0) / ln
        stroke(draw, [(mx_ - nx * 1.1, my_ - ny * 1.1), (mx_ + nx * 1.1, my_ + ny * 1.1)], 0.45, shade(GH_SKIN, 2.4))
    # The lank hair is more than the tuft on his crown: strands of it hang
    # down the back of his head, over the scar (from behind it was a bare ball).
    if r.back:
        lank = ((-5.0, -8.8, -7.2, -1.0), (-1.8, -10.0, -2.8, 3.4), (1.8, -10.0, 3.4, 0.8), (5.0, -8.8, 7.4, -2.8))
    elif d:
        lank = ((-d * 3.4, -10.4, -d * 8.8, -6.6), (-d * 5.6, -9.4, -d * 11.0, -4.4))
    else:
        lank = ()
    for i, (u0, v0, u1, v1) in enumerate(lank):
        bend = 0.9 if i % 2 else -0.9
        cel(draw, Limb([(hx + u0, hy + v0), (hx + (u0 + u1) / 2.0 + bend, hy + (v0 + v1) / 2.0), (hx + u1, hy + v1)], [1.7, 1.4, 0.5]),
            GH_HAIR, sh=None, lw=0.6)
    if d:
        # in profile the ear is on the side of his head, ragged, its tip past the back of the skull
        # (it was drawn before the skull and wholly behind it: a plain ball of a head)
        ex = hx - d * 5.2
        ear = Poly([(ex + d * 1.4, hy - 1.6), (ex - d * 7.8, hy - 6.2), (ex - d * 5.6, hy - 3.0), (ex - d * 8.2, hy - 1.4),
                    (ex - d * 4.0, hy + 2.2), (ex + d * 0.8, hy + 3.2)])
        cel(draw, ear, GH_SKIN, sh=(0.4, 0.4))
        stroke(draw, [(ex - d * 1.0, hy + 0.4), (ex - d * 5.0, hy - 3.2)], 0.5, shade(GH_SKIN, 1.4))
    if not r.back:
        e1, e2, ey, mx, my = face_anchor(r)
        look = float(d)
        if d:
            _ghoul_eye(draw, e2, ey - 0.4, 2.2, 3.4, look)
            _ghoul_eye(draw, e1, ey, 3.4, 4.0, look)
        else:
            _ghoul_eye(draw, e1 - 0.2, ey - 0.6, 3.6, 4.2, 0.2)
            _ghoul_eye(draw, e2 + 0.2, ey + 0.2, 2.8, 3.2, -0.3)
        # a grin full of jagged teeth, the tongue lolling out of one corner
        mcx = hx + d * 4.6
        my += 0.4
        mw = 5.2 if not d else 3.6
        grin = [(mcx - mw, my - 1.6)] + bez((mcx - mw * 0.4, my - 0.6), (mcx, my - 0.4), (mcx + mw, my - 1.6), 3)[1:] + \
            bez((mcx + mw * 0.8, my + 1.6), (mcx, my + 3.6), (mcx - mw * 0.8, my + 1.6), 4)
        Poly(grin).draw(draw, fill=GH_MOUTH)
        tx = mcx + (mw * 0.55 if not d else d * mw * 0.3)
        cel(draw, Limb([(tx, my + 0.6), (tx + 0.6, my + 2.6), (tx + 0.2, my + 4.4)], [1.3, 1.2, 1.0]), GH_TONGUE, sh=None, lw=0.5)
        n = 4 if not d else 3
        for i in range(n):
            t = (i + 0.5) / n
            x = mcx - mw * 0.9 + mw * 1.8 * t
            y = my - 1.4 + 0.6 * abs(t - 0.5) * 2.0
            Poly([(x - 0.9, y - 0.2), (x + 0.9, y - 0.2), (x + 0.2 * (1 if i % 2 else -1), y + 1.9)]).draw(draw, fill=(250, 246, 226))
        for x in ((mcx - mw * 0.4, mcx + mw * 0.2) if not d else (mcx - d * mw * 0.2,)):
            Poly([(x - 0.8, my + 2.6), (x + 0.8, my + 2.6), (x, my + 1.0)]).draw(draw, fill=(250, 246, 226))
    # a few lank strands of hair
    tuft = [(-2.6, -10.0, -5.6, -14.0), (-0.4, -10.8, -1.4, -15.4), (2.0, -10.4, 3.4, -14.6)]
    for (u0, v0, u1, v1) in tuft:
        x0 = hx + (u0 if not d else -d * 1.0 + u0 * 0.6)
        x1 = hx + (u1 if not d else -d * 2.4 + u1 * 0.6)
        cel(draw, Limb([(x0, hy + v0), ((x0 + x1) / 2.0 + 0.8, hy + (v0 + v1) / 2.0), (x1, hy + v1)], [1.2, 0.9, 0.4]), GH_HAIR, sh=None, lw=0.6)


# ===================================================================
# REAPER (32) -- a black hood, a skull in shadow, an enormous scythe
# ===================================================================

REAP_CLOAK = (46, 42, 58)
REAP_GLOW = (255, 60, 70)
REAP_STEEL = (214, 220, 234)


def _reap_blade(draw, top, dirn):
    """The scythe's blade from the top of its shaft: one long crescent that
    rises a little and falls to its point, over his head."""
    x0, y0 = top
    tip = (x0 + dirn * 23.4, y0 + 6.6)
    spine = bez((x0 - dirn * 0.6, y0 - 1.6), (x0 + dirn * 11.0, y0 - 6.6), (x0 + dirn * 21.0, y0 + 1.0), 9)
    edge = bez(tip, (x0 + dirn * 12.0, y0 - 0.6), (x0 - dirn * 0.6, y0 + 2.8), 9)
    mid = bez(tip, (x0 + dirn * 11.6, y0 - 3.0), (x0 - dirn * 0.6, y0 + 0.8), 9)
    cel(draw, Poly(spine + edge), REAP_STEEL, sh=None, regions=[(Poly(mid[::-1] + edge), shade(REAP_STEEL, 0.9))])
    stroke(draw, bez((x0 + dirn * 2.6, y0 - 1.6), (x0 + dirn * 10.8, y0 - 4.8), (x0 + dirn * 18.4, y0 - 0.6), 6), 0.6,
           (255, 255, 255))
    cel(draw, Ell(x0, y0 + 0.4, 1.9, 1.9), (150, 150, 170), sh=(0.5, 0.5))


def draw_reaper(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, bob=[0, -1, -1, 0][frame], step=0)
    d = r.d
    # The scythe is in his left hand, upright at his side: screen right
    # head-on, screen left from behind. In profile it is carried on the near
    # shoulder. In every facing its blade arcs over his head, clear of the
    # hood and the skull.
    sc_side = (1 if not r.back else -1) if not d else d
    sway = [0.0, 0.8, 0.0, -0.8][frame]
    if d:
        top = (r.cx - d * 12.6, r.head_cy - 20.5)
        bot, grip, out = _carry(r, top, 0.2)
    else:
        out = 3.0
        grip = hand_at(r, sc_side, 0.2, 0.0, out)
        top = (r.cx + sc_side * 13.0, r.head_cy - 20.5)
        k = (r.base_y - 1.0 - grip[1]) / (grip[1] - top[1])
        bot = (grip[0] + (grip[0] - top[0]) * k, r.base_y - 1.0)

    def scythe():
        cel(draw, Limb([bot, top], [1.15, 1.0]), (132, 96, 68), sh=(0.5, 0.0))
        _reap_blade(draw, top, d if d else -sc_side)

    if r.back:
        scythe()
    cx = r.cx
    top_y = r.sh_y - 2.4
    hem = r.base_y - 0.8
    if d:
        pts = [(cx - d * 3.4, top_y), (cx + d * 3.0, top_y), (cx + d * 5.4, r.hip_y), (cx + d * 6.0 + sway, hem)]
        for i in range(4):
            t = (i + 0.5) / 4.0
            pts += [(cx + d * (6.0 - 14.0 * t) + sway, hem + (1.2 if i % 2 == 0 else -1.6))]
        pts += [(cx - d * 8.4 + sway, hem - 2.0), (cx - d * 5.0, r.hip_y)]
    else:
        w = r.sh_w
        pts = [(cx - w + 1.8, top_y), (cx + w - 1.8, top_y), (cx + w, r.sh_y + 0.6), (cx + w + 5.0 + sway, hem)]
        for i in range(1, 6):
            t = i / 6.0
            pts.append((cx + w + 5.0 + sway - (2 * w + 10.0) * t, hem + (1.2 if i % 2 else -1.8)))
        pts += [(cx - w - 5.0 + sway, hem), (cx - w, r.sh_y + 0.6)]
    cel(draw, Poly(pts), REAP_CLOAK, sh=(1.8, 1.0))
    if d:
        rig_arms(r, draw, REAP_CLOAK, BONE, layer="far", hands=False)
        _held_arm(r, draw, REAP_CLOAK, d, out, 0.2)
        scythe()
    else:
        for side in (-1, 1):
            rig_arms(r, draw, REAP_CLOAK, BONE, sides=(side,), hands=False, reach=0.2,
                     out=out if side == sc_side else 0.0)
    if not r.back:
        # the dark inside of the hood, then the skull set back in it
        hood_opening(r, 0.96).draw(draw, fill=VOID)
        skull(draw, r.hx + d * 1.0, r.head_cy + 1.4, 8.6, 8.4, d, glow=REAP_GLOW)
    hood = _hood(r, draw, REAP_CLOAK, peak=4.0, opening=0.96)
    if r.back:
        # a fold down the back of the hood, from its point
        hx, hy, ry = r.hx, r.head_cy, r.head_ry
        clipped(draw, hood, lambda l: Poly([(hx - 1.0, hy - ry - 4.0), (hx + 4.4, hy - 1.0), (hx + 1.6, hy + 9.0)]).draw(
            l, fill=shade(REAP_CLOAK, 1.8)))
    elif not d:
        scythe()
    for side in ((-1, 1) if not d else (d,)):
        h = grip if side == sc_side else hand_at(r, side, 0.2)
        cel(draw, Ell(h[0], h[1], 1.8, 1.8), BONE, sh=None, lw=0.8)
        for k in (-1, 0, 1):
            stroke(draw, [(h[0] + k * 1.0, h[1] + 1.0), (h[0] + k * 1.2, h[1] + 2.6)], 0.5, BONE)


# ===================================================================
# SHADE (33) -- a living shadow: smoke, two lilac eyes, a grin
# ===================================================================

SHADE_BODY = (48, 34, 72)
SHADE_EDGE = (86, 62, 124)
SHADE_GLOW = (214, 164, 255)


def _shade_tongue(x, y, h, lean, curl):
    """A tongue of smoke rising from (x, y): it leans, and curls over at the tip."""
    return Limb([(x, y + 2.4), (x + lean * 0.3, y - h * 0.42), (x + lean * 0.8 + curl * 0.5, y - h * 0.8),
                 (x + lean * 0.7 + curl * 2.2, y - h)], [3.5, 2.9, 1.8, 0.6])


def draw_shade(draw, ox, oy, direction, frame):
    d, back = _dir(direction)
    bob = [0, -1, -2, -1][frame]
    base = oy + 54 + bob
    cx = ox + 32
    wave = [0.0, 1.0, 0.0, -1.0][frame]
    hy = base - 24.0
    # One mass of smoke with one outline: the head, a body narrowing to a
    # tail, three tongues of smoke curling off the top (of different heights,
    # leaning back as it moves) and long thin arms ending in fingers. (Four
    # outlined flames in a row on the head were a paw print.)
    parts = [Ell(cx, hy, 12.4, 11.0), Ell(cx - d * 1.0, base - 13.0, 8.6, 7.6),
             Ell(cx + wave - d * 2.0, base - 6.0, 5.0, 4.2), Ell(cx + wave * 1.6 - d * 3.0, base - 2.0, 2.6, 2.4)]
    for i, (u, h) in enumerate(((-7.4, 8.0), (-0.6, 13.6), (6.6, 10.0))):
        s = 1 if i % 2 else -1
        y0 = hy - 11.0 * math.sqrt(1.0 - (u / 12.4) ** 2)
        parts.append(_shade_tongue(cx + u - d * 1.4, y0, h + wave * s * 0.8, -d * 3.4 + wave * s * 1.6, s * 1.1 - d * 0.6))
    for side in ((-1, 1) if not d else (d,)):
        sw = wave * side
        if d:
            a0, a1, hand = (cx + d * 6.0, base - 21.0), (cx + d * 11.6, base - 20.4 - sw), (cx + d * 15.0, base - 15.6 + sw)
        else:
            a0, a1, hand = (cx + side * 8.6, base - 21.0), (cx + side * 14.2, base - 19.8 - sw), (cx + side * 16.4, base - 14.2 + sw)
        parts.append(Limb([a0, a1, hand], [2.8, 2.0, 1.7]))
        parts.append(Ell(hand[0], hand[1], 2.1, 1.9))
        for k in (-1, 0, 1):
            parts.append(Limb([hand, (hand[0] + k * 1.8 + (d if d else side) * 0.5, hand[1] + 3.6 - abs(k) * 0.6)], [1.1, 0.5]))
    # light catching the smoke's upper left edge, in place of a shine
    rim = Poly(arc_pts(cx, hy, 12.4, 11.0, 150, 292, 14) + arc_pts(cx + 2.0, hy + 2.0, 12.4, 11.0, 286, 156, 14))
    blob(draw, parts, SHADE_BODY, sh=(1.8, 1.4), tone=shade(SHADE_BODY, 0.8), regions=[(rim, SHADE_EDGE)])
    # wisps of it coming away: they rise and thin out over the four frames
    for i, (u, v) in enumerate(((-10.4, -19.0), (9.4, -22.0))):
        f = (frame + i * 2) % 4
        cel(draw, Ell(cx + u - d * 4.0 + (f - 1.5) * 0.5 * (1 if i else -1), hy + v - f * 1.3, 2.1 - f * 0.3, 1.9 - f * 0.3),
            SHADE_EDGE, sh=None, lw=0.7)
    if back:
        # smoke curling on its back
        curl = [(cx + 0.6 + (6.6 - t * 5.0) * math.cos(math.radians(200 + t * 330)),
                 hy + 0.4 + (5.6 - t * 4.2) * math.sin(math.radians(200 + t * 330))) for t in (i / 12.0 for i in range(13))]
        stroke(draw, curl, 1.3, SHADE_EDGE)
        stroke(draw, bez((cx - 3.6, base - 15.6), (cx + 3.4, base - 17.6), (cx + 3.0, base - 11.4), 6), 1.2, SHADE_EDGE)
        return
    fx = cx + d * 4.0
    for side, ex in (((-1, fx - 4.6), (1, fx + 4.6)) if not d else ((d, fx + d * 3.0), (-d, fx - d * 2.6))):
        w = 1.0 if not d or side == d else 0.66
        Ell(ex, base - 26.0, 2.8 * w, 3.6).draw(draw, fill=SHADE_GLOW)
        Ell(ex - 0.6 * w, base - 26.8, 1.0 * w, 1.1).draw(draw, fill=(255, 255, 255))
    # a crescent grin, wide enough to be seen
    mx = fx + d * 1.0
    gw = 5.4 if not d else 3.8
    pts = arc_pts(mx, base - 21.8, gw, 3.2, 8, 172, 10) + arc_pts(mx, base - 22.2, gw - 0.9, 1.3, 172, 8, 10)
    Poly(pts).draw(draw, fill=SHADE_GLOW)


# ===================================================================
# REVENANT (34) -- rusted armour, a tattered cape, teal ghost-fire
# ===================================================================

REV_RUST = (160, 92, 58)        # rust, in areas: along a rim, up from a hem, never a spot (seven round spots were measles)
REV_RUST_DK = (116, 68, 54)     # its shadow side, and the iron of the hilt
REV_ARMOR = (126, 138, 132)
REV_PLATE = (150, 162, 154)     # the lit plates: the helm, the pauldrons, the gauntlets
REV_GHOST = (96, 240, 214)
REV_CAPE = (98, 72, 72)         # a cloak the colour of old blood: dark and warm, behind steel and against the deck's teal
GHOST_FIRE = ((50, 170, 160), (96, 240, 214), (220, 255, 246))

# The file's import list belongs to both halves of it; what only this half uses comes in here.
from sprite_base import CLEAR, Fixed, clipped, torso_shape  # noqa: E402


def _torn_cape(r, draw, cloth, layer=None, lining=None, length=None, width=1.0, rag=1.0):
    """The kit's rig_cape with its hem in rags, for the three undead knights (the Revenant,
    the Dullahan, the Death Knight): their cloaks have been in the ground.

    The same three views as the kit's -- behind the body head-on and in profile
    (layer="under"), over it from behind (layer="over") -- and the same sway with the
    stride; the hem is cut into uneven tongues instead of being ruled straight, and in
    profile it streams out behind a stride."""
    if lining is None:
        lining = shade(cloth, 1.3)
    if (layer == "under" and r.back) or (layer == "over" and not r.back):
        return
    sway = r.ph * 1.2
    bottom = r.base_y - 1.8 if length is None else length
    cx, d = r.cx, r.d
    w = r.sh_w * width

    def hem(x0, x1, cuts):
        n = len(cuts) - 1
        return [(x0 + (x1 - x0) * i / float(n), bottom - cuts[i] * rag) for i in range(n + 1)]

    if d:
        back = 8.2 + abs(sway) * 1.5
        lin = [(cx - d * 1.0, r.sh_y - 2.6), (cx - d * 4.6, r.sh_y - 1.0)] + \
            hem(cx - d * back, cx - d * 1.2, (0.0, 2.6, 0.2, 3.0, 0.8)) + [(cx + d * 1.0, r.hip_y - 2)]
        cel(draw, Poly(lin), lining, sh=(0.0, 0.0))
        cel(draw, Poly([(cx - d * 1.2, r.sh_y - 2.4), (cx - d * 4.6, r.sh_y - 0.8)] +
                       hem(cx - d * back, cx - d * (back - 3.4), (0.0, 2.6, 0.2)) +
                       [(cx - d * 3.4, r.hip_y)]), cloth, sh=(0.8, 0.0))
        return
    cuts = (0.0, 2.4, 0.3, 3.2, 0.0, 2.0, 0.5, 3.0, 0.0)
    if r.back:
        shape = Poly([(cx - w + 0.6, r.sh_y - 2.8), (cx + w - 0.6, r.sh_y - 2.8)] +
                     hem(cx + w + 2.2 + sway, cx - w - 2.2 + sway, cuts))
        cel(draw, shape, cloth, sh=(1.8, 0.0),
            regions=[(Poly([(cx - 0.8, r.sh_y), (cx + 0.8, r.sh_y), (cx + 1.4 + sway, bottom),
                            (cx - 1.4 + sway, bottom)]), shade(cloth, 0.8))])
        return
    shape = Poly([(cx - w + 0.4, r.sh_y - 2.8), (cx + w - 0.4, r.sh_y - 2.8)] +
                 hem(cx + w + 3.0 + sway, cx - w - 3.0 + sway, cuts))
    cel(draw, shape, lining, sh=(1.2, 0.0))


from sprite_base import shoe  # noqa: E402


def _rev_helm(r, draw, m):
    """The bucket helm, battered: cleft by the blow that killed him (a V in its top, off to one
    side, with the fire showing in it and a crack running on down to the eye slit), that corner
    crushed and rusted, rust creeping up from the rim and weeping from the slit. `m` is the side
    the cleft is on: +1 screen right (head-on), -1 screen left (from behind), -d in profile, where
    it is toward the back.

    (It was a clean hexagon with two round spots on it: the Crusader's helm in grey.)"""
    hx, hy, rx, ry, d = r.hx, r.head_cy, r.head_rx, r.head_ry, r.d
    top, rim = hy - ry + 0.6, hy + ry - 1.8

    def X(u):
        return hx + m * u

    helm = Poly([(X(-rx + 0.6), top + 3.2), (X(-rx * 0.42), top), (X(rx * 0.14), top), (X(rx * 0.34), top + 4.8),
                 (X(rx * 0.52), top + 1.3), (X(rx - 0.6), top + 4.6), (X(rx + 0.2), rim - 1.6), (X(rx - 0.4), rim),
                 (X(-rx + 0.4), rim), (X(-rx - 0.2), rim - 1.6)])
    rust = [
        # the crushed corner beyond the cleft
        Poly([(X(rx * 0.3), top - 1), (X(rx + 2), top - 1), (X(rx + 2), top + 8.4), (X(rx * 0.7), top + 6.2),
              (X(rx * 0.44), top + 7.8)]),
        # creeping up from the rim
        Poly([(X(-rx - 2), rim - 2.8), (X(-rx * 0.62), rim - 4.4), (X(-rx * 0.22), rim - 2.6), (X(rx * 0.24), rim - 3.8),
              (X(rx * 0.6), rim - 2.4), (X(rx + 2), rim - 4.6), (X(rx + 2), rim + 1), (X(-rx - 2), rim + 1)]),
    ]
    if not r.back:
        # weeping from the end of the slit
        rust.append(Poly([(X(-rx * 0.78), hy + 2.4), (X(-rx * 0.5), hy + 2.4), (X(-rx * 0.56), rim), (X(-rx * 0.74), rim)]))
    cel(draw, helm, REV_PLATE, sh=(1.6, 1.2), hi=(0.8, 0.8), regions=[(shp, REV_RUST_DK) for shp in rust], line=False)
    # the rust's lit side: the same areas again, kept to where the plate itself is lit
    lit_rust = draw.sub()
    clipped(lit_rust, helm, lambda l: [shp.draw(l, fill=REV_RUST) for shp in rust])
    lit_face = draw.sub()
    helm.shift(-1.6, -1.2).draw(lit_face, fill=(255, 255, 255))
    draw.merge(lit_rust, clip=lit_face)
    # the crack, from the cleft down to the slit (or half way down the back), the fire's light in it
    crack = [(X(rx * 0.34), top + 4.4), (X(rx * 0.26), top + 6.4), (X(rx * 0.36), top + 7.8), (X(rx * 0.29), top + 10.0)]
    stroke(draw, crack, 1.5, VOID)
    stroke(draw, crack, 0.7, REV_GHOST)
    if not r.back:
        if d:
            x0, x1 = hx + d * 0.6, hx + d * (rx + 1.0)
            eyes = ((hx + d * 7.4, 1.0),)
            slots = (5.6, 7.6)
        else:
            x0, x1 = hx - 9.8, hx + 9.8
            eyes = ((hx - 4.2, 1.0), (hx + 4.2, 1.0))
            slots = (-7.2, -5.4)
        clipped(draw, helm, lambda l: RRect(min(x0, x1), hy - 0.8, max(x0, x1), hy + 3.0, 1.2).draw(l, fill=VOID))
        for (ex, w) in eyes:
            Ell(ex, hy + 1.1, 2.3 * w, 1.55).draw(draw, fill=REV_GHOST)
            Ell(ex - 0.3, hy + 0.9, 1.2 * w, 0.8).draw(draw, fill=GHOST_FIRE[2])
        for u in slots:
            sx = hx + (d if d else 1) * u
            RRect(sx - 0.5, hy + 4.4, sx + 0.5, hy + 6.8, 0.4).draw(draw, fill=VOID)
    else:
        # a seam across the back plate
        clipped(draw, helm, lambda l: stroke(l, [(hx - rx, hy + 1.0), (hx + rx, hy + 1.0)], 0.7, shade(REV_PLATE, 1.7)))
    helm.draw(draw, fill=None, outline=ink(REV_PLATE), width=1.0)


def _rev_sword(draw, hand, ang, flip, sway):
    """His sword, in the hand at `hand`: snapped off a hand's breadth past the guard, and the
    blade it lost made up in ghost-fire. (It was the kit's sword, short, with a candle flame
    standing on its tip on the ground, and its guard and pommel two loose balls by the hand.)"""
    x, y = xform([(2.2, 0.0)], hand[0], hand[1], ang)[0]

    def at(pts):
        return xform(pts, x, y, ang, 1.0, flip)

    for (u, w, h, lean) in ((8.6, 3.6, 5.6, 1.0), (11.8, 3.2, 4.6, -1.0)):
        px, py = at([(u, 0.0)])[0]
        flame(draw, px, py + 1.0, w, h, GHOST_FIRE, sway=sway * lean + lean * 0.6)
    cel(draw, Poly(at([(5.0, -2.1), (9.8, -2.2), (12.4, -1.5), (14.8, 0.0), (12.4, 1.5), (9.8, 2.2), (5.0, 2.1)])),
        GHOST_FIRE[1], sh=None, line_color=ink(GHOST_FIRE[0]), lw=0.8)
    Poly(at([(5.0, -0.9), (10.4, -1.0), (13.0, 0.0), (10.4, 1.0), (5.0, 0.9)])).draw(draw, fill=GHOST_FIRE[2])
    steel = (190, 202, 198)
    stub = [(0.4, -2.1), (3.0, -2.1), (3.6, -1.2), (4.3, -2.1), (5.6, -2.0), (6.8, -0.9), (5.7, -0.1), (7.6, 0.9), (6.2, 2.1),
            (0.4, 2.1)]
    cel(draw, Poly(at(stub)), steel, sh=None,
        regions=[(Poly(at([(0.0, 0.0), (9.0, 0.0), (9.0, 3.0), (0.0, 3.0)])), shade(steel, 0.9)),
                 (Poly(at([(0.0, -3.0), (2.2, -3.0), (1.4, 0.4), (2.4, 3.0), (0.0, 3.0)])), REV_RUST)])
    cel(draw, Limb(at([(-4.0, 0.0), (0.0, 0.0)]), [0.95, 0.95]), (70, 60, 60), sh=None, lw=0.7)
    px, py = at([(-4.6, 0.0)])[0]
    cel(draw, Ell(px, py, 1.45, 1.45), REV_RUST_DK, sh=None, lw=0.7)
    cel(draw, Poly(at([(-0.5, -3.7), (0.9, -3.7), (0.9, 3.7), (-0.5, 3.7)])), REV_RUST_DK, sh=None, lw=0.8)


def _rev_legs(r, draw):
    """Steel thighs over greaves gone to rust: legs that are not the torso's grey, and a stride
    that can be seen."""
    for side in ((-r.d, r.d) if r.d else (-1, 1)):
        (hx0, hy0), (fx, fy) = r.hip(side), r.foot(side)
        near = r.near(side)
        w0 = 2.6 * 1.08 * min(r.build, 1.25)
        cel(draw, Limb([(hx0, hy0), (fx, fy - 1.2)], [w0, w0 * 0.86]), REV_ARMOR if near else shade(REV_ARMOR, 0.7),
            sh=(0.9, 0.0))
        kx, ky = hx0 + (fx - hx0) * 0.4, hy0 + (fy - hy0) * 0.4
        cel(draw, Limb([(kx, ky), (fx, fy - 1.2)], [w0, w0 * 0.88]), REV_RUST if near else REV_RUST_DK, sh=(0.9, 0.0),
            tone=REV_RUST_DK if near else shade(REV_RUST_DK, 0.6))
        shoe(r, draw, fx, fy, REV_RUST_DK, near)


def draw_revenant(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=1.14)
    d = r.d
    cx = r.cx
    sw_side = _hold_side(r)
    sway = [0.0, 1.0, 0.0, -1.0][frame]
    stride = frame % 2
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    m = (-1 if r.back else 1) if not d else -d

    def steady(fn, *args, **kw):
        ph = r.ph
        r.ph = ph * 0.25
        try:
            return fn(*args, **kw)
        finally:
            r.ph = ph

    # the sword arm: low and out to the side head-on, low and ahead in profile, and held still
    grip = dict(out=0.6) if not d else dict(reach=0.35)

    _torn_cape(r, draw, REV_CAPE, layer="under", width=1.06, rag=1.5)
    if d:
        rig_arms(r, draw, REV_ARMOR, REV_PLATE, layer="far")
    _rev_legs(r, draw)
    rig_torso(r, draw, REV_ARMOR)
    torso = torso_shape(r)
    if not r.back:
        # rust eating up from the belt, and the wound that killed him, the fire's light in it
        wx, wy = cx + d * 2.6, r.sh_y + 3.4

        def plate(l):
            Poly([(cx - 9, r.waist_y - 1.6), (cx - 3.4, r.waist_y - 3.4), (cx - 0.6, r.waist_y - 1.2), (cx + 2.4, r.waist_y - 2.4),
                  (cx + 9, r.waist_y - 0.6), (cx + 9, r.hip_y + 2), (cx - 9, r.hip_y + 2)]).draw(l, fill=REV_RUST)
            Poly([(wx - 2.8, wy - 2.8), (wx + 0.7, wy - 0.9), (wx + 2.8, wy + 2.8), (wx - 0.7, wy + 0.9)]).draw(l, fill=VOID)
            Poly([(wx - 1.8, wy - 1.9), (wx + 0.4, wy - 0.5), (wx + 1.9, wy + 2.0), (wx - 0.4, wy + 0.5)]).draw(l, fill=REV_GHOST)

        clipped(draw, torso, plate)
        torso.draw(draw, fill=None, outline=ink(REV_ARMOR), width=1.0)
    rig_belt(r, draw, shade(REV_RUST_DK, 1.0), buckle=REV_PLATE)
    for side in _sides(r):
        if side == sw_side:
            steady(rig_arms, r, draw, REV_ARMOR, REV_PLATE, layer="near", hands=False, sides=(side,), **grip)
        else:
            rig_arms(r, draw, REV_ARMOR, REV_PLATE, layer="near", hands=False, sides=(side,))
    _torn_cape(r, draw, REV_CAPE, layer="over", width=1.06, rag=1.5)
    if r.back:
        # the same wound, out through his back and the cape, and a rip up from the hem
        wx, wy = cx - 1.6, r.sh_y + 5.0
        Poly([(wx - 3.2, wy + 3.0), (wx - 0.9, wy - 0.3), (wx + 3.2, wy - 3.0), (wx + 0.9, wy + 0.3)]).draw(draw, fill=VOID)
        Poly([(wx - 2.2, wy + 2.1), (wx - 0.5, wy - 0.2), (wx + 2.3, wy - 2.1), (wx + 0.5, wy + 0.2)]).draw(draw, fill=REV_GHOST)
    # pauldrons: steel, rusted along their lower edge
    for side in _sides(r):
        sx, sy = r.shoulder(side)
        px = sx + (side * 1.4 if not d else 0.0)
        shape = Poly([(px - 5.2, sy + 1.6), (px - 4.2, sy - 2.4), (px, sy - 4.0), (px + 4.2, sy - 2.4), (px + 5.2, sy + 1.6)])
        cel(draw, shape, REV_PLATE, sh=(0.8, 0.8), hi=(0.5, 0.5),
            regions=[(Poly([(px - 7, sy - 0.2), (px + 7, sy - 0.6), (px + 7, sy + 3), (px - 7, sy + 3)]), REV_RUST)])
    # ghost-fire in the helm, out through its top
    top = hy - ry + 0.6
    flame(draw, hx + m * 1.6 - d * 1.0, top + 6.0, 19.0, 16.4 + stride * 1.5, GHOST_FIRE, sway=sway * 1.5 - d * 3.0,
          flip=-1.0 if stride else 1.0)
    _rev_helm(r, draw, m)
    # the sword
    h = steady(hand_at, r, sw_side, **grip)
    _rev_sword(draw, h, 90 - sw_side * 52 if not d else 90 - d * 58, sw_side if not d else d, sway)
    for side in _sides(r):
        if side == sw_side:
            steady(rig_hand, r, draw, side, REV_PLATE, **grip)
        else:
            rig_hand(r, draw, side, REV_PLATE)


# ===================================================================
# GRAVEDIGGER (35) -- battered hat, patched coat, a big shovel
# ===================================================================

GD_COAT = (122, 94, 70)
GD_HAT = (84, 76, 80)
GD_PATCH = (170, 140, 96)
GD_SKIN = (238, 214, 190)
GD_HAIR = (120, 110, 100)
GD_GLOVE = (150, 120, 90)
GD_STEEL = (176, 182, 196)
GD_IRON = (74, 64, 66)
GD_LAMP = (255, 214, 96)


def _gd_shovel(draw, grip, socket):
    """A grave-digger's spade from the fist at `grip` to the blade's socket, the blade running
    on past it: a blade as broad as his chest, because the shovel is who he is."""
    ang = math.degrees(math.atan2(socket[1] - grip[1], socket[0] - grip[0]))
    ln = math.hypot(socket[0] - grip[0], socket[1] - grip[1])
    cel(draw, Limb(xform([(-3.6, 0.0), (ln + 0.6, 0.0)], grip[0], grip[1], ang), [1.0, 1.0]), WOOD, sh=(0.4, 0.0))
    cel(draw, Poly(xform([(-4.8, -2.4), (-3.4, -2.4), (-3.4, 2.4), (-4.8, 2.4)], grip[0], grip[1], ang)), WOOD, sh=None)
    spade = [(0.0, -1.3), (0.6, -4.7), (5.6, -4.7), (9.0, -2.9), (10.8, 0.0), (9.0, 2.9), (5.6, 4.7), (0.6, 4.7), (0.0, 1.3)]
    cel(draw, Poly(xform(spade, socket[0], socket[1], ang)), GD_STEEL, sh=(0.9, 0.7), hi=(0.6, 0.6))
    stroke(draw, xform([(0.4, 0.0), (5.4, 0.0)], socket[0], socket[1], ang), 0.7, shade(GD_STEEL, 1.6))
    cel(draw, Poly(xform([(7.4, -2.2), (9.6, -1.0), (10.3, 0.0), (9.4, 1.6), (7.8, 1.0)], socket[0], socket[1], ang)),
        (128, 98, 70), sh=None, line=False)


def _gd_lantern(draw, x, y, swing):
    """A lantern hanging from the fist at (x, y), swinging a little with the walk."""
    bx, top = x + swing, y + 3.0
    stroke(draw, [(x, y + 1.2), (bx, top + 0.4)], 0.7, GD_IRON)
    cel(draw, RRect(bx - 2.5, top + 1.0, bx + 2.5, top + 6.6, 0.8), GD_LAMP, sh=None, line_color=GD_IRON, lw=0.8)
    Ell(bx, top + 4.0, 1.2, 1.7).draw(draw, fill=(255, 250, 216))
    cel(draw, Poly([(bx - 2.3, top + 1.3), (bx - 1.1, top - 0.1), (bx + 1.1, top - 0.1), (bx + 2.3, top + 1.3)]), GD_IRON,
        sh=None, lw=0.6)
    cel(draw, RRect(bx - 2.8, top + 6.2, bx + 2.8, top + 7.4, 0.4), GD_IRON, sh=None, lw=0.6)


def draw_gravedigger(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=1.06)
    d = r.d
    cx = r.cx
    sh_side = _hold_side(r)
    lamp_side = -sh_side
    wave = [0.0, 1.0, 0.0, -1.0][frame]
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry

    def steady(fn, *args, **kw):
        ph = r.ph
        r.ph = ph * 0.25
        try:
            return fn(*args, **kw)
        finally:
            r.ph = ph

    # the shovel rides on his shoulder, blade up behind the hat: it stood in front of him, and in
    # profile its shaft ran up the middle of his face
    hold = dict(reach=0.6) if not d else dict(reach=0.5)
    grip = steady(hand_at, r, sh_side, **hold)
    if d:
        socket = (cx - d * 15.0, r.head_cy - 6.0)
    else:
        socket = (cx + sh_side * 13.4, r.head_cy - 7.0)
    lamp = dict(reach=0.5, out=4.0) if d else {}
    lamp_hand = steady(hand_at, r, lamp_side, **lamp)

    if d:
        s, e, w, h = steady(arm_pts, r, -d, **lamp)
        aw = 2.2 * min(r.build, 1.3)
        cel(draw, Limb([s, e, w], [aw, aw * 0.9, aw * 0.82]), shade(GD_COAT, 0.6), sh=(0.8, 0.4))
    rig_legs(r, draw, (86, 80, 90), (70, 54, 44))
    rig_robe(r, draw, GD_COAT, flare=2.4, hem=r.hip_y + 4.0)
    if not r.back:
        cel(draw, RRect(cx - 5.0 + d * 1.0, r.hip_y - 1.0, cx - 2.0 + d * 1.0, r.hip_y + 2.0, 0.6), GD_PATCH, sh=None)
        stroke(draw, [(cx - 4.6 + d, r.hip_y - 0.6), (cx - 2.4 + d, r.hip_y + 1.6)], 0.4, shade(GD_PATCH, 1.6))
    else:
        cel(draw, RRect(cx + 1.2, r.sh_y + 2.4, cx + 4.8, r.sh_y + 6.0, 0.6), GD_PATCH, sh=None)
        stroke(draw, [(cx + 1.8, r.sh_y + 3.0), (cx + 4.2, r.sh_y + 5.4)], 0.4, shade(GD_PATCH, 1.6))
    rig_belt(r, draw, (80, 60, 44), buckle=(200, 190, 170))
    if d:
        _gd_lantern(draw, lamp_hand[0], lamp_hand[1], -d * wave * 0.8)
        cel(draw, Ell(lamp_hand[0], lamp_hand[1], 2.2, 2.2), shade(GD_GLOVE, 0.5), sh=(0.6, 0.6))
        steady(rig_arms, r, draw, GD_COAT, GD_GLOVE, layer="near", hands=False, **hold)
    else:
        rig_arms(r, draw, GD_COAT, GD_GLOVE, layer="near", hands=False, sides=(lamp_side,))
        steady(rig_arms, r, draw, GD_COAT, GD_GLOVE, layer="near", hands=False, sides=(sh_side,), **hold)
        _gd_lantern(draw, lamp_hand[0], lamp_hand[1], wave * 0.8)
        rig_hand(r, draw, lamp_side, GD_GLOVE)
    if not r.back:
        _gd_shovel(draw, grip, socket)
        steady(rig_hand, r, draw, sh_side, GD_GLOVE, **hold)
    # the head: a stubbled jaw, hair only where the hat leaves it
    head_skull(r, draw, GD_SKIN, ears=not d)
    skull_clip = Ell(hx, hy, rx - 0.5, ry - 0.5)
    hair_line = ink(GD_HAIR)
    if not r.back:
        clipped(draw, skull_clip, lambda l: Ell(hx + d * 3.0, hy + 10.4, rx * 0.9, 5.6).draw(l, fill=mix(GD_SKIN, (146, 132, 128), 0.34)))
        head_face(r, draw, GD_SKIN, iris=(80, 90, 70), mood="calm", mouth="set", blush=False, brows=True,
                  brow_color=(90, 80, 76))

    def hair(l):
        if r.back:
            Poly([(hx - rx - 1.0, hy - 8.0), (hx + rx + 1.0, hy - 8.0), (hx + rx + 1.0, hy + 5.0)] +
                 bez((hx + rx - 2.0, hy + 8.2), (hx, hy + 11.4), (hx - rx + 2.0, hy + 8.2), 6) +
                 [(hx - rx - 1.0, hy + 5.0)]).draw(l, fill=GD_HAIR)
            Poly([(hx + 2.0, hy - 8.0), (hx + rx + 1.0, hy - 8.0), (hx + rx + 1.0, hy + 9.0), (hx + 6.0, hy + 10.0)]).draw(
                l, fill=shade(GD_HAIR))
        elif d:
            Poly([(hx - d * 1.2, hy - 8.0), (hx - d * (rx + 1.0), hy - 8.0), (hx - d * (rx + 1.0), hy + 6.4),
                  (hx - d * 6.6, hy + 8.8), (hx - d * 4.2, hy + 4.6), (hx - d * 2.4, hy + 1.6), (hx - d * 0.4, hy + 3.0),
                  (hx + d * 0.6, hy - 2.0)]).draw(l, fill=GD_HAIR)
            stroke(l, [(hx - d * 6.6, hy + 8.8), (hx - d * 4.2, hy + 4.6), (hx - d * 2.4, hy + 1.6), (hx - d * 0.4, hy + 3.0),
                       (hx + d * 0.6, hy - 2.0)], 0.7, hair_line)
        else:
            for s2 in (-1, 1):
                Poly([(hx + s2 * (rx - 3.6), hy - 8.0), (hx + s2 * (rx + 1.0), hy - 8.0), (hx + s2 * (rx + 1.0), hy + 2.6),
                      (hx + s2 * (rx - 2.0), hy + 1.6), (hx + s2 * (rx - 2.8), hy - 2.0)]).draw(l, fill=GD_HAIR)
                stroke(l, [(hx + s2 * (rx - 3.6), hy - 8.0), (hx + s2 * (rx - 2.8), hy - 2.0), (hx + s2 * (rx - 2.0), hy + 1.6),
                           (hx + s2 * (rx + 1.0), hy + 2.6)], 0.7, hair_line)

    clipped(draw, skull_clip, hair)
    if r.back:
        # the coat's collar, turned up over the nape
        cel(draw, Poly([(cx - 6.4, r.sh_y - 3.8), (cx + 6.4, r.sh_y - 3.8), (cx + 7.4, r.sh_y + 0.8), (cx - 7.4, r.sh_y + 0.8)]),
            GD_COAT, sh=(0.8, 0.6))
    # a battered wide hat with a bent brim
    cx0 = hx - d * 0.8
    brim = Poly(arc_pts(cx0 + d * 1.0, hy - 6.2, 15.0 if not d else 13.8, 3.8, 0, 360)[:-1])
    cel(draw, brim, GD_HAT, sh=(0.0, 1.2))
    crown = Poly([(cx0 - 7.2, hy - 6.2), (cx0 - 6.6, hy - 13.0), (cx0 - 2.0, hy - 14.4), (cx0 + 1.0, hy - 13.0),
                  (cx0 + 5.4, hy - 14.0), (cx0 + 7.0, hy - 12.6), (cx0 + 7.2, hy - 6.2)])
    cel(draw, crown, GD_HAT, sh=(1.4, 0.6))
    cel(draw, Poly([(cx0 - 7.2, hy - 6.2), (cx0 + 7.2, hy - 6.2), (cx0 + 7.1, hy - 8.0), (cx0 - 7.1, hy - 8.0)]),
        GD_PATCH, sh=None)
    if r.back:
        _gd_shovel(draw, grip, socket)
        steady(rig_hand, r, draw, sh_side, GD_GLOVE, **hold)


# ===================================================================
# DULLAHAN (36) -- a headless knight carrying its head
# ===================================================================

DUL_ARMOR = (72, 70, 94)
DUL_PLATE = (98, 94, 130)      # the lit planes: pauldrons, gauntlets, greaves, the collar's rim
DUL_HELM = (114, 112, 152)     # the head it carries: lighter than anything it is carried against
DUL_CAPE = (104, 40, 120)
DUL_PLUME = (164, 70, 184)
DUL_GLOW = (150, 255, 120)
DUL_FIRE = ((50, 150, 62), (150, 255, 120), (232, 255, 212))
DUL_SABRE = (192, 234, 200)

from sprite_base import shoe  # noqa: E402  (see the note under the Revenant's constants)


def _dul_legs(r, draw):
    """Dark thighs over tall plate greaves: a rider's legs, and a stride that can be seen
    (dark legs on a dark body were one lump in the engine's grade)."""
    for side in ((-r.d, r.d) if r.d else (-1, 1)):
        (hx0, hy0), (fx, fy) = r.hip(side), r.foot(side)
        near = r.near(side)
        w0 = 2.6 * 1.08 * min(r.build, 1.25)
        cel(draw, Limb([(hx0, hy0), (fx, fy - 1.2)], [w0, w0 * 0.86]), DUL_ARMOR if near else shade(DUL_ARMOR, 0.6),
            sh=(0.9, 0.0))
        kx, ky = hx0 + (fx - hx0) * 0.44, hy0 + (fy - hy0) * 0.44
        cel(draw, Limb([(kx, ky), (fx, fy - 1.2)], [w0, w0 * 0.88]), DUL_PLATE if near else shade(DUL_PLATE, 0.6),
            sh=(0.9, 0.0))
        shoe(r, draw, fx, fy, shade(DUL_PLATE, 1.2), near)


def _dul_helm(draw, x, y, face, back, fall, wave):
    """The head: a barbute with a T of a visor, two green eyes in it, and a plume that falls
    to `fall` (-1/+1). `face` is where it looks: 0 at the camera, -1/+1 along the walk.

    It was a ball in the armour's own colour with a tuft standing straight up off it: a
    cartoon bomb and its fuse. A helm has a rim, a visor and a plume that hangs."""
    shape = Poly(arc_pts(x, y - 0.4, 7.6, 7.2, 180, 360, 22) +
                 [(x + 8.0, y + 4.4), (x + 7.0, y + 6.6), (x - 7.0, y + 6.6), (x - 8.0, y + 4.4)])
    regions = []
    if back:
        regions = [(RRect(x - 0.9, y - 9.0, x + 0.9, y + 3.2, 0.6), lit(DUL_HELM, 0.8)),
                   (Poly([(x - 9.0, y + 3.4), (x + 9.0, y + 3.4), (x + 9.0, y + 8.0), (x - 9.0, y + 8.0)]),
                    shade(DUL_HELM, 0.7))]
    cel(draw, shape, DUL_HELM, sh=(1.4, 1.2), hi=(0.7, 0.7), regions=regions)
    if not back:
        def visor(l):
            if face:
                RRect(min(x + face * 0.6, x + face * 9.0), y - 1.9, max(x + face * 0.6, x + face * 9.0), y + 1.7, 1.0).draw(l, fill=VOID)
                Poly([(x + face * 4.2, y + 1.4), (x + face * 6.4, y + 1.4), (x + face * 6.1, y + 6.0),
                      (x + face * 4.5, y + 6.0)]).draw(l, fill=VOID)
            else:
                RRect(x - 5.5, y - 1.9, x + 5.5, y + 1.7, 1.0).draw(l, fill=VOID)
                Poly([(x - 1.2, y + 1.4), (x + 1.2, y + 1.4), (x + 0.9, y + 6.0), (x - 0.9, y + 6.0)]).draw(l, fill=VOID)
        clipped(draw, shape, visor)
        eyes = ((x + face * 3.4, 1.0), (x + face * 6.9, 0.5)) if face else ((x - 2.8, 1.0), (x + 2.8, 1.0))
        for (ex, w) in eyes:
            Ell(ex, y - 0.1, 1.8 * w, 1.25).draw(draw, fill=DUL_GLOW)
            Ell(ex - 0.4 * w, y - 0.4, 0.7 * w, 0.5).draw(draw, fill=(240, 255, 226))
    cel(draw, Limb([(x - fall * 0.6, y - 7.0), (x + fall * 2.6, y - 10.0 - abs(wave) * 0.4),
                    (x + fall * 7.0, y - 7.6 + wave * 0.5), (x + fall * 9.0, y - 2.4 + wave)], [1.5, 2.3, 2.0, 0.5]),
        DUL_PLUME, sh=(0.6, 0.6))


def draw_dullahan(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=1.12)
    d = r.d
    cx = r.cx
    # head-on its head rides in the crook of one arm and the sabre hangs from the other hand;
    # in profile the sabre is in the near hand and the head is held out ahead on the far arm
    head_side = (-1 if not r.back else 1) if not d else -d
    sab_side = -head_side
    wave = [0.0, 1.0, 0.0, -1.0][frame]
    stride = frame % 2

    def steady(fn, *args, **kw):
        ph = r.ph
        r.ph = ph * 0.25
        try:
            return fn(*args, **kw)
        finally:
            r.ph = ph

    if d:
        hc = (cx + d * 15.4, r.hip_y - 4.4)
    else:
        hc = (cx + head_side * 13.0, r.hip_y - 5.4)
    carry = dict(out=3.4)

    _torn_cape(r, draw, DUL_CAPE, layer="under")
    if d:
        # the far arm, out ahead and under the head it carries
        aw = 2.2 * min(r.build, 1.3)
        s = r.shoulder(-d)
        cel(draw, Limb([s, (cx + d * 4.6, r.hip_y - 3.6), (hc[0] - d * 5.2, hc[1] + 5.6)], [aw, aw * 0.9, aw * 0.82]),
            shade(DUL_ARMOR, 0.6), sh=(0.8, 0.4))
    _dul_legs(r, draw)
    rig_torso(r, draw, DUL_ARMOR)
    if not r.back:
        cel(draw, Poly([(cx - 2.8 + d * 2, r.sh_y + 0.6), (cx + 2.8 + d * 2, r.sh_y + 0.6), (cx + d * 2, r.waist_y + 0.4)]),
            DUL_PLUME, sh=None)
    rig_belt(r, draw, shade(DUL_ARMOR, 1.4), buckle=DUL_GLOW)
    if d:
        steady(rig_arms, r, draw, DUL_ARMOR, DUL_PLATE, layer="near", hands=False)
    else:
        rig_arms(r, draw, DUL_ARMOR, DUL_PLATE, layer="near", hands=False, sides=(sab_side,))
        rig_arms(r, draw, DUL_ARMOR, DUL_PLATE, layer="near", hands=False, sides=(head_side,), **carry)
    _torn_cape(r, draw, DUL_CAPE, layer="over")
    for side in _sides(r):
        sx, sy = r.shoulder(side)
        cel(draw, Poly([(sx - 4.8, sy + 1.4), (sx - 3.2, sy - 2.8), (sx + (side * 1.4 if not d else 0), sy - 4.6),
                        (sx + 3.2, sy - 2.8), (sx + 4.8, sy + 1.4)]), DUL_PLATE, sh=(0.8, 0.8), hi=(0.5, 0.5))
    # the collar with nothing in it but green fire, as big as the head that is not there
    gx, gy = cx + d * 0.6, r.sh_y - 2.2
    gw = 6.2 if not d else 4.8
    cel(draw, Ell(gx, gy, gw, 2.4), shade(DUL_ARMOR, 0.6), sh=None)
    flame(draw, gx - d * 1.0, gy + 0.8, 19.0, 23.6 + stride * 1.8, DUL_FIRE, sway=wave * 1.6 - d * 3.0,
          flip=-1.0 if stride else 1.0)
    cel(draw, Poly(arc_pts(gx, gy, gw, 2.4, 0, 180, 12) + arc_pts(gx, gy - 1.5, gw, 2.0, 180, 0, 12)), DUL_PLATE, sh=None)
    # the sabre, a rider's, with the fire's green in its steel: point down and out head-on, and
    # trailing behind in profile, clear of the head
    h = steady(hand_at, r, sab_side)
    ang = 90 - sab_side * 30 if not d else 90 + d * 40
    blade(draw, h[0], h[1], ang, length=11.5, width=1.7, color=DUL_SABRE, hilt=DUL_HELM, grip=(54, 46, 66), guard=2.6,
          curve=1.2, flip=-sab_side if not d else d)
    steady(rig_hand, r, draw, sab_side, DUL_PLATE)
    # the head
    _dul_helm(draw, hc[0], hc[1], d, r.back, head_side if not d else -d, wave)
    # the hand under it, at the corner nearest the body
    if d:
        hd = (hc[0] - d * 5.0, hc[1] + 6.2)
    else:
        hd = (hc[0] - head_side * 4.2, hc[1] + 6.2)
    cel(draw, Ell(hd[0], hd[1], 2.4, 2.1), DUL_PLATE if (not d) else shade(DUL_PLATE, 0.5), sh=(0.6, 0.6))


# ===================================================================
# PHANTOM (37) -- a porcelain mask, a swirling cloak, a thin rapier
# ===================================================================

PH_CLOAK = (58, 72, 130)
PH_LINING = (104, 128, 206)    # the cloak's inside: the collar from the front, the hem's opening
PH_GHOST = (178, 204, 242)     # a ghost's blue, deep enough for the white mask to stand off it
PH_MASK = (250, 250, 252)
PH_GLOW = (120, 200, 255)
PH_HAIR = (44, 52, 90)
# the kit's "swept" fringe, turned to fall over the bare side of the face: the long locks lay on
# the mask and hid it
PH_STYLE = dict(HAIR_STYLES["swept"], fringe=((0.8, 2.2), (0.4, 3.0), (-0.02, 4.4), (-0.42, 5.2), (-0.84, 4.4)),
                sweep=-2.0)


def _ph_rapier(draw, x, y, ang, length=15.0):
    """A thin rapier gripped at (x, y): a cup guard just past the fist, a needle of a blade."""
    cel(draw, Limb(xform([(-2.8, 0.0), (0.4, 0.0)], x, y, ang), [0.8, 0.8]), (70, 76, 110), sh=None, lw=0.6)
    cel(draw, Poly(xform([(2.6, -0.95), (length, -0.35), (length + 1.4, 0.0), (length, 0.35), (2.6, 0.95)], x, y, ang)),
        (232, 238, 250), sh=None, lw=0.5)
    cel(draw, Poly(xform(arc_pts(1.2, 0.0, 2.4, 2.8, -90, 90, 8), x, y, ang)), (222, 226, 240), sh=(0.5, 0.5), lw=0.7)


def draw_phantom(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, bob=[0, -1, -2, -1][frame], step=0)
    d = r.d
    wave = [0.0, 1.0, 0.0, -1.0][frame]
    rp_side = _hold_side(r)
    cx = r.cx
    w = r.sh_w
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    top = r.sh_y - 2.8
    hem = r.hip_y + 3.6
    # the collar's wings behind the head, their lining toward the camera (from the front)
    if not d and not r.back:
        for s2 in (-1, 1):
            cel(draw, Poly([(hx + s2 * 5.0, r.sh_y - 0.4), (hx + s2 * 12.6, hy - 2.6), (hx + s2 * 14.6, hy - 6.4),
                            (hx + s2 * 14.6, hy + 3.0), (hx + s2 * 10.4, r.sh_y + 0.6)]), PH_LINING, sh=(0.8, 0.6))
    # no legs: a ghost's tail, curling away under the cloak (it was the round end of a flame
    # shape, and read as a ball he stood on)
    if d:
        tail = [(cx + d * 0.4, r.hip_y - 1.0), (cx - d * 0.4, r.base_y - 7.0), (cx - d * 3.0, r.base_y - 3.4),
                (cx - d * (6.6 + wave), r.base_y - 1.6), (cx - d * (9.8 + wave * 1.4), r.base_y - 3.4)]
        widths = [5.8, 4.9, 3.1, 1.5, 0.3]
    else:
        tail = [(cx, r.hip_y - 1.0), (cx + 0.6 + wave * 0.3, r.base_y - 7.0), (cx - 0.8 + wave * 0.8, r.base_y - 3.4),
                (cx - 3.9 + wave * 1.4, r.base_y - 1.6), (cx - 6.8 + wave * 1.8, r.base_y - 3.4)]
        widths = [7.0, 5.7, 3.3, 1.5, 0.3]
    if not d:
        # the inside of the cloak, seen through the parting of its front
        cel(draw, Poly([(cx - w - 4.6 + wave, hem - 0.8), (cx - 3.6, r.hip_y - 3.4), (cx + 3.6, r.hip_y - 3.4),
                        (cx + w + 4.6 + wave, hem - 0.8)]), PH_LINING if not r.back else PH_CLOAK, sh=None)
    cel(draw, Limb(tail, widths), PH_GHOST, sh=(0.9, 0.6))
    # the cloak
    if d:
        cloak = Poly([(cx - d * 3.0, r.sh_y - 2.6), (cx + d * 3.2, r.sh_y - 2.2), (cx + d * 5.2, hem - 1.6),
                      (cx + d * 1.0, hem + 0.4), (cx - d * 4.0, hem - 0.8), (cx - d * (10.6 + wave * 1.4), hem + 1.6),
                      (cx - d * 6.2, r.sh_y + 1.0)])
        band = Poly([(cx - 20, hem - 2.4), (cx + 20, hem - 2.4), (cx + 20, hem + 3.0), (cx - 20, hem + 3.0)])
    elif r.back:
        cloak = Poly([(cx - w + 1.0, top), (cx + w - 1.0, top), (cx + w + 7.2 + wave, hem + 1.4),
                      (cx + 4.2 + wave * 0.6, hem - 0.6), (cx + wave * 0.4, hem + 1.6), (cx - 4.2 + wave * 0.6, hem - 0.6),
                      (cx - w - 7.2 + wave, hem + 1.4)])
        band = Poly([(cx - 0.9, r.sh_y), (cx + 0.9, r.sh_y), (cx + 1.6 + wave * 0.4, hem + 2.0), (cx - 1.6 + wave * 0.4, hem + 2.0)])
    else:
        cloak = Poly([(cx - w + 1.0, top), (cx + w - 1.0, top), (cx + w + 7.2 + wave, hem + 1.4),
                      (cx + 5.6 + wave * 0.6, hem - 0.2), (cx + 1.0, r.hip_y - 2.6), (cx - 1.0, r.hip_y - 2.6),
                      (cx - 5.6 + wave * 0.6, hem - 0.2), (cx - w - 7.2 + wave, hem + 1.4)])
        band = None
    cel(draw, cloak, PH_CLOAK, sh=(1.6, 1.0),
        regions=[(band, lit(PH_CLOAK, 0.5) if d else shade(PH_CLOAK, 0.8))] if band is not None else ())
    if not d and not r.back:
        # a white shirt front under the chin
        cel(draw, Poly([(cx - 2.8, r.sh_y - 1.8), (cx + 2.8, r.sh_y - 1.8), (cx, r.waist_y - 0.4)]), PH_MASK, sh=(0.5, 0.0),
            lw=0.7)
    rig_arms(r, draw, PH_CLOAK, PH_GHOST, layer="near", hands=False, reach=0.3)
    head_skull(r, draw, PH_GHOST, ears=False)
    if not r.back:
        e1, e2, ey, mx, my = face_anchor(r)
        # the half mask is on his left: the camera's right head-on, the whole near side walking
        # left, a sliver along the far side walking right. Drawn past the skull and clipped to it.
        if not d:
            m = [(-0.3, -9.5), (rx + 1.0, -9.5), (rx + 1.0, 6.0), (6.4, 9.6), (2.4, 8.2), (0.4, 4.8)]
            hole, bare = (4.7, 2.3, 1.0), (e1, 1.0, -1)
        elif direction == LEFT:
            m = [(-3.0, -9.5), (rx + 1.0, -9.5), (rx + 1.0, 3.6), (8.4, 5.2), (3.0, 6.4), (-1.4, 4.6), (-3.0, -1.0)]
            hole, bare = (5.2, 2.0, 1.0), None
        else:
            m = [(5.6, -9.5), (rx + 1.0, -9.5), (rx + 1.0, 3.6), (8.6, 5.4), (6.2, 3.4), (5.2, -1.0)]
            hole, bare = (8.4, 1.3, 0.8), (e1, 1.0, -d)
        k = d or 1
        mask = Poly([(hx + k * u, hy + v) for (u, v) in m])
        clipped(draw, Ell(hx, hy, rx - 0.5, ry - 0.5),
                lambda l: cel(l, mask, PH_MASK, sh=(0.9, 0.9), tone=(204, 212, 234), line_color=(64, 78, 126), lw=0.9))
        # the mask's eye: a dark almond with a point of light in it, where the other is alive
        Ell(hx + k * hole[0], ey + 0.3, hole[1], 1.7).draw(draw, fill=VOID)
        Ell(hx + k * (hole[0] + 0.2), ey + 0.4, 1.0 * hole[2], 1.0).draw(draw, fill=PH_GLOW)
        Ell(hx + k * (hole[0] + 0.2) - 0.3, ey + 0.1, 0.4, 0.4).draw(draw, fill=(255, 255, 255))
        if bare is not None:
            ms_eye(draw, bare[0], ey, 3.9 * bare[1], 5.2, (70, 110, 170), (float(d), 0.0), "sharp", skin=PH_GHOST,
                   side=bare[2])
        ms_mouth(draw, mx - (1.8 if not d else 0), my, "smirk", PH_GHOST, 0.9)
    rig_hair(r, draw, PH_HAIR, PH_STYLE, skin=PH_GHOST)
    # the high collar from behind and from the side: it stands up over the nape
    if r.back:
        cel(draw, Poly([(hx - 14.6, hy - 6.2), (hx - 11.6, hy + 0.4), (hx - 6.4, hy + 5.4), (hx, hy + 6.8), (hx + 6.4, hy + 5.4),
                        (hx + 11.6, hy + 0.4), (hx + 14.6, hy - 6.2), (hx + 14.4, hy + 3.4), (hx + 9.0, r.sh_y + 1.6),
                        (hx - 9.0, r.sh_y + 1.6), (hx - 14.4, hy + 3.4)]), PH_CLOAK, sh=(1.2, 1.0))
    elif d:
        cel(draw, Poly([(hx + d * 0.6, r.sh_y + 1.2), (hx - d * 2.6, hy + 6.2), (hx - d * 9.0, hy + 1.6), (hx - d * 14.4, hy - 5.6),
                        (hx - d * 13.6, hy + 4.2), (hx - d * 7.0, r.sh_y + 1.4)]), PH_CLOAK, sh=(1.0, 0.8))
    # the rapier: point down and out head-on, levelled ahead in profile
    h = hand_at(r, rp_side, 0.3)
    _ph_rapier(draw, h[0], h[1], 90 - rp_side * 56 if not d else 90 - d * 60)
    for side in _sides(r):
        rig_hand(r, draw, side, PH_GHOST, reach=0.3)


# ===================================================================
# MUMMY (38) -- all bandages, one eye peeking out, a gold scarab
# ===================================================================

MUM_WRAP = (240, 232, 208)
MUM_SH = (192, 174, 142)
MUM_DARK = (66, 48, 62)        # what the wraps cover, where they gap
MUM_GLOW = (110, 210, 255)
MUM_TEAL = (56, 168, 178)


def _mum_part(draw, shape, color=MUM_WRAP, sh=(1.2, 1.2), step=2.7, slant=0.8, phase=0.0, regions=(), extra=None):
    """One wrapped part: the flat fill and its shadow, the bandage's turns across it, then its
    line. The turns are clipped to the part (they were loose strokes, and stuck out past the
    skull, the shins and the ribs as stray ticks) and wide enough to be seen at game size."""
    cel(draw, shape, color, sh=sh, regions=regions, line=False)
    xs, ys = [p[0] for p in shape.pts], [p[1] for p in shape.pts]
    x0, x1, y0, y1 = min(xs) - 1.0, max(xs) + 1.0, min(ys), max(ys)
    band = shade(color, 1.0) if color != MUM_WRAP else MUM_SH

    def turns(l):
        y, i = y0 + step * (0.55 + phase), 0
        while y < y1 + slant:
            k = slant * (1 if i % 2 else -1)
            stroke(l, [(x0, y + k), (x1, y - k)], 0.9, band)
            y += step
            i += 1
        if extra is not None:
            extra(l)

    clipped(draw, shape, turns)
    shape.draw(draw, fill=None, outline=ink(color), width=1.0)


def _mum_end(draw, pts, color=MUM_WRAP):
    """A loose end of bandage."""
    cel(draw, Limb(pts, [1.05, 1.05, 0.75][:len(pts)]), color, sh=(0.4, 0.5), tone=MUM_SH, lw=0.7)


def draw_mummy(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=1.02)
    d = r.d
    cx = r.cx
    wave = [0.0, 1.0, 0.0, -1.0][frame]
    far = shade(MUM_WRAP, 0.6)
    aw = 2.2 * min(r.build, 1.3)
    # arms held out ahead, the way a mummy walks: level in profile, and stiff -- they do not swing
    def arm(side):
        ph = r.ph
        r.ph = ph * 0.2
        s, e, w, h = arm_pts(r, side, (2.0 if r.near(side) else 1.6) if d else 0.9)
        r.ph = ph
        col = MUM_WRAP if r.near(side) else far
        _mum_part(draw, Limb([s, e, w], [aw, aw * 0.9, aw * 0.82]), col, sh=(0.8, 0.4), step=2.3, slant=0.6)
        cel(draw, Ell(h[0], h[1], 2.3, 2.2), col, sh=(0.6, 0.6))
        return w

    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    if d:
        arm(-d)
    for side in ((-d, d) if d else (-1, 1)):
        (hx0, hy0), (fx, fy) = r.hip(side), r.foot(side)
        col = MUM_WRAP if r.near(side) else far
        w0 = 2.6 * min(r.build, 1.25)
        _mum_part(draw, Limb([(hx0, hy0), (fx, fy - 1.2)], [w0, w0 * 0.86]), col, sh=(0.9, 0.0), step=2.4, slant=0.6,
                  phase=0.3 if side > 0 else 0.0)
        cel(draw, Ell(fx + d * 0.8, fy, 2.5, 1.6), col, sh=(0.0, 0.6), tone=MUM_SH if r.near(side) else None)
    torso = torso_shape(r)
    _mum_part(draw, torso, sh=(1.5, 0.9), step=2.6, slant=0.9,
              regions=[(Ell(r.hx, torso.pts[0][1] + 0.4, rx * 0.62, 2.3), MUM_SH)])
    # a broad collar of gold with a scarab at the throat: the one colour on it
    cy0 = r.sh_y - 3.8
    if d:
        cel(draw, Poly([(cx - d * 4.7, r.sh_y - 2.0), (cx + d * 4.5, r.sh_y - 0.8), (cx + d * 4.9, r.sh_y + 3.0),
                        (cx + d * 1.0, r.sh_y + 3.8), (cx - d * 4.9, r.sh_y + 1.0)]), GOLD, sh=(0.6, 0.6))
        cel(draw, Ell(cx + d * 3.3, r.sh_y + 1.5, 1.5, 1.3), MUM_TEAL, sh=None, lw=0.6)
    else:
        deep = 8.4 if not r.back else 6.8
        cel(draw, Poly(arc_pts(cx, cy0, 9.4, deep, 12, 168, 16) + arc_pts(cx, cy0, 6.0, 4.2, 168, 12, 12)), GOLD,
            sh=(0.6, 0.6))
        if not r.back:
            cel(draw, Ell(cx, cy0 + deep - 2.1, 2.2, 1.7), MUM_TEAL, sh=(0.4, 0.4), lw=0.6)
            Ell(cx - 0.6, cy0 + deep - 2.6, 0.5, 0.4).draw(draw, fill=(214, 246, 246))
    wrist = None
    for side in _sides(r):
        w = arm(side)
        if side == _hold_side(r):
            wrist = w
    # the end trailing off that arm
    if d:
        _mum_end(draw, [(wrist[0] - d * 0.6, wrist[1] + 1.2), (wrist[0] - d * 1.6, wrist[1] + 4.0 + wave * 0.5),
                        (wrist[0] - d * (2.8 + wave), wrist[1] + 6.4)])
    else:
        s = _hold_side(r)
        _mum_end(draw, [(wrist[0] + s * 0.8, wrist[1] + 0.8), (wrist[0] + s * 2.4, wrist[1] + 4.4 + wave * 0.6),
                        (wrist[0] + s * (1.6 - wave), wrist[1] + 8.0)])
    # the head: turns of bandage, and across the eyes a gap one eye looks out of
    e1, e2, ey, _, _ = face_anchor(r)

    def face(l):
        if r.back:
            return
        if d:
            Poly([(hx - d * 3.0, ey - 2.4), (hx + d * (rx + 1.0), ey - 2.9), (hx + d * (rx + 1.0), ey + 2.5),
                  (hx - d * 2.0, ey + 2.1)]).draw(l, fill=MUM_DARK)
        else:
            Poly([(hx - rx - 1.0, ey - 2.9), (hx + rx + 1.0, ey - 2.1), (hx + rx + 1.0, ey + 2.6),
                  (hx - rx - 1.0, ey + 2.0)]).draw(l, fill=MUM_DARK)
            # one turn has slipped across the other eye
            strip = Poly([(hx + rx + 1.0, ey - 8.4), (hx + rx + 1.0, ey - 2.6), (hx + 2.2, ey + 5.6), (hx - 1.8, ey + 3.4)])
            strip.draw(l, fill=MUM_WRAP)
            stroke(l, [(hx + rx + 1.0, ey - 2.6), (hx + 2.2, ey + 5.6)], 0.8, MUM_SH)
            stroke(l, [(hx + rx + 1.0, ey - 8.4), (hx - 1.8, ey + 3.4)], 0.8, MUM_SH)

    head = Ell(hx, hy, rx, ry)
    cel(draw, head, MUM_WRAP, sh=(0.9, 0.8), line=False)

    def turns(l):
        for (v, k) in ((-8.0, -1.1), (-5.2, 1.2), (6.2, 1.0), (8.8, -0.8)) + (((-2.2, -1.0), (0.8, 1.1), (3.6, -0.9)) if r.back else ()):
            stroke(l, [(hx - rx - 1.0, hy + v + k), (hx + rx + 1.0, hy + v - k)], 0.9, MUM_SH)
        face(l)

    clipped(draw, head, turns)
    head.draw(draw, fill=None, outline=ink(MUM_WRAP), width=1.0)
    if not r.back:
        ex = e1 if not d else hx + d * 5.0
        Ell(ex, ey - 0.1, 2.1, 2.2).draw(draw, fill=MUM_GLOW)
        Ell(ex, ey - 0.3, 1.2, 1.3).draw(draw, fill=lit(MUM_GLOW, 1.4))
        Ell(ex - 0.7, ey - 0.9, 0.7, 0.7).draw(draw, fill=(255, 255, 255))
    # the knot at the back of the head, and its two ends
    if r.back:
        kx, ky = hx + 2.0, hy + 2.0
        _mum_end(draw, [(kx, ky), (kx + 3.4 + wave * 0.8, ky + 5.6), (kx + 2.6 - wave * 0.6, ky + 11.0)])
        _mum_end(draw, [(kx, ky), (kx - 1.6 + wave * 0.6, ky + 5.0), (kx - 0.4 + wave, ky + 8.6)])
        cel(draw, Ell(kx, ky, 1.9, 1.7), MUM_WRAP, sh=(0.5, 0.5), tone=MUM_SH)
    elif d:
        # in profile the knot's ends hang down the back of the neck (standing out from the head
        # they read as an ear)
        tx = hx - d * 11.2
        _mum_end(draw, [(tx, hy + 2.6), (tx - d * (1.8 + wave * 0.5), hy + 7.6), (tx - d * (1.0 + wave), hy + 12.4)])
        _mum_end(draw, [(tx, hy + 2.6), (tx - d * 0.2, hy + 6.8), (tx + d * (0.8 - wave * 0.5), hy + 9.6)])
        cel(draw, Ell(tx, hy + 2.6, 1.7, 1.6), MUM_WRAP, sh=(0.5, 0.5), tone=MUM_SH)


# ===================================================================
# DEATH KNIGHT (39) -- a skull visor, spiked black plate, a rune blade
# ===================================================================

DK_ARMOR = (60, 56, 78)
DK_PLATE = (90, 84, 122)       # the plate's lit planes: pauldrons, gauntlets, knees
DK_IRON = (106, 100, 142)      # the crown's spikes
DK_TRIM = (150, 36, 50)        # the cape
DK_RED = (206, 48, 62)         # the crown band, the belt, the hilt
DK_GLOW = (255, 64, 72)
DK_RUNE = (206, 100, 255)
DK_MASK = (218, 220, 236)      # the skull visor: pale cold steel, not the skeletons' bone
DK_SPIKE = (204, 200, 220)
DK_BLADE = (126, 136, 188)


def _dk_helm(r, draw):
    """A black dome under a crown of iron spikes, banded in red, and for a visor a skull of
    pale steel: slanted sockets with a red coal in each, a grille of teeth.

    Everything dark on the old helm (sockets, nose and teeth drawn dark on a dark ball) came
    out of the engine's grade as one lump with two red dots; the visor is the one pale plane
    on the character, so the face carries the contrast."""
    d = r.d
    k = d or 1
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    cxh = hx - d * 0.4
    top = hy - ry
    spikes = ((-6.6, 6.4), (-2.8, 9.2), (1.4, 10.4), (5.6, 7.6)) if d else \
        ((-7.6, 6.6), (-3.9, 9.6), (0.0, 12.0), (3.9, 9.6), (7.6, 6.6))
    for (u, ln) in spikes:
        bx = cxh + k * u
        cel(draw, Poly([(bx - 2.2, top + 3.8), (bx + k * u * 0.26, top + 2.6 - ln), (bx + 2.2, top + 3.8)]),
            DK_IRON, sh=(1.0, 0.0), lw=0.7)
    dome = Ell(cxh, hy + 0.2, rx + 1.0, ry + 1.2)
    band = Poly(arc_pts(cxh, hy - 12.2, 17.0, 6.4, 18, 162, 16) + arc_pts(cxh, hy - 9.6, 17.0, 6.4, 162, 18, 16))
    regions = [(band, DK_RED),
               (Poly(arc_pts(cxh, hy - 12.2, 17.0, 6.4, 18, 62, 8) + arc_pts(cxh, hy - 9.6, 17.0, 6.4, 62, 18, 8)),
                shade(DK_RED, 1.2))]
    if r.back:
        # from behind: a ridge over the crown and a neck guard, in the lighter plate
        regions = [(RRect(cxh - 1.0, top - 2.0, cxh + 1.0, hy + 6.0, 0.8), DK_PLATE),
                   (Poly([(cxh - 14.0, hy + 6.6), (cxh + 14.0, hy + 6.6), (cxh + 14.0, hy + 14.0), (cxh - 14.0, hy + 14.0)]),
                    DK_PLATE)] + regions
    cel(draw, dome, DK_ARMOR, sh=(1.6, 1.2), hi=(0.8, 0.8), regions=regions)
    if r.back:
        clipped(draw, dome, lambda l: stroke(l, [(cxh - 14.0, hy + 6.6), (cxh + 14.0, hy + 6.6)], 0.7, ink(DK_ARMOR)))
        return
    if d:
        m = [(1.4, -4.4), (11.4, -4.9), (12.7, 0.4), (11.8, 4.6), (9.9, 5.6), (9.4, 9.2), (7.6, 10.4), (3.2, 9.8),
             (2.6, 5.4), (0.8, 1.2)]
        mask = Poly([(hx + d * u, hy + v) for (u, v) in m])
        sockets = (([(2.4, -1.5), (8.2, 0.6), (8.0, 3.0), (5.2, 4.0), (2.6, 2.6)], (5.4, 1.7), 1.0),
                   ([(10.0, 0.9), (12.0, 0.3), (11.9, 3.0), (10.2, 3.4)], (11.0, 1.9), 0.5))
        nose = [(10.2, 5.0), (11.8, 4.8), (11.2, 3.5)]
        teeth = (4.4, 6.3, 8.2)
    else:
        half = [(0.0, -3.3), (4.4, -3.6)] + bez((8.4, -4.4), (10.8, 0.4), (8.4, 4.4), 5) + \
            [(6.2, 5.4), (5.8, 9.0), (4.4, 10.4), (0.0, 10.7)]
        mask = Poly([(hx + u, hy + v) for (u, v) in half] + [(hx - u, hy + v) for (u, v) in half[-2:0:-1]])
        sock = [(1.5, 0.9), (7.4, -1.7), (7.6, 2.0), (5.2, 3.8), (2.2, 3.2)]
        sockets = tuple(([(s * u, v) for (u, v) in sock], (s * 4.5, 1.5), 1.0) for s in (-1, 1))
        nose = [(-1.0, 5.6), (1.0, 5.6), (0.0, 3.9)]
        teeth = (-3.3, -1.1, 1.1, 3.3)
    cel(draw, mask, DK_MASK, sh=(1.0, 0.9))
    for (pts, (gu, gv), w) in sockets:
        Poly([(hx + k * u, hy + v) for (u, v) in pts]).draw(draw, fill=VOID)
        Ell(hx + k * gu, hy + gv, 1.7 * w, 1.25).draw(draw, fill=DK_GLOW)
        Ell(hx + k * gu - 0.4 * w, hy + gv - 0.3, 0.7 * w, 0.5).draw(draw, fill=(255, 214, 200))
    Poly([(hx + k * u, hy + v) for (u, v) in nose]).draw(draw, fill=VOID)
    for u in teeth:
        stroke(draw, [(hx + k * u, hy + 7.3), (hx + k * u, hy + 9.7)], 0.75, VOID)


def _dk_runeblade(draw, x, y, ang, flip, length):
    """The rune blade: pale and cold, as tall as its bearer, a lit fuller knotted with runes."""
    blade(draw, x, y, ang, length=length, width=2.6, color=DK_BLADE, hilt=DK_RED, grip=(44, 38, 56), guard=4.6,
          flip=flip)
    stroke(draw, xform([(3.0, 0.0), (length - 2.0, 0.0)], x, y, ang), 0.9, DK_RUNE)
    for t in (0.24, 0.5, 0.76):
        u = length * t
        Poly(xform([(u - 1.9, 0.0), (u, -1.3), (u + 1.9, 0.0), (u, 1.3)], x, y, ang)).draw(draw, fill=DK_RUNE)
        px, py = xform([(u, 0.0)], x, y, ang)[0]
        Ell(px, py, 0.55, 0.55).draw(draw, fill=(250, 226, 255))


def draw_deathknight(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=1.2)
    d = r.d
    cx = r.cx
    sw_side = _hold_side(r)
    # the sword arm: the blade is carried upright, clear of the body (head-on) or levelled ahead of
    # it under the visor (profile), and held still: only a quarter of a free arm's swing
    grip = dict(out=2.5) if not d else dict(reach=0.5)

    def steady(fn, *args, **kw):
        ph = r.ph
        r.ph = ph * 0.25
        try:
            return fn(*args, **kw)
        finally:
            r.ph = ph

    _torn_cape(r, draw, DK_TRIM, layer="under")
    if d:
        rig_arms(r, draw, DK_ARMOR, DK_PLATE, layer="far")
    for side in ((-1, 1) if not d else (-d, d)):
        rig_legs(r, draw, DK_ARMOR, shade(DK_ARMOR, 0.8), sides=(side,), width=1.1)
        if not r.back:
            (hx0, hy0), (fx, fy) = r.hip(side), r.foot(side)
            cel(draw, Ell((hx0 + fx) / 2.0 + d * 0.6, (hy0 + fy) / 2.0 + 0.4, 2.8, 1.9),
                DK_PLATE if r.near(side) else shade(DK_PLATE, 0.6), sh=(0.5, 0.6))
    rig_torso(r, draw, DK_ARMOR)
    if not r.back:
        skull(draw, cx + d * 2.0, r.sh_y + 2.8, 3.0, 2.8, d, glow=None, bone=DK_MASK, teeth=False)
    rig_belt(r, draw, DK_RED, buckle=DK_MASK)
    for side in _sides(r):
        if side == sw_side:
            steady(rig_arms, r, draw, DK_ARMOR, DK_PLATE, layer="near", hands=False, sides=(side,), **grip)
        else:
            rig_arms(r, draw, DK_ARMOR, DK_PLATE, layer="near", hands=False, sides=(side,))
    _torn_cape(r, draw, DK_TRIM, layer="over")
    # pauldrons, a spike standing off each: under the helm's rim head-on, over it in profile,
    # where the near shoulder is this side of the head
    def pauldrons():
        for side in _sides(r):
            sx, sy = r.shoulder(side)
            px = sx + (side * 2.0 if not d else 0.0)
            out = side if not d else -d
            cel(draw, Poly([(px + out * 2.0, sy - 3.6), (px + out * 4.8, sy - 2.4), (px + out * 7.0, sy - 8.0)]), DK_SPIKE,
                sh=(0.6, 0.0), lw=0.8)
            cel(draw, Ell(px, sy - 1.0, 5.2, 3.5), DK_PLATE, sh=(0.9, 0.9), hi=(0.6, 0.6))

    if not d:
        pauldrons()
    _dk_helm(r, draw)
    if d:
        pauldrons()
    h = steady(hand_at, r, sw_side, **grip)
    if d:
        _dk_runeblade(draw, h[0], h[1], -90 + d * 52, d, 21.0)
    else:
        _dk_runeblade(draw, h[0], h[1], -90 + sw_side * 20, 1.0, 24.0)
    for side in _sides(r):
        if side == sw_side:
            steady(rig_hand, r, draw, side, DK_PLATE, **grip)
        else:
            rig_hand(r, draw, side, DK_PLATE)


# ===================================================================
# SHADOWFIEND (40) -- a horned, winged little demon with a third eye
# ===================================================================

from sprite_base import clipped, torso_shape  # noqa: E402  (this block's own: the file's import list is shared)

SF_SKIN = (84, 52, 108)
SF_WING = (56, 34, 74)          # the wings' bones
SF_WEB = (140, 48, 118)         # the membrane between them: a magenta off the body's violet, so that the
#                                 wings are wings and not more of the body with a line round it
SF_GLOW = (255, 96, 214)
SF_HORN = (232, 222, 206)
SF_HORN_LINE = (150, 128, 130)  # horn and spine are lined in a mid tone: in the kit's ink a pale horn was mostly line


def _sf_wing(draw, bx, by, s, flap, k=1.0):
    """One bat wing from its root at (bx, by), spread to `s`: the arm up to
    the wrist, three fingers down from it, the membrane scalloped between
    their tips. `flap` lifts the whole hand of it on the strides."""
    def at(u, v):
        return (bx + s * u * k, by + v * k)
    root, foot = at(-0.6, -1.4), at(0.6, 6.2)
    wrist = at(11.0, -8.6 - flap * 3.0)
    f1 = at(17.2 + flap * 0.8, -2.6 - flap * 3.4)
    f2 = at(13.6 + flap * 0.4, 4.8 - flap * 2.4)
    f3 = at(7.8, 7.8 - flap * 1.2)
    arm = bez(root, at(5.0, -7.8 - flap * 2.0), wrist, 4)
    edge = arm + bez(wrist, at(16.4 + flap * 0.6, -7.4 - flap * 3.0), f1, 4)[1:]
    edge += bez(f1, at(12.6, -0.4 - flap * 2.6), f2, 4)[1:]
    edge += bez(f2, at(9.4, 3.2 - flap * 1.6), f3, 4)[1:]
    edge += bez(f3, at(3.8, 4.4 - flap * 0.6), foot, 4)[1:]
    cel(draw, Poly(edge), SF_WEB, sh=(0.8, 0.8))
    for tip in (f1, f2, f3):
        stroke(draw, [wrist, (wrist[0] + (tip[0] - wrist[0]) * 0.9, wrist[1] + (tip[1] - wrist[1]) * 0.9)], 0.9, SF_WING)
    cel(draw, Poly([(wrist[0] - 0.9 * k, wrist[1] + 0.4), (wrist[0] + 0.9 * k, wrist[1] + 0.4), (wrist[0] + s * 0.8 * k, wrist[1] - 2.8 * k)]),
        SF_HORN, sh=None, lw=0.5, line_color=SF_HORN_LINE)
    cel(draw, Limb(arm, [1.6 * k, 1.5 * k, 1.4 * k, 1.3 * k, 1.2 * k], cap0=True), SF_WING, sh=None)


def _sf_tail(draw, root, ctrl, end):
    """The tail from the base of his spine to its spade: thick enough, and the
    spade big enough, to still be there at game size."""
    path = bez(root, ctrl, end, 6)
    ang = math.degrees(math.atan2(path[-1][1] - path[-2][1], path[-1][0] - path[-2][0]))
    spade = Poly(xform([(-0.8, 0.0), (0.2, -3.0), (5.8, 0.0), (0.2, 3.0)], end[0], end[1], ang))
    blob(draw, [Limb(path, [2.0, 1.9, 1.7, 1.5, 1.3, 1.1, 1.0]), spade], SF_SKIN, sh=(0.5, 0.6))


def _sf_horn(draw, x, y, s, far=False, lean=0.0):
    """One horn rooted at (x, y): a thick crescent, out toward `s`, up, and in
    again at its point; `lean` tips it over toward `s` (swept back, in
    profile). (It was a stroke with a kink in it that dipped at the tip: an
    antenna.)"""
    col = SF_HORN if not far else shade(SF_HORN, 1.3)
    path = bez((x - s * 0.6, y + 1.2), (x + s * 6.2, y - 0.2), (x + s * 5.8, y - 5.4), 5) + \
        bez((x + s * 5.8, y - 5.4), (x + s * 5.4, y - 8.8), (x + s * 2.6, y - 10.2), 4)[1:]
    if lean:
        c, sn = math.cos(math.radians(lean * s)), math.sin(math.radians(lean * s))
        path = [(x + (px - x) * c - (py - y) * sn, y + (px - x) * sn + (py - y) * c) for (px, py) in path]
    n = len(path) - 1
    cel(draw, Limb(path, [2.5 - 2.2 * (i / float(n)) ** 1.2 for i in range(n + 1)], cap=False), col, sh=(0.5, 0.6),
        line_color=SF_HORN_LINE, lw=0.8)


def _sf_spine(draw, x, y, ang=-90.0, k=1.0):
    """One spike of the ridge down the back of his head and his back."""
    cel(draw, Poly(xform([(-0.9, -1.5), (2.8, 0.0), (-0.9, 1.5)], x, y, ang, k)), SF_HORN, sh=None, lw=0.6,
        line_color=SF_HORN_LINE)


def draw_shadowfiend(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=1.1, head=1.0)
    d = r.d
    flap = [0.0, 1.0, 0.0, 1.0][frame]
    wag = [0.0, 1.0, 0.0, -1.0][frame]
    cx = r.cx
    m = -1 if r.back else 1            # his left is the right of the picture head-on, its left from behind
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    top = hy - ry

    def wings():
        if d:
            _sf_wing(draw, cx - d * 3.0, r.sh_y - 1.0, -d, flap, 0.9)
        else:
            for s in (-1, 1):
                _sf_wing(draw, cx + s * 2.6, r.sh_y - 1.0, s, flap)

    def tail():
        if d:
            _sf_tail(draw, (cx - d * 3.4, r.hip_y + 0.4), (cx - d * 12.0, r.hip_y + 8.0 + wag),
                     (cx - d * 13.4, r.hip_y + 2.6 - wag * 0.6))
        else:
            x0, cy0 = (0.0, 6.4) if r.back else (4.0, 9.0)
            _sf_tail(draw, (cx + m * x0, r.hip_y + 0.2), (cx + m * (x0 + 8.0), r.hip_y + cy0 + wag),
                     (cx + m * 14.4, r.hip_y + 3.0 - wag * 0.6))

    # head-on and in profile the wings and the tail are behind him
    if not r.back:
        wings()
        tail()
    if d:
        rig_arms(r, draw, SF_SKIN, SF_SKIN, layer="far", hands=False)
    rig_legs(r, draw, SF_SKIN, shade(SF_SKIN, 1.0), bare=True)
    rig_torso(r, draw, SF_SKIN)
    if not r.back:
        # the paler belly, kept inside the body (in profile it stood out of his chest as a bump)
        clipped(draw, torso_shape(r), lambda l: Ell(cx + d * 3.0, r.waist_y - 1.0, 3.8, 3.6).draw(l, fill=lit(SF_SKIN, 0.6)))
    rig_arms(r, draw, SF_SKIN, SF_SKIN, layer="near", hands=False)
    for side in _sides(r):
        h = hand_at(r, side)
        cel(draw, Ell(h[0], h[1], 2.2, 2.0), SF_SKIN, sh=(0.5, 0.5))
        for k in (-1, 0, 1):
            x = h[0] + k * 1.2
            Poly([(x - 0.5, h[1] + 1.2), (x + 0.5, h[1] + 1.2), (x + k * 0.3, h[1] + 3.2)]).draw(draw, fill=SF_HORN)
    if r.back:
        # ...and from behind they lie over his back, a ridge of spikes down his spine between them
        wings()
        tail()
        for i, y in enumerate((r.sh_y + 2.6, r.sh_y + 6.6)):
            _sf_spine(draw, cx, y, -90.0, 0.9 - i * 0.1)
    if d:
        _sf_horn(draw, hx - d * 1.8, top + 1.0, -d, far=True, lean=10.0)
        for a in (-34.0, 0.0, 34.0):
            nx, ny = -d * math.cos(math.radians(a)), math.sin(math.radians(a))
            _sf_spine(draw, hx + nx * (rx - 0.7), hy - 0.6 + ny * (ry - 0.7), math.degrees(math.atan2(ny - 0.25, nx)))
    head_skull(r, draw, SF_SKIN, ears=False)
    if d:
        _sf_horn(draw, hx - d * 6.6, top + 2.6, -d, lean=10.0)
    else:
        for s in (-1, 1):
            _sf_horn(draw, hx + s * (rx - 3.4), top + 2.6, s)
    if r.back:
        for i, y in enumerate((top + 5.6, top + 10.6, top + 15.6)):
            _sf_spine(draw, hx, y, -90.0, 1.1 - i * 0.1)
        return
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
PG_SH = (188, 198, 234)        # the sheet's shadow side: blue enough to be darker than snow and sand
PG_CHINA = (92, 140, 236)      # blue-and-white china, as the cup and the plate it throws in a match are
PG_CHINA_LINE = (52, 78, 160)
PG_PATCH = (150, 198, 242)


def _pg_at(pts, x, y, tilt, mx=1.0):
    """Local points (u right, v down) turned by `tilt` degrees and moved to (x, y); mx = -1 mirrors."""
    a = math.radians(tilt)
    c, s = math.cos(a), math.sin(a)
    return [(x + u * mx * c - v * s, y + u * mx * s + v * c) for (u, v) in pts]


def _pg_cup(draw, x, y, tilt, mx=1.0):
    """A teacup, 9 units across with its handle: big enough to be a teacup at game size. (The
    three things that flew round it were 5 units and less: a battery, a flag and a stick.)"""
    def at(pts):
        return _pg_at(pts, x, y, tilt, mx)

    cel(draw, Limb(at(arc_pts(3.9, 0.1, 1.9, 1.8, -80, 80, 8)), 0.6), PG_CHINA, sh=None, line_color=PG_CHINA_LINE, lw=0.6)
    cel(draw, Poly(at([(-1.7, 2.4), (1.7, 2.4), (2.0, 3.5), (-2.0, 3.5)])), PG_CHINA, sh=None, line_color=PG_CHINA_LINE, lw=0.7)
    bowl = Poly(at([(-3.8, -2.2), (3.8, -2.2), (3.4, 0.6), (2.3, 2.7), (-2.3, 2.7), (-3.4, 0.6)]))
    cel(draw, bowl, PG_CHINA, sh=None, line_color=PG_CHINA_LINE, lw=0.8,
        regions=[(Poly(at([(-5, -0.5), (5, -0.5), (5, 0.9), (-5, 0.9)])), PG_SHEET)])
    cel(draw, Poly(at(arc_pts(0.0, -2.2, 3.8, 1.25, 0, 360, 18))), PG_SHEET, sh=None, line_color=PG_CHINA_LINE, lw=0.7)
    Poly(at(arc_pts(0.0, -2.1, 2.7, 0.7, 0, 360, 14))).draw(draw, fill=(156, 96, 56))


def _pg_plate(draw, x, y, tilt):
    """A plate, tipped toward the camera: white with a blue ring, lined in blue (in the kit's
    slate a white disc beside a white ghost was a second, smaller ghost)."""
    def ring(rx, ry):
        return Poly(_pg_at(arc_pts(0.0, 0.0, rx, ry, 0, 360, 22), x, y, tilt))

    cel(draw, ring(4.9, 3.3), PG_SHEET, sh=(0.0, 0.7), tone=PG_SH, line_color=PG_CHINA_LINE, lw=0.8)
    ring(3.5, 2.2).draw(draw, fill=PG_CHINA)
    ring(2.5, 1.45).draw(draw, fill=PG_SHEET)


def draw_poltergeist(draw, ox, oy, direction, frame):
    d, back = _dir(direction)
    bob = [0, -2, -3, -1][frame]
    base = oy + 54 + bob
    cx = ox + 32
    wave = [0.0, 1.0, 0.0, -1.0][frame]
    hem_y = base - 3.0
    k = d or 1
    # The sheet, and the two stubs of arms under it, as one mass with one line round it (the arms
    # were outlined triangles laid on the sheet). Head-on the arms are held up and out, under the
    # things it is floating; in profile it leans into its flight, the hem trailing behind it,
    # with one arm out ahead and well under the mouth (level with the mouth it was a beak).
    if d:
        prof = arc_pts(-0.6, base - 24.0, 12.4, 13.0, 180, 360, 30)
        prof += [(12.0, base - 12.0), (11.2 + wave * 0.4, hem_y), (7.2, hem_y + 2.2 + wave * 0.5), (3.0, hem_y - 0.6),
                 (-1.4, hem_y + 2.2 - wave * 0.5), (-6.0, hem_y - 0.4), (-10.4, hem_y + 2.0 + wave * 0.5),
                 (-16.2 - wave * 0.6, hem_y - 1.4 + wave * 0.8), (-13.8, base - 11.0)]
        sheet = Poly([(cx + d * u, y) for (u, y) in prof])
        arms = [Limb([(cx + d * 8.0, base - 10.6), (cx + d * 13.2, base - 11.4), (cx + d * 16.0, base - 13.6 - wave)],
                     [2.5, 2.0, 1.4])]
        folds = [Poly([(cx - d * 7.4, hem_y + 3.0), (cx - d * 5.6, base - 14.0), (cx - d * 4.4, hem_y + 3.0)]),
                 Poly([(cx + d * 1.4, hem_y + 3.0), (cx + d * 2.6, base - 11.0), (cx + d * 4.4, hem_y + 3.0)])]
    else:
        pts = arc_pts(cx, base - 24.0, 12.4, 13.0, 180, 360, 30)
        pts += [(cx + 12.8, base - 12.0), (cx + 12.0 + wave, hem_y)]
        for i in range(1, 6):
            t = i / 6.0
            pts.append((cx + 12.0 + wave - 24.0 * t, hem_y + (2.2 if i % 2 else -0.6) + (wave * 0.6 if i % 2 else 0)))
        pts += [(cx - 12.0 + wave, hem_y), (cx - 12.8, base - 12.0)]
        sheet = Poly(pts)
        arms = [Limb([(cx + s * 10.0, base - 15.0), (cx + s * 14.2, base - 15.6 - wave * s * 0.5),
                      (cx + s * 16.6, base - 17.8 - wave * s)], [2.6, 2.1, 1.5]) for s in (-1, 1)]
        folds = [Poly([(cx - 5.6 + wave, hem_y + 3.0), (cx - 3.6, base - 13.0), (cx - 2.4 + wave, hem_y + 3.0)]),
                 Poly([(cx + 2.4 + wave, hem_y + 3.0), (cx + 4.4, base - 15.0), (cx + 5.8 + wave, hem_y + 3.0)])]
    blob(draw, [sheet] + arms, PG_SHEET, sh=(2.1, 1.5), tone=PG_SH, regions=[(f, PG_SH) for f in folds])
    if back:
        # a patch sewn on the back of it: an old bed sheet, and something to see from behind
        px, py = cx + 3.6, base - 15.6
        patch = Poly([(px - 3.3, py - 2.9), (px + 3.1, py - 3.3), (px + 3.5, py + 2.7), (px - 3.0, py + 3.1)])
        cel(draw, patch, PG_PATCH, sh=(0.0, 0.0), line_color=PG_CHINA_LINE, lw=0.6)
        for (u0, v0, u1, v1) in ((-4.3, -1.6, -2.6, -1.6), (-4.2, 1.2, -2.5, 1.2), (2.6, -1.8, 4.3, -1.8), (2.8, 1.0, 4.5, 1.0),
                                 (-1.4, -4.2, -1.4, -2.4), (1.4, -4.4, 1.4, -2.6), (-1.2, 2.2, -1.2, 4.0), (1.6, 2.0, 1.6, 3.8)):
            stroke(draw, [(px + u0, py + v0), (px + u1, py + v1)], 0.55, PG_CHINA_LINE)
    else:
        fx = cx + d * 4.0
        ey = base - 24.0
        iris = (70, 70, 110)
        if d:
            # the near eye whole, the far one narrow at the leading edge (they were the other way round)
            ms_eye(draw, fx + d * 3.8, ey, 2.8, 5.0, iris, (float(d), 0.0), "bright", skin=PG_SHEET, side=d)
            ms_eye(draw, fx - d * 2.4, ey, 4.0, 5.4, iris, (float(d), 0.0), "bright", skin=PG_SHEET, side=-d)
            Ell(fx - d * 6.2, base - 20.4, 1.7, 0.85).draw(draw, fill=(255, 190, 202))
        else:
            for side in (-1, 1):
                ms_eye(draw, fx + side * 4.6, ey, 4.0, 5.4, iris, (0.0, 0.0), "bright", skin=PG_SHEET, side=side)
                Ell(fx + side * 7.2, base - 20.6, 1.7, 0.85).draw(draw, fill=(255, 190, 202))
        mx = fx + d * 1.6
        stroke(draw, [(mx - 2.4, base - 19.0), (mx, base - 18.2), (mx + 2.4, base - 19.0)], 0.7, shade(PG_SH, 1.8))
        cel(draw, RRect(mx - 1.4, base - 18.8, mx + 1.4, base - 15.2, 1.3), (255, 122, 146), sh=None, lw=0.6)
    # What it has floating: a teacup at its left hand and a plate at its right, bobbing against
    # each other and never across its face (they circled it a quarter turn a frame, at face
    # height: every stride one of them was on its mouth). In profile the cup is out ahead of the
    # reaching arm and the plate trails behind and above.
    cup_bob, plate_bob = [0.0, -1.4, -0.4, 1.0][frame], [0.4, 1.4, 0.0, -1.2][frame]
    cup_tilt, plate_tilt = [-10, 6, 14, -2][frame], [12, -4, -14, 2][frame]
    if d:
        _pg_plate(draw, cx - d * 17.4, base - 30.6 + plate_bob, -d * (16 + plate_tilt))
        _pg_cup(draw, cx + d * 18.8, base - 21.6 + cup_bob, d * (8 + cup_tilt), d)
    else:
        s = -1 if back else 1
        _pg_plate(draw, cx - s * 18.2, base - 23.6 + plate_bob, -s * plate_tilt)
        _pg_cup(draw, cx + s * 18.0, base - 24.4 + cup_bob, s * cup_tilt, s)


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
