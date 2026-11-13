#!/usr/bin/env python3
"""Generate sprites/tidecaller.png -- the Tidecaller.

A water priestess: long aqua hair under a wave-crest tiara, a coral starfish
pinned in it, a deep blue robe with a white overdress and a hem of white surf,
and a coral trident taller than she is, a water orb in its fork and bubbles
rising beside it.

The hair is a step lighter than the robe on purpose: in the engine's grade two
blues a shade apart are one blue, and from behind she is nothing but hair and
robe. The trident stands upright in every facing, its head clear above the
hair; in profile she holds it at her side, behind the face, so the shaft
crosses hair and not her eyes.
"""

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sprite_base import (
    SKIN, Ell, Limb, Poly, RRect, arc_pts, bez, blob, cel, clipped, generate_character,
    hand_at, ink, lit, rig, rig_arms, rig_belt, rig_hand, rig_head, rig_legs, rig_robe, shade,
    star, head_skull,
)

ROBE = (236, 244, 252)
ROBE_BLUE = (52, 104, 204)
AQUA = (64, 196, 214)
SURF = (240, 250, 255)
SURF_SHADE = (190, 226, 246)
HAIR = (46, 160, 208)
CORAL = (255, 126, 112)
PEARL = (240, 232, 212)
SHOE = (232, 240, 250)      # a cool white: the far shoe's shade stays grey, where pearl's turned brown
ORB = (126, 222, 255)
IRIS = (36, 146, 172)

# The trident's head, from the fork at (0, 0), y up the shaft negative: a socket,
# a curved bar, two prongs leaning out and a taller one between them. Each prong
# is 2.4 wide with 2 clear between: thinner and they are all outline.
TRIDENT = ((-1.0, 2.0), (-1.3, 0.4), (-3.8, -0.2), (-5.5, -1.8), (-6.2, -7.0), (-5.3, -9.6),
           (-3.9, -7.0), (-3.3, -3.0), (-1.3, -2.8), (-1.3, -9.0), (0.0, -12.0), (1.3, -9.0),
           (1.3, -2.8), (3.3, -3.0), (3.9, -7.0), (5.3, -9.6), (6.2, -7.0), (5.5, -1.8),
           (3.8, -0.2), (1.3, 0.4), (1.0, 2.0))
FORK_Y = 8.5          # where the fork is in the frame: the tip at -3.5, -4.5 on a stride's hop


def _bubble(draw, x, y, rad):
    cel(draw, Ell(x, y, rad, rad), (206, 244, 255), sh=None, line_color=shade(AQUA, 1.6), lw=0.6)
    Ell(x - rad * 0.35, y - rad * 0.35, rad * 0.32, rad * 0.32).draw(draw, fill=(255, 255, 255))


def _trident(r, draw, x, away):
    """Upright at x, butt on the ground. `away` is the side the bubbles rise on
    (the one away from her head)."""
    top = r.base_y - 54.0 + FORK_Y
    cel(draw, Limb([(x, r.base_y - 1.2), (x, top + 1.0)], [1.1, 1.0]), PEARL, sh=(0.5, 0.0))
    cel(draw, Poly([(x + u, top + v) for (u, v) in TRIDENT]), CORAL, sh=(0.7, 0.4),
        line_color=shade(CORAL, 2.6), lw=0.8)
    cel(draw, Ell(x, top - 1.2, 2.5, 2.5), ORB, sh=(0.8, 0.8), hi=(0.6, 0.6))
    Ell(x - 0.8, top - 2.0, 0.8, 0.8).draw(draw, fill=(255, 255, 255))
    # three bubbles rising in a loose column beside the head, one loop a walk cycle
    for k in range(3):
        t = (r.frame / 4.0 + k / 3.0) % 1.0
        bx = x + away * (8.0 + math.sin((t + k * 0.37) * 2.0 * math.pi) * 1.1)
        _bubble(draw, bx, top + 3.0 - 12.0 * t, 1.7 - 0.7 * t)


def _shine(draw, shape, cx, cy, rx, ry, a0, a1, pieces=3, thick=1.4):
    """The kit's gloss band (short tapered strokes along an arc), for hair drawn here."""
    col = lit(HAIR, 1.6)
    span = (a1 - a0) / float(pieces)

    def paint(layer):
        for i in range(pieces):
            s0 = a0 + span * i + span * 0.12
            s1 = a0 + span * (i + 1) - span * 0.12
            outer = arc_pts(cx, cy, rx + thick * 0.5, ry + thick * 0.5, s0, s1, 6)
            inner = arc_pts(cx, cy, rx - thick * 0.5, ry - thick * 0.5, s1, s0, 6)
            Poly([arc_pts(cx, cy, rx, ry, s0, s0, 1)[0]] + outer[1:-1] +
                 [arc_pts(cx, cy, rx, ry, s1, s1, 1)[0]] + inner[1:-1]).draw(layer, fill=col)
    clipped(draw, shape, paint)


def _hair_bell(r, draw):
    """The fall of hair seen head-on: a bell from the crown to the waist ending
    in four heavy locks. Behind the body from the front; from behind it is over
    the back, and most of what the camera sees of her."""
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    w = rx + 1.9
    oy = hy - 0.665
    sway = r.ph * 0.6
    y1 = hy + 19.6
    locks = ((0.92, 0.4), (0.62, -3.0), (0.33, 1.2), (0.0, -2.6), (-0.33, 1.2), (-0.62, -3.0), (-0.92, 0.4))
    bottom = [(hx + u * w + sway, y1 + dy) for (u, dy) in locks]
    right = [(hx + w + 1.5, hy + 7.0), (hx + w + 0.4 + sway * 0.5, hy + 15.0)]
    left = [(hx - w - 0.4 + sway * 0.5, hy + 15.0), (hx - w - 1.5, hy + 7.0)]
    if not r.back:
        cel(draw, Poly([(hx - w + 0.3, hy - 2.0), (hx + w - 0.3, hy - 2.0)] + right + bottom + left), HAIR,
            sh=(1.2, 1.2))
        return
    shape = Poly(arc_pts(hx, oy, w, ry + 1.9, 172, 368, 40) + right + bottom + left)
    # the skull's roundness, as a band of shadow where the hair leaves it to fall
    under = Poly(arc_pts(hx, hy + 0.2, rx + 0.4, ry + 1.2, 12, 168, 16) +
                 arc_pts(hx, hy + 1.9, rx + 0.4, ry + 1.4, 168, 12, 16))
    cel(draw, shape, HAIR, sh=(2.0, 1.5), regions=[(under, shade(HAIR))])
    _shine(draw, shape, hx, oy, w * 0.7, (ry + 1.9) * 0.62, 212, 328)


def _hair_side(r, draw):
    """Profile: the crown, the fringe and the fall down her back as one mass,
    so no line crosses it at the shoulder. Built facing +u, mirrored by d."""
    hx, hy, rx, ry, d = r.hx, r.head_cy, r.head_rx, r.head_ry, r.d
    v = 1.9
    oy = hy - v * 0.35
    hl = hy - ry * 0.56
    sway = r.ph * 1.0
    pts = [(rx + 0.1, hl + 3.2), (rx + v * 0.6, hl - 0.4)]
    pts += arc_pts(-0.9, oy, rx + v, ry + v, 322, 180, 30)
    # down the back to three locks at the waist, then up behind the ear
    pts += [(-rx - 3.0, hy + 5.0), (-rx - 3.5 - sway * 0.4, hy + 12.0), (-rx - 2.8 - sway * 0.8, hy + 17.0),
            (-rx - 3.8 - sway, hy + 20.8), (-rx - 0.6 - sway, hy + 17.8), (-rx + 1.6 - sway * 0.9, hy + 21.2),
            (-rx + 4.0 - sway * 0.7, hy + 17.6), (-rx + 6.0 - sway * 0.6, hy + 19.8), (-4.2, hy + 13.0),
            (-3.6, hy + 6.0)]
    # round the ear to a sideburn that stops behind the near eye, then the fringe
    pts += [(-3.0, hy + 1.2), (-2.6, hy - 0.8), (-1.4, hy + 0.2), (-1.0, hy + 3.8), (-0.1, hy + 3.0),
            (1.4, hl + 3.0), (4.6, hl + 1.2), (7.2, hl + 5.0), (9.0, hl + 1.2), (11.6, hl + 4.2)]
    shape = Poly([(hx + d * u, y) for (u, y) in pts])
    clipped(draw, Ell(hx, hy, rx, ry), lambda l: shape.shift(0.3, 1.1).draw(l, fill=shade(SKIN)))
    cel(draw, shape, HAIR, sh=(1.2, 1.3))
    a0, a1 = (205, 262) if d > 0 else (278, 335)
    _shine(draw, shape, hx - d * 0.9, oy, (rx + v) * 0.72, (ry + v) * 0.66, a0, a1, pieces=2)


def _tiara(r, draw):
    """A little crown of three wave crests, white as foam, and the starfish."""
    hx, hy, rx, ry, d = r.hx, r.head_cy, r.head_rx, r.head_ry, r.d
    if r.back:
        star(draw, hx + 7.4, hy - 5.6, 2.8, CORAL, line=ink(CORAL))
        return
    if d:
        # seen from the side: the tall crest over the brow, a low one behind it
        # seen from the side: a band following the crown over the brow, the tall
        # crest at its front and a low one behind it (no taller than the hair:
        # a spike standing off the head here is a cat's ear)
        y = hy - 0.665
        crest = [(11.0, -7.0), (10.7, -9.4), (10.0, -13.0), (8.6, -10.6), (7.4, -11.0), (6.0, -13.6),
                 (4.9, -12.2), (3.2, -12.6), (3.6, -11.0), (6.0, -10.0), (8.4, -8.8), (10.4, -6.0)]
        cel(draw, Poly([(hx + d * u, y + w) for (u, w) in crest]), SURF, sh=(0.5, 0.5), tone=SURF_SHADE,
            line_color=ink(HAIR), lw=0.8)
        star(draw, hx - d * 8.4, hy - 2.4, 2.8, CORAL, line=ink(CORAL))
        return
    x, y = hx, hy - ry + 1.6
    crest = [(-5.8, 1.4), (-5.2, -1.4), (-4.2, -2.8), (-2.5, -0.8), (0.0, -6.2), (2.5, -0.8), (4.2, -2.8),
             (5.2, -1.4), (5.8, 1.4), (3.0, 2.1), (0.0, 2.4), (-3.0, 2.1)]
    cel(draw, Poly([(x + u, y + w) for (u, w) in crest]), SURF, sh=(0.6, 0.6), tone=SURF_SHADE,
        line_color=ink(HAIR), lw=0.8)
    cel(draw, Ell(x, y - 0.2, 1.2, 1.2), ORB, sh=None, lw=0.6)
    # pinned on her right, the side away from the trident
    star(draw, hx - 8.4, hy - 3.6, 2.8, CORAL, line=ink(CORAL))


def _overdress(r, draw):
    """The white priestess overdress down the front, a wave cut along its hem,
    stopping short of the surf so the blue shows between."""
    cx, d = r.cx, r.d
    top, bot = r.sh_y - 2.2, r.base_y - 8.6
    if d:
        front = Poly([(cx + d * 0.4, top), (cx + d * 4.2, top + 1.6), (cx + d * 5.8, bot),
                      (cx + d * 3.2, bot + 1.2), (cx + d * 0.9, bot + 0.2)])
    else:
        hem = bez((cx + 6.9, bot), (cx + 3.4, bot + 3.0), (cx, bot + 0.2), 5) + \
            bez((cx, bot + 0.2), (cx - 3.4, bot + 3.0), (cx - 6.9, bot), 5)[1:]
        front = Poly([(cx - 4.6, top), (cx + 4.6, top)] + hem)
    cel(draw, front, ROBE, sh=(1.2, 0.8), tone=(200, 220, 240))


def _surf(r, draw, robe):
    """A band of foam along the hem: one scalloped mass, inside the robe's edge."""
    cx = r.cx
    y = r.base_y - 2.6
    parts = [RRect(cx - 14.0, y - 1.0, cx + 14.0, y + 1.2, 0.4)]
    parts += [Ell(cx + i * 3.6 + r.ph * 0.8, y - 0.9, 1.9, 1.9) for i in range(-4, 5)]
    clipped(draw, robe, lambda l: blob(l, parts, SURF, sh=None, line=False))
    robe.draw(draw, fill=None, outline=ink(ROBE_BLUE), width=1.0)


def draw_tidecaller(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=0.95)
    rs = rig(ox, oy, direction, frame, build=0.95, step=0)    # the trident arm does not swing
    d = r.d
    # The trident is in her left hand: screen right head-on, screen left from
    # behind. In profile it is the near hand, held back at her side.
    side = (1 if not r.back else -1) if not d else d
    reach, out = (0.9, -7.2) if d else ((0.8, 3.7) if not r.back else (0.0, 1.6))
    grip = hand_at(rs, side, reach, 0.0, out)
    away = -d if d else side
    if not d:
        if not r.back:
            _hair_bell(r, draw)
        else:
            _trident(r, draw, grip[0] + 0.2, away)
    if d:
        rig_arms(r, draw, ROBE_BLUE, SKIN, layer="far", cuff=SURF)
    rig_legs(r, draw, ROBE_BLUE, SHOE)
    robe = rig_robe(r, draw, ROBE_BLUE, flare=3.2)
    if not r.back:
        _overdress(r, draw)
    _surf(r, draw, robe)
    rig_belt(r, draw, AQUA, buckle=CORAL)
    if not d:
        rig_arms(r, draw, ROBE_BLUE, SKIN, sides=(-side,), cuff=SURF, hands=r.back)
    rig_arms(rs, draw, ROBE_BLUE, SKIN, sides=(side,), cuff=SURF, hands=False, reach=reach, out=out)
    if r.back:
        rig_hand(rs, draw, side, SKIN, reach, out=out)
        head_skull(r, draw, SKIN)
        _hair_bell(r, draw)
    elif d:
        rig_head(r, draw, SKIN, eye_color=IRIS, expression="smile", ears=False, brows=False)
        _hair_side(r, draw)
    else:
        rig_head(r, draw, SKIN, hair=HAIR, style="long", eye_color=IRIS, expression="smile")
    _tiara(r, draw)
    if not r.back:
        _trident(r, draw, grip[0] + 0.2, away)
        if not d:
            rig_hand(r, draw, -side, SKIN)
        rig_hand(rs, draw, side, SKIN, reach, out=out)


def main():
    generate_character("tidecaller", draw_func=draw_tidecaller)


if __name__ == "__main__":
    main()
