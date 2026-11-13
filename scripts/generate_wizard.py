#!/usr/bin/env python3
"""Generate sprites/wizard.png -- the Wizard.

A MapleStory magician. The hat is most of his silhouette: a huge violet cone
whose tip flops over, on a brim wider than his shoulders. Under it, a white
mane -- side hair, a cloud of beard, bushy brows -- round big bright eyes, a
little robe with bell sleeves and gold trim, and a staff taller than his hat
with a glowing orb in a gold crescent.

The staff is in his left hand and its orb stands clear over the brim in every
facing: out to his side head-on and from behind (leaning a little outward),
and in profile planted ahead of him in the far hand, which does not swing.
The hat's tip flops away from it. From behind he is the mane: long white hair
out from under the brim and down over his shoulders in three locks.

Gold here is drawn with a thin line: under the kit's one-unit ink a band two
units tall is all line, and his trim was brown.

generate_icon.py draws the app icon from draw_wizard, and main() runs it
after writing the sheet, so the icon is redrawn with him.
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sprite_base import (
    DOWN, UP, LEFT, RIGHT, HAIR_STYLES, SKIN, Ell, Limb, Poly, RRect, arm_pts, blob, cel,
    face_anchor, generate_character, hand_at, head_face, head_skull, ink, lit, rig,
    rig_arms, rig_belt, rig_hair, rig_hand, rig_legs, rig_robe, shade, sparkle, star,
)

ROBE = (122, 80, 204)
HAT = (94, 60, 180)
GOLD = (248, 200, 78)
BEARD = (248, 248, 252)
BEARD_SHADE = (206, 210, 232)
STAFF = (160, 104, 62)
ORB = (186, 148, 255)
ORB_CORE = (246, 238, 255)
SHOE = (126, 82, 62)
IRIS = (78, 104, 184)
SASH = shade(ROBE, 1.9)
SPARKS = ((255, 246, 168), (196, 232, 255), (255, 214, 250))
TRIM_LW = 0.55          # the line round anything gold

# Where the sparkles sit around the orb on each frame, so they twinkle.
SPARK_AT = (((-4.6, -3.0), (4.2, 1.8)), ((3.8, -4.2), (-4.0, 2.6)),
            ((-3.4, 3.8), (4.8, -2.2)), ((4.4, 3.4), (-4.8, -1.6)))


def _hat(r, draw):
    hx, hy, d = r.hx, r.head_cy, r.d
    k = r.head_rx / 12.4
    # The tip flops away from the staff: left head-on, backward in profile.
    flop = -d if d else (-1 if not r.back else 1)
    if d:
        brim = Ell(hx + d * 1.4, hy - 7.0 * k, 15.8 * k, 4.2 * k)
    else:
        brim = Ell(hx, hy - 7.0 * k, 17.2 * k, 4.4 * k)
    cel(draw, brim, HAT, sh=(0.0, 1.3))
    bx = hx - d * 0.6
    nod = r.ph * 0.6                 # the tip nods with the stride
    pts = [(bx, hy - 7.6 * k), (bx - 0.5 * flop, hy - 13.6 * k), (bx + 1.2 * flop, hy - 18.8 * k),
           (bx + 5.6 * flop, hy - 21.6 * k), (bx + 10.4 * flop, hy - 20.2 * k + nod * 0.5),
           (bx + 13.0 * flop, hy - 16.4 * k + nod)]
    cel(draw, Limb(pts, [9.2 * k, 6.5 * k, 4.3 * k, 3.0 * k, 1.8 * k, 0.6]), HAT, sh=(1.7, 0.7))
    # gold band round the base of the cone, with a star on the front
    y0, y1 = hy - 7.6 * k, hy - 10.5 * k
    w0, w1 = 9.2 * k, 7.9 * k
    cel(draw, Poly([(bx - w0, y0), (bx + w0, y0), (bx + w1 - 0.2 * flop, y1), (bx - w1 - 0.2 * flop, y1)]),
        GOLD, sh=(0.0, 0.8), lw=TRIM_LW)
    if not r.back:
        # a star pinned on it, bigger than the band so its points show against the hat
        sx, sy = bx + d * 4.0, (y0 + y1) / 2.0 - 0.2
        star(draw, sx, sy, 3.7, ink(GOLD), inner=0.5)
        star(draw, sx, sy, 2.9, lit(GOLD, 1.8))
    # stars embroidered on the cone
    for (u, v, rad) in ((0.4 * flop, -14.4, 1.5), (3.6 * flop, -18.8, 1.15)):
        star(draw, bx + u * k, hy + v * k, rad, GOLD)


def _mane(r, draw):
    """His white hair and beard, one mass round the face (drawn piece by piece the side hair
    was a pair of ear muffs, and in profile a white ear on a bald head)."""
    hx, hy, rx, d = r.hx, r.head_cy, r.head_rx, r.d
    if d:
        u = lambda x: hx + d * x
        trail = abs(r.ph) * 0.6
        parts = [Ell(u(4.6), hy + 10.2, 5.6, 6.4), Ell(u(1.0), hy + 7.6, 3.6, 3.8),
                 Ell(u(5.8), hy + 15.4, 3.3, 3.4), Ell(u(8.6), hy + 7.2, 2.9, 1.8),
                 # the hair: down the back of the head from under the brim to his collar, and a
                 # sideburn forward to the beard
                 Ell(u(-7.0), hy + 0.6, 4.8, 6.0), Ell(u(-8.1 - trail), hy + 6.4, 3.9, 5.2),
                 Ell(u(-4.4), hy + 4.6, 2.7, 3.8)]
        strands = [[(u(3.2), hy + 10.6), (u(3.8), hy + 14.2)], [(u(6.4), hy + 10.2), (u(6.6), hy + 13.4)],
                   [(u(-8.0), hy + 3.0), (u(-8.8 - trail), hy + 8.6)]]
    else:
        parts = [Ell(hx, hy + 10.6, 7.2, 7.0), Ell(hx - 7.4, hy + 8.0, 3.4, 3.5),
                 Ell(hx + 7.4, hy + 8.0, 3.4, 3.5), Ell(hx, hy + 16.2, 3.9, 3.6),
                 Ell(hx - 2.8, hy + 7.2, 3.1, 1.8), Ell(hx + 2.8, hy + 7.2, 3.1, 1.8)]
        for s in (-1, 1):
            x = hx + s * (rx - 0.6)
            parts += [Ell(x, hy - 0.4, 2.8, 3.6), Ell(x + s * 0.6, hy + 3.4, 2.5, 3.2)]
        strands = [[(hx - 3.4, hy + 11.0), (hx - 2.4, hy + 15.4)], [(hx + 3.4, hy + 11.0), (hx + 2.4, hy + 15.4)],
                   [(hx, hy + 12.2), (hx, hy + 17.2)]]
    blob(draw, parts, BEARD, sh=(1.2, 1.3), tone=BEARD_SHADE)
    for s in strands:
        cel(draw, Limb(s, [0.45, 0.3]), BEARD_SHADE, sh=None, line=False)


def _back_hair(r, draw):
    """From behind: long white hair out from under the brim, over his shoulders in three locks
    that sway with the stride."""
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    sway = r.ph * 0.7
    parts = [Ell(hx, hy + 0.6, rx + 0.9, ry + 0.4),
             Ell(hx - 6.6 + sway, hy + 9.4, 4.6, 4.6), Ell(hx + sway * 1.4, hy + 11.4, 5.0, 4.8),
             Ell(hx + 6.6 + sway, hy + 9.4, 4.6, 4.6)]
    blob(draw, parts, BEARD, sh=(1.3, 1.4), tone=BEARD_SHADE)
    for (x0, x1) in ((-3.9, -3.3), (3.9, 3.3)):
        cel(draw, Limb([(hx + x0, hy + 6.4), (hx + x1 + sway, hy + 12.6)], [0.5, 0.3]), BEARD_SHADE,
            sh=None, line=False)


def _brows(r, draw):
    e1, e2, ey, _, _ = face_anchor(r)
    k = r.head_rx / 12.4
    pairs = ((-1, e1), (1, e2)) if not r.d else ((-r.d, e1), (r.d, e2))
    for side, ex in pairs:
        w = 1.0 if (not r.d or side == -r.d) else 0.7
        blob(draw, [Ell(ex, ey - 4.1 * k, 2.3 * w, 1.05), Ell(ex + side * 1.9 * w, ey - 4.4 * k, 1.3 * w, 0.9)],
             BEARD, sh=(0.0, 0.5), tone=BEARD_SHADE, lw=0.8)


def _sleeve_cuff(r, draw, side, reach, out=0.0):
    _, e, w, h = arm_pts(r, side, reach, 0.0, out)
    dx, dy = w[0] - e[0], w[1] - e[1]
    ln = (dx * dx + dy * dy) ** 0.5 or 1.0
    ux, uy = dx / ln, dy / ln
    px, py = -uy, ux
    a = (w[0] - ux * 2.4, w[1] - uy * 2.4)
    b = (w[0] + ux * 0.6, w[1] + uy * 0.6)
    near = r.near(side)
    cel(draw, Poly([(a[0] + px * 2.1, a[1] + py * 2.1), (b[0] + px * 3.1, b[1] + py * 3.1),
                    (b[0] - px * 3.1, b[1] - py * 3.1), (a[0] - px * 2.1, a[1] - py * 2.1)]),
        ROBE if near else shade(ROBE, 0.6), sh=(0.6, 0.6))
    # the gold edge of the bell: round-ended, so what shows either side of the hand is a rim
    # and not a pair of points
    cel(draw, Limb([(b[0] + px * 2.5, b[1] + py * 2.5), (b[0] - px * 2.5, b[1] - py * 2.5)], [0.8, 0.8], cap0=True),
        GOLD if near else shade(GOLD, 0.6), sh=None, lw=TRIM_LW)


def _arm(r, draw, side, reach=0.0, out=0.0):
    rig_arms(r, draw, ROBE, SKIN, sides=(side,), hands=False, reach=reach, out=out)
    _sleeve_cuff(r, draw, side, reach, out)


def _staff(r, draw, grip, lean):
    """Planted on the ground through the hand at `grip`, its head `lean` units to that side of
    its foot; the orb rides above the brim."""
    gx, gy = grip
    bot_y, top_y = r.base_y - 1.0, r.head_cy - 14.6
    t = (bot_y - gy) / (bot_y - top_y)
    bot = (gx - lean * t, bot_y)
    top = (bot[0] + lean, top_y)
    cel(draw, Limb([bot, top], [1.3, 1.15]), STAFF, sh=(0.7, 0.0), lw=0.7)
    ux, uy = top[0] - bot[0], top[1] - bot[1]
    ln = (ux * ux + uy * uy) ** 0.5
    ux, uy = ux / ln, uy / ln
    px, py = -uy, ux
    ox, oy = top[0] + ux * 3.5, top[1] + uy * 3.5
    # a gold crescent cradling the orb
    cres = [(-4.0, -0.4), (-1.6, 1.6), (1.6, 1.6), (4.0, -0.4), (2.6, -2.4), (-2.6, -2.4)]
    cel(draw, Poly([(top[0] + px * a + ux * b, top[1] + py * a + uy * b) for (a, b) in cres]),
        GOLD, sh=(0.0, 0.6), lw=TRIM_LW)
    cel(draw, Ell(ox, oy, 3.6, 3.6), ORB, sh=(1.1, 1.1), hi=(0.8, 0.8))
    Ell(ox - 1.0, oy - 1.0, 1.3, 1.3).draw(draw, fill=ORB_CORE)
    for i, (dx, dy) in enumerate(SPARK_AT[r.frame]):
        sparkle(draw, ox + dx, oy + dy, 1.9 if i == 0 else 1.4, SPARKS[(r.frame + i) % 3])


def _hem(r, draw, flare, sway, tall=2.1):
    """The robe's gold hem: the band rig_robe(trim=) draws, taller and thinly lined."""
    cx, d = r.cx, r.d
    top, hem = r.sh_y - 2.4, r.base_y - 2.6
    if d:
        f = ((cx + d * 4.3, top + 1.8), (cx + d * (4.6 + flare * 0.5) + sway * d, hem))
        b = ((cx - d * 4.5, top + 1.5), (cx - d * (4.6 + flare * 0.8) - sway * d, hem))
    else:
        w = r.sh_w
        f = ((cx + w, r.sh_y + 0.6), (cx + w + flare + sway, hem))
        b = ((cx - w, r.sh_y + 0.6), (cx - w - flare + sway, hem))

    def at(edge, y):
        (x0, y0), (x1, y1) = edge
        return (x0 + (x1 - x0) * (y - y0) / (y1 - y0), y)

    cel(draw, Poly([at(b, hem - tall), at(f, hem - tall), f[1], b[1]]), GOLD, sh=(0.0, 0.6), lw=TRIM_LW)


def _sash(r, draw):
    """A dark sash at the waist with a gold clasp (a gold belt ran cuff to cuff through both
    hands as one bar)."""
    cx, d, y = r.cx, r.d, r.waist_y + 1.0
    w = 4.6 if d else r.hip_w + 0.7
    cel(draw, RRect(cx - w, y, cx + w, y + 2.3, 0.6), SASH, sh=None, lw=0.7)
    if r.front:
        cel(draw, RRect(cx - 1.7, y - 0.5, cx + 1.7, y + 2.8, 0.7), GOLD, sh=None, lw=TRIM_LW)


def _robe_stars(r, draw):
    if r.back:
        pts = ((-3.6, 1.0), (3.4, 3.6), (0.2, 6.2))
    elif r.d:
        pts = ((r.d * 1.6, 2.2), (-r.d * 2.4, 5.4))
    else:
        pts = ((-4.0, 2.6), (4.2, 4.4), (-1.4, 6.8))
    for (u, v) in pts:
        sparkle(draw, r.cx + u, r.hip_y - 3.0 + v, 1.3, GOLD, core=GOLD)


def draw_wizard(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, head=0.95)
    rs = rig(ox, oy, direction, frame, head=0.95, step=0)     # the arm that carries does not swing
    d = r.d
    flare = 3.4
    # The staff is in his left hand: screen right head-on, screen left from
    # behind, and the far hand in profile.
    ss = (1 if not r.back else -1) if not d else -d
    reach, out = (0.3, 8.2) if d else (0.8, 4.1)
    grip = hand_at(rs, ss, reach, 0.0, out)
    lean = d * 5.0 if d else ss * 4.4
    if d:
        # planted well ahead of him, on his far side: only the brim crosses it
        _staff(r, draw, grip, lean)
        _arm(rs, draw, -d, reach, out)
        rig_hand(rs, draw, -d, SKIN, reach, out=out)
    rig_legs(r, draw, shade(ROBE, 1.4), SHOE)
    # in profile the hem flares on both strides (the kit's opens on one and closes on the other)
    rr = rig(ox, oy, direction, frame, head=0.95, step=abs(r.step) * 1.7) if d else r
    rig_robe(rr, draw, ROBE, flare=flare)
    _hem(r, draw, flare, rr.ph * 0.8)
    _robe_stars(r, draw)
    _sash(r, draw)
    if not d:
        _arm(r, draw, -ss)
        _arm(rs, draw, ss, reach, out)
    if r.back:
        # beside him, under his hair and the brim
        _staff(r, draw, grip, lean)
    head_skull(r, draw, SKIN, ears=False)
    if r.back:
        _back_hair(r, draw)
    else:
        head_face(r, draw, SKIN, iris=IRIS, mouth=None, brows=False)
        _mane(r, draw)
        _brows(r, draw)
    _hat(r, draw)
    if d:
        _arm(r, draw, d)
        rig_hand(r, draw, d, SKIN)
    else:
        if not r.back:
            _staff(r, draw, grip, lean)
        rig_hand(r, draw, -ss, SKIN)
        rig_hand(rs, draw, ss, SKIN, reach, out=out)


def main():
    generate_character("wizard", draw_func=draw_wizard)
    # The app icon is the wizard (imported here: generate_icon imports this module)
    import generate_icon
    generate_icon.main()


if __name__ == "__main__":
    main()
