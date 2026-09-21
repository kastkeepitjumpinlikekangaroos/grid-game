#!/usr/bin/env python3
"""Generate sprites/wizard.png -- the Wizard.

A MapleStory magician. The hat is most of his silhouette: a huge violet cone
whose tip flops over, on a brim wider than his shoulders. Under it, a cloud of
white beard and bushy brows over big bright eyes, a little robe with bell
sleeves and gold trim, and an oversized staff with a glowing orb.

generate_icon.py draws the app icon from draw_wizard, so it tracks this.
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sprite_base import (
    DOWN, UP, LEFT, RIGHT, HAIR_STYLES, SKIN, Ell, Limb, Poly, arm_pts, blob, cel,
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
SPARKS = ((255, 246, 168), (196, 232, 255), (255, 214, 250))

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
    pts = [(bx, hy - 7.6 * k), (bx - 0.6 * flop, hy - 12.8 * k), (bx + 1.8 * flop, hy - 16.6 * k),
           (bx + 7.2 * flop, hy - 17.0 * k), (bx + 11.6 * flop, hy - 12.6 * k)]
    cel(draw, Limb(pts, [9.2 * k, 6.4 * k, 3.9 * k, 2.4 * k, 0.7]), HAT, sh=(1.7, 0.7))
    # gold band round the base of the cone, with a star on the front
    y0, y1 = hy - 7.6 * k, hy - 10.0 * k
    w0, w1 = 9.2 * k, 8.1 * k
    cel(draw, Poly([(bx - w0, y0), (bx + w0, y0), (bx + w1 - 0.1 * flop, y1), (bx - w1 - 0.1 * flop, y1)]),
        GOLD, sh=(0.0, 0.7))
    if not r.back:
        star(draw, bx + d * 4.0, (y0 + y1) / 2.0, 2.3, lit(GOLD, 1.2), line=ink(GOLD))
    # stars embroidered on the cone
    for (u, v, rad) in ((3.0 * flop, -12.6, 1.3), (5.2 * flop, -15.4, 1.0)):
        star(draw, bx + u * k, hy + v * k, rad, GOLD)


def _beard(r, draw):
    hx, hy, d = r.hx, r.head_cy, r.d
    if d:
        u = lambda x: hx + d * x
        parts = [Ell(u(4.6), hy + 10.2, 5.6, 6.4), Ell(u(1.0), hy + 7.6, 3.6, 3.8),
                 Ell(u(5.8), hy + 15.4, 3.3, 3.4), Ell(u(8.6), hy + 7.2, 2.9, 1.8)]
        strands = [[(u(3.2), hy + 10.6), (u(3.8), hy + 14.2)], [(u(6.4), hy + 10.2), (u(6.6), hy + 13.4)]]
    else:
        parts = [Ell(hx, hy + 10.6, 7.2, 7.0), Ell(hx - 7.4, hy + 8.0, 3.4, 3.5),
                 Ell(hx + 7.4, hy + 8.0, 3.4, 3.5), Ell(hx, hy + 16.2, 3.9, 3.6),
                 Ell(hx - 2.8, hy + 7.2, 3.1, 1.8), Ell(hx + 2.8, hy + 7.2, 3.1, 1.8)]
        strands = [[(hx - 3.4, hy + 11.0), (hx - 2.4, hy + 15.4)], [(hx + 3.4, hy + 11.0), (hx + 2.4, hy + 15.4)],
                   [(hx, hy + 12.2), (hx, hy + 17.2)]]
    blob(draw, parts, BEARD, sh=(1.2, 1.3), tone=BEARD_SHADE)
    for s in strands:
        cel(draw, Limb(s, [0.45, 0.3]), BEARD_SHADE, sh=None, line=False)


def _brows(r, draw):
    e1, e2, ey, _, _ = face_anchor(r)
    k = r.head_rx / 12.4
    pairs = ((-1, e1), (1, e2)) if not r.d else ((-r.d, e1), (r.d, e2))
    for side, ex in pairs:
        w = 1.0 if (not r.d or side == -r.d) else 0.7
        blob(draw, [Ell(ex, ey - 4.1 * k, 2.3 * w, 1.05), Ell(ex + side * 1.9 * w, ey - 4.4 * k, 1.3 * w, 0.9)],
             BEARD, sh=(0.0, 0.5), tone=BEARD_SHADE, lw=0.8)


def _side_hair(r, draw):
    """White hair showing under the brim."""
    hx, hy, rx, d = r.hx, r.head_cy, r.head_rx, r.d
    if r.back:
        rig_hair(r, draw, BEARD, "short", hat=True)
        return
    sides = (-1, 1) if not d else (-d,)
    for s in sides:
        x = hx + s * (rx - 0.6) if not d else hx - d * (rx - 1.6)
        blob(draw, [Ell(x, hy - 0.4, 2.8, 3.6), Ell(x + s * 0.6, hy + 3.2, 2.4, 2.8)],
             BEARD, sh=(0.6, 0.9), tone=BEARD_SHADE)


def _sleeve_cuff(r, draw, side, reach, out=0.0):
    _, e, w, h = arm_pts(r, side, reach, 0.0, out)
    dx, dy = w[0] - e[0], w[1] - e[1]
    ln = (dx * dx + dy * dy) ** 0.5 or 1.0
    ux, uy = dx / ln, dy / ln
    px, py = -uy, ux
    a = (w[0] - ux * 2.4, w[1] - uy * 2.4)
    b = (w[0] + ux * 0.4, w[1] + uy * 0.4)
    col = ROBE if r.near(side) else shade(ROBE, 0.6)
    cel(draw, Poly([(a[0] + px * 2.1, a[1] + py * 2.1), (b[0] + px * 3.4, b[1] + py * 3.4),
                    (b[0] - px * 3.4, b[1] - py * 3.4), (a[0] - px * 2.1, a[1] - py * 2.1)]),
        col, sh=(0.6, 0.6))
    cel(draw, Poly([(b[0] + px * 3.4 - ux * 1.1, b[1] + py * 3.4 - uy * 1.1), (b[0] + px * 3.4, b[1] + py * 3.4),
                    (b[0] - px * 3.4, b[1] - py * 3.4), (b[0] - px * 3.4 - ux * 1.1, b[1] - py * 3.4 - uy * 1.1)]),
        GOLD, sh=None)


def _arm(r, draw, side, reach=0.0, out=0.0):
    rig_arms(r, draw, ROBE, SKIN, sides=(side,), hands=False, reach=reach, out=out)
    _sleeve_cuff(r, draw, side, reach, out)


def _staff(r, draw, side, reach, out):
    hx, hy = hand_at(r, side, reach, 0.0, out)
    d = r.d
    if d:
        # In profile it is in the far hand, leaning back: the body hides the
        # shaft and the orb shows behind his head, clear of the face.
        bot = (hx + d * 2.4, r.base_y - 1.0)
        top = (hx - d * 11.0, r.head_cy - 2.6)
    else:
        bot = (hx + 0.2, r.base_y - 1.0)
        top = (hx + 0.2, r.head_cy - 3.2)
    cel(draw, Limb([bot, top], [1.25, 1.1]), STAFF, sh=(0.6, 0.0))
    ux, uy = top[0] - bot[0], top[1] - bot[1]
    ln = (ux * ux + uy * uy) ** 0.5
    ux, uy = ux / ln, uy / ln
    px, py = -uy, ux
    ox, oy = top[0] + ux * 3.4, top[1] + uy * 3.4
    # a gold crescent cradling the orb
    cres = [(-3.9, -0.4), (-1.6, 1.6), (1.6, 1.6), (3.9, -0.4), (2.6, -2.2), (-2.6, -2.2)]
    cel(draw, Poly([(top[0] + px * a + ux * b, top[1] + py * a + uy * b) for (a, b) in cres]),
        GOLD, sh=(0.0, 0.6))
    cel(draw, Ell(ox, oy, 3.5, 3.5), ORB, sh=(1.1, 1.1), hi=(0.8, 0.8))
    Ell(ox - 1.0, oy - 1.0, 1.3, 1.3).draw(draw, fill=ORB_CORE)
    for i, (dx, dy) in enumerate(SPARK_AT[r.frame]):
        sparkle(draw, ox + dx, oy + dy, 1.9 if i == 0 else 1.4, SPARKS[(r.frame + i) % 3])


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
    d = r.d
    # The staff is in his left hand: screen right head-on, screen left from
    # behind, and the far hand in profile.
    ss = (1 if not r.back else -1) if not d else -d
    reach, out = (0.0, 0.0) if d else (0.0, 2.0)
    if r.back or d:
        _staff(r, draw, ss, reach, out)
    if d:
        _arm(r, draw, -d)
        rig_hand(r, draw, -d, SKIN)
    rig_legs(r, draw, shade(ROBE, 1.4), SHOE)
    rig_robe(r, draw, ROBE, trim=GOLD, flare=3.4)
    _robe_stars(r, draw)
    rig_belt(r, draw, GOLD, buckle=lit(GOLD, 1.3))
    if not d:
        _arm(r, draw, -ss)
        _arm(r, draw, ss, reach, out)
    head_skull(r, draw, SKIN, ears=False)
    if not r.back:
        head_face(r, draw, SKIN, iris=IRIS, mouth=None, brows=False)
    _side_hair(r, draw)
    if not r.back:
        _beard(r, draw)
        _brows(r, draw)
    _hat(r, draw)
    if d:
        _arm(r, draw, d)
        rig_hand(r, draw, d, SKIN)
    else:
        if not r.back:
            _staff(r, draw, ss, reach, out)
        rig_hand(r, draw, -ss, SKIN)
        rig_hand(r, draw, ss, SKIN, reach, out=out)


def main():
    generate_character("wizard", draw_func=draw_wizard)


if __name__ == "__main__":
    main()
