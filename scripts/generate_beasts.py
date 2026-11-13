#!/usr/bin/env python3
"""Beast/Nature character sprite generators (IDs 72-86).

Fifteen creatures, drawn the way MapleStory draws monsters: chibi, round,
big-headed and big-eyed, fierce-cute rather than realistic. Every one has a
body of its own rather than the humanoid rig, and a silhouette nothing else in
the roster shares. Quadrupeds are drawn coming at the camera head-on (head
biggest, body falling away behind) and walking in profile; the birds are
kept apart by species -- the Raptor is a bald eagle, the Hawk a slate-blue
falcon, the Phoenix is made of fire.
"""

import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from sprite_base import (
    DOWN, UP, LEFT, RIGHT, FIRE, TOOTH, Ell, Limb, Poly, RRect, arc_pts, blob, bolt, cel, cloud,
    creature_setup as _setup, crystal, eye_pair as _eyes, flame, flame_pts, generate_character, ink,
    leaf, lit, mix, ms_eye, ms_mouth, draw_paw as _paw, quad_legs as _quad_legs, rot_pts as _rot, shade,
    sparkle, star, stroke, xform,
)

VOID = (34, 26, 40)


# ===================================================================
# WOLF (72) -- a grey timber wolf: a dark mantle from crown to tail, white face, ruff and
# belly, amber eyes, a brush carried low
# ===================================================================

# The plain, natural one of the three wolves (Fenrir is the near-black monster with the
# spiked mane, Cerberus has three heads): light, three tones of one grey, no fangs showing.
WOLF = (150, 162, 188)
WOLF_DK = (104, 116, 148)       # the mantle: crown, ear backs, the back, the brush's tip
WOLF_W = (238, 240, 248)
WOLF_LT = (206, 212, 230)       # the cheeks and their ruff: between the grey and the muzzle's white
WOLF_NOSE = (52, 46, 60)
WOLF_EYE = (252, 188, 44)

from sprite_base import bez as _bez  # noqa: E402  (the kit's curve; not in this file's shared import list)


class _CoatShade(object):
    """A region for `blob` and `cel`: the shadow band of a mass (the part of `shapes` that a copy
    moved up and left by `sh` leaves uncovered) where it crosses `mark`."""

    def __init__(self, shapes, sh, mark):
        self.shapes, self.sh, self.mark = shapes, sh, mark

    def draw(self, d, fill=None, outline=None, width=1):
        band, clip = d.sub(), d.sub()
        for s in self.shapes:
            s.draw(band, fill=fill)
        for s in self.shapes:
            s.shift(-self.sh[0], -self.sh[1]).draw(band, fill=(0, 0, 0, 0))
        self.mark.draw(clip, fill=fill)
        d.merge(band, clip=clip)


def _coat(shapes, marks, sh=(1.2, 1.0)):
    """Regions for a mass of `shapes` shaded by `sh`: each (shape, colour) marking of its coat, and
    that colour's own shadow tone where the mass's shadow band crosses it. (A region is laid flat
    over the shading, so a marking on the shadow side would otherwise come out unshaded.)"""
    out = []
    for mark, col in marks:
        out += [(mark, col), (_CoatShade(shapes, sh, mark), shade(col))]
    return out


def _wolf_paw(draw, x, y, col, d=0, r=2.5, toes=False):
    """A paw flat on the ground. No claws: three white ones hung under the ground line, and at game
    size were a row of teeth."""
    cel(draw, Ell(x + d * 0.9, y, r, r * 0.62), col, sh=(0.0, 0.6))
    if toes:
        for k in (-0.9, 0.9):
            stroke(draw, [(x + k, y - 0.2), (x + k, y + r * 0.45)], 0.5, ink(col))


def _wolf_tail(draw, root, bend, tip, under=1.0):
    """The brush: a curve from the rump pulled toward `bend`, thickest past its middle and pointed,
    a tuft of fur standing off its underside (`under` is which side that is), the tip dark."""
    pts = _bez(root, bend, tip, 7)
    shapes = [Limb(pts, [2.0, 2.7, 3.3, 3.6, 3.5, 3.0, 2.0, 0.4], cap=False)]
    (ax, ay), (bx, by), (ex, ey) = pts[3], pts[4], pts[6]
    ln = math.hypot(ex - ax, ey - ay) or 1.0
    tx, ty = (ex - ax) / ln, (ey - ay) / ln
    nx, ny = -ty * under, tx * under
    shapes.append(Poly([(ax + nx * 2.2, ay + ny * 2.2), (bx + nx * 5.0 + tx * 2.4, by + ny * 5.0 + ty * 2.4), (ex + nx * 1.0, ey + ny * 1.0)]))
    blob(draw, shapes, WOLF, sh=(0.8, 0.8), regions=_coat(shapes, [(Ell(tip[0], tip[1], 4.6, 4.6), WOLF_DK)], (0.8, 0.8)))


def _wolf_head(draw, hx, hy, d, frame, back=False):
    # ears: tall and set wide apart, dark-backed
    for s in ((-1, 1) if not d else (-d, d)):
        near = not d or s == -d
        ex = hx + s * 6.2 if not d else hx - d * 1.6 + s * 2.4
        lean = s * 1.2 if not d else -d * 1.4
        cel(draw, Poly([(ex - 3.1, hy - 5.2), (ex + lean, hy - 13.8), (ex + 3.1, hy - 5.2)]), WOLF_DK if near else shade(WOLF_DK, 0.6),
            sh=(0.5, 0.5))
        if not back:
            Poly([(ex - 1.4, hy - 6.4), (ex + lean * 0.7, hy - 11.2), (ex + 1.4, hy - 6.4)]).draw(draw, fill=(236, 186, 190))
    skull = [Ell(hx, hy, 9.8 if not d else 8.8, 8.6)]
    # the cheek ruff: two tufts a side, which is what makes a wolf's head wider than a dog's
    for s in ((-1, 1) if not d else (-d,)):
        skull.append(Poly([(hx + s * 7.4, hy - 1.6), (hx + s * 12.4, hy + 1.8), (hx + s * 8.6, hy + 4.0), (hx + s * 9.4, hy + 7.0),
                           (hx + s * 5.0, hy + 7.4)]))
    if back:
        # the mantle down the back of the head, the grey of the cheeks either side of it
        cap = Poly(arc_pts(hx, hy - 3.0, 6.8, 6.6, 180, 360, 14) + [(hx + 5.6, hy + 3.0), (hx + 4.4, hy + 10.0), (hx - 4.4, hy + 10.0), (hx - 5.6, hy + 3.0)])
        blob(draw, skull, WOLF, sh=(1.2, 1.0), regions=_coat(skull, [(cap, WOLF_DK)]))
        return
    if d:
        skull.append(Ell(hx + d * 7.8, hy + 2.6, 5.8, 3.6))
        # the mantle's edge is the brow: it comes down to a point between the eyes
        cap = Poly([(hx - d * 12.0, hy - 12.0), (hx + d * 12.0, hy - 12.0), (hx + d * 12.0, hy - 3.6), (hx + d * 8.4, hy - 3.1),
                    (hx + d * 4.9, hy - 1.5), (hx + d * 0.3, hy - 3.1), (hx - d * 12.0, hy - 5.4)])
        jaw = Poly([(hx - d * 3.0, hy + 3.8), (hx + d * 3.4, hy + 1.9), (hx + d * 15.0, hy + 0.5), (hx + d * 15.0, hy + 12.0), (hx - d * 3.0, hy + 12.0)])
        blob(draw, skull, WOLF, sh=(1.2, 1.0), regions=_coat(skull, [(cap, WOLF_DK), (jaw, WOLF_W)]))
        cel(draw, Ell(hx + d * 12.8, hy + 1.2, 1.9, 1.6), WOLF_NOSE, sh=None, line=False)
        stroke(draw, [(hx + d * 5.2, hy + 4.4), (hx + d * 8.8, hy + 5.0), (hx + d * 11.8, hy + 4.0)], 0.7, WOLF_NOSE)
        _eyes(draw, hx + d * 2.6, hx + d * 6.6, hy - 1.6, d, WOLF_EYE, skin=WOLF_DK, w=3.8, h=4.4)
        return
    cap = Poly([(hx - 12.0, hy - 12.0), (hx + 12.0, hy - 12.0), (hx + 12.0, hy - 4.6), (hx + 7.2, hy - 3.1), (hx + 2.3, hy - 1.6), (hx, hy - 0.2),
                (hx - 2.3, hy - 1.6), (hx - 7.2, hy - 3.1), (hx - 12.0, hy - 4.6)])
    cheeks = Poly([(hx - 14.0, hy + 0.2), (hx - 7.0, hy + 1.2), (hx, hy + 2.6), (hx + 7.0, hy + 1.2), (hx + 14.0, hy + 0.2), (hx + 14.0, hy + 12.0),
                   (hx - 14.0, hy + 12.0)])
    blob(draw, skull, WOLF, sh=(1.2, 1.0), regions=_coat(skull, [(cap, WOLF_DK), (cheeks, WOLF_LT)]))
    cel(draw, Ell(hx, hy + 3.8, 5.2, 3.9), WOLF_W, sh=(0.6, 0.6), line_color=mix(ink(WOLF_W), WOLF_W, 0.3))
    cel(draw, Ell(hx, hy + 1.6, 2.3, 1.7), WOLF_NOSE, sh=None, line=False)
    Ell(hx - 0.7, hy + 1.0, 0.6, 0.4).draw(draw, fill=(150, 150, 170))
    stroke(draw, [(hx - 2.6, hy + 4.8), (hx, hy + 5.6), (hx + 2.6, hy + 4.8)], 0.7, WOLF_NOSE)
    _eyes(draw, hx - 4.6, hx + 4.6, hy - 1.8, 0, WOLF_EYE, skin=WOLF_DK, w=3.8, h=4.6)


def draw_wolf(draw, ox, oy, direction, frame):
    d, back, base, cx, ph = _setup(ox, oy, direction, frame)
    wag = [0.0, 1.0, 0.0, -1.0][frame]
    if d:
        # trotting in profile: the brush carried low behind, a deep chest and a tucked belly, the
        # haunch over a hind leg bent at the hock, the diagonal pairs of legs moving together
        _wolf_tail(draw, (cx - d * 8.8, base - 15.2), (cx - d * 17.6, base - 15.6 + wag * 0.5), (cx - d * 18.2, base - 4.4 + wag * 1.6), under=d)
        for near in (False, True):
            # the far pair stand a little ahead and higher up the screen, so all four show at a stand
            off, up = (0.0, 0.0) if near else (1.6, 0.8)
            col = WOLF if near else shade(WOLF, 0.7)
            for (lx, phase, hind) in ((5.6, 1.0, False), (-6.8, -1.0, True)):
                sw = ph * phase * (1.0 if near else -1.0) * 3.0
                lift = 1.2 if sw < 0 else 0.0
                x0 = cx + d * (lx + off)
                x1 = x0 + d * sw
                fy = base - up - lift
                if hind:
                    pts, w = [(x0, base - 10.0), (x0 - d * (1.6 - sw * 0.3), base - 5.6 - up), (x1 - d * 0.2, fy - 1.8)], [2.6, 1.9, 1.6]
                else:
                    pts, w = [(x0, base - 11.0), (x0 + d * sw * 0.45, base - 6.0 - up), (x1, fy - 1.8)], [2.3, 2.0, 1.7]
                cel(draw, Limb(pts, w), col, sh=(0.6, 0.0))
                _wolf_paw(draw, x1, fy - 1.1, WOLF_W if near else shade(WOLF_W, 1.0), d, r=2.4)
        # barrel, chest, haunch and the ruff's tuft under the throat, as one mass
        body = [Ell(cx - d * 1.4, base - 14.0, 9.2, 5.4), Ell(cx + d * 5.2, base - 13.8, 5.8, 6.6), Ell(cx - d * 6.6, base - 12.6, 5.0, 6.2),
                Poly([(cx + d * 8.4, base - 16.0), (cx + d * 11.8, base - 12.2), (cx + d * 9.2, base - 11.4), (cx + d * 9.8, base - 8.4),
                      (cx + d * 5.6, base - 7.8)])]
        saddle = Poly([(cx - d * 14.0, base - 22.0), (cx + d * 12.0, base - 22.0), (cx + d * 12.0, base - 17.6), (cx + d * 4.0, base - 16.6),
                       (cx - d * 4.0, base - 16.2), (cx - d * 9.0, base - 14.8), (cx - d * 14.0, base - 12.0)])
        under = Poly([(cx + d * 13.0, base - 14.5), (cx + d * 7.0, base - 12.0), (cx + d * 1.0, base - 10.4), (cx - d * 4.6, base - 9.8),
                      (cx - d * 4.6, base - 4.0), (cx + d * 13.0, base - 4.0)])
        blob(draw, body, WOLF, sh=(1.2, 1.2), regions=_coat(body, [(saddle, WOLF_DK), (under, WOLF_W)], (1.2, 1.2)))
        _wolf_head(draw, cx + d * 8.6, base - 23.2, d, frame)
        return
    if back:
        # walking away: the rump and the hanging brush nearest, the mantle running from the crown
        # down the back to the root of the tail
        for s in (-1, 1):
            lift = 1.2 if ph * s < 0 else 0.0
            cel(draw, Limb([(cx + s * 5.6, base - 9.0), (cx + s * 5.9, base - 2.0 - lift)], [2.5, 2.1]), WOLF, sh=(0.6, 0.0))
            _wolf_paw(draw, cx + s * 5.9, base - 1.5 - lift, WOLF_W, r=2.6)
        body = [Ell(cx, base - 13.6, 8.6, 7.0), Ell(cx - 5.4, base - 10.4, 4.4, 5.2), Ell(cx + 5.4, base - 10.4, 4.4, 5.2)]
        saddle = Poly([(cx - 6.6, base - 22.0), (cx + 6.6, base - 22.0), (cx + 5.6, base - 15.0), (cx + 2.6, base - 10.6), (cx - 2.6, base - 10.6),
                       (cx - 5.6, base - 15.0)])
        blob(draw, body, WOLF, sh=(1.2, 1.2), regions=_coat(body, [(saddle, WOLF_DK)], (1.2, 1.2)))
        _wolf_head(draw, cx, base - 25.0, 0, frame, back=True)
        _wolf_tail(draw, (cx, base - 13.0), (cx + wag * 1.0, base - 8.0), (cx + wag * 2.2, base - 1.4))
        return
    # coming at the camera: the head biggest, the chest and its white ruff under it, the forelegs
    # close together; the haunches and hind legs beyond stand higher up the screen and darker, and
    # the brush hangs out to one side
    _wolf_tail(draw, (cx + 5.0, base - 12.6), (cx + 14.6, base - 13.2), (cx + 13.4 + wag * 0.6, base - 2.8 + wag * 1.2), under=-1.0)
    hind = shade(WOLF, 0.7)
    for s in (-1, 1):
        lift = 1.0 if ph * s > 0 else 0.0
        cel(draw, Limb([(cx + s * 6.6, base - 8.0), (cx + s * 7.0, base - 4.6 - lift)], [2.0, 1.7]), hind, sh=None)
        _wolf_paw(draw, cx + s * 7.0, base - 3.9 - lift, shade(WOLF_W, 1.0), r=2.1)
        cel(draw, Ell(cx + s * 6.0, base - 10.4, 3.6, 4.8), hind, sh=(0.6, 0.6))
    cel(draw, Ell(cx, base - 12.4, 7.6, 6.4), WOLF, sh=(1.2, 1.0))
    for s in (-1, 1):
        fwd = ph * s
        fy = base - (1.4 if fwd < 0 else 0.0) + (0.4 if fwd > 0 else 0.0)
        cel(draw, Limb([(cx + s * 3.8, base - 12.0), (cx + s * 3.7, fy - 2.0)], [2.5, 2.1]), WOLF, sh=(0.6, 0.0))
        _wolf_paw(draw, cx + s * 3.7, fy - 1.5, WOLF_W, r=2.7, toes=True)
    blob(draw, [Ell(cx, base - 15.4, 6.6, 4.4),
                Poly([(cx - 5.8, base - 14.0), (cx - 3.8, base - 9.8), (cx - 1.9, base - 12.0), (cx, base - 8.0), (cx + 1.9, base - 12.0),
                      (cx + 3.8, base - 9.8), (cx + 5.8, base - 14.0)])], WOLF_W, sh=(0.6, 0.8))
    _wolf_head(draw, cx, base - 25.0, 0, frame)


# ===================================================================
# SERPENT (73) -- a coiled cobra: a wide cream-faced hood, eye marks on its back, forked tongue
# ===================================================================

SNAKE = (84, 176, 84)
SNAKE_DK = (40, 104, 62)
SNAKE_BELLY = (236, 222, 132)
SNAKE_BAND = (232, 204, 88)
SNAKE_EYE = (250, 214, 60)
SNAKE_MOUTH = (150, 40, 60)
SNAKE_TONGUE = (236, 64, 86)
SNAKE_LASH = (24, 44, 32)

from sprite_base import bez as _bez  # noqa: E402  (the kit's curve; not in this file's shared import list)


def _snake_coils(draw, cx, base, d, back, frame):
    """The pile it rides on: three loops, the widest on the ground, cream-bellied. As it moves the
    loops shift against each other; from behind, where its banded back shows, the bands slide along."""
    for i, (u, v, rx, ry) in enumerate(((0.0, -3.8, 12.0, 4.2), (-0.8, -7.8, 9.4, 3.8), (0.6, -11.2, 6.8, 3.2))):
        turn = 1.0 if i % 2 == 0 else -1.0
        x, y = cx + u - d * 1.0 + [0.0, 0.9, 0.0, -0.9][frame] * turn, base + v
        regions = []
        if back:
            off = [0.0, 1.9, 0.0, -1.9][frame] * turn + (2.8 if i % 2 else 0.0)
            for k in range(-3, 4):
                bx = x + k * 5.6 + off
                regions.append((Poly([(bx - 1.2, y - ry - 1.0), (bx + 1.2, y - ry - 1.0), (bx + 1.8, y + ry + 1.0), (bx - 0.6, y + ry + 1.0)]),
                                SNAKE_BAND))
        if not back:
            regions.append((Poly(arc_pts(x, y, rx - 1.2, ry - 0.9, 12, 168, 12)), SNAKE_BELLY))
        cel(draw, Ell(x, y, rx, ry), SNAKE, sh=(1.2, 1.0), regions=regions)


def draw_serpent(draw, ox, oy, direction, frame):
    d, back, base, cx, ph = _setup(ox, oy, direction, frame, bob=[0, 0, 0, 0])
    sway = [0.0, 1.6, 0.0, -1.6][frame]
    # the tail tip curling out from behind the pile
    tip, k = (cx + 10.0, 1.0) if not d else (cx - d * 10.6, -d)
    cel(draw, Limb([(tip - k * 3.4, base - 2.8), (tip, base - 3.8), (tip + k * 2.6, base - 7.0 + sway)], [2.1, 1.5, 0.45]), SNAKE, sh=(0.4, 0.4))
    _snake_coils(draw, cx, base, d, back, frame)
    if not d:
        hx, hy = cx + sway * 0.7, base - 30.0
        spine = [(cx, base - 11.5), (cx + sway * 0.3, base - 17.0), (hx, base - 22.0)]
        neck = Limb(spine, [4.2, 3.8, 3.6])
        # the hood: a wide spade, the one shape nothing else in the roster has
        hood = Poly(arc_pts(hx, hy + 2.0, 13.0, 8.6, 180, 360, 24) + _bez((hx + 13.0, hy + 2.0), (hx + 12.0, hy + 11.5), (hx + 3.6, hy + 17.6))[1:]
                    + _bez((hx - 3.6, hy + 17.6), (hx - 12.0, hy + 11.5), (hx - 13.0, hy + 2.0))[:-1])
        head = Ell(hx, hy - 0.6, 7.6, 6.0)
        if back:
            # from behind: the head beyond the hood, and on the hood's back the cobra's false eyes
            cel(draw, head, SNAKE, sh=(1.0, 1.0))
            marks = [(Limb([(hx - w, y - w * 0.42), (hx, y), (hx + w, y - w * 0.42)], [1.25, 1.25, 1.25]), SNAKE_BAND)
                     for (y, w) in ((hy + 1.6, 9.6), (hy + 7.0, 8.6), (hy + 12.4, 5.6))]
            blob(draw, [neck, hood], SNAKE, sh=(1.2, 1.0), regions=marks)
            return
        blob(draw, [neck, hood], SNAKE, sh=(1.2, 1.0),
             regions=[(Limb(spine, [2.5, 2.3, 2.3]), SNAKE_BELLY), (hood.scaled(0.78, hx, hy + 5.0), SNAKE_BELLY)])
        cel(draw, head, SNAKE, sh=(1.0, 1.0), hi=(0.6, 0.6))
        _eyes(draw, hx - 3.5, hx + 3.5, hy - 1.6, 0, SNAKE_EYE, skin=SNAKE, w=4.0, h=4.6, lash=SNAKE_LASH)
        for s in (-1, 1):
            Ell(hx + s * 1.1, hy + 1.9, 0.5, 0.4).draw(draw, fill=SNAKE_DK)
        cel(draw, Poly([(hx - 4.2, hy + 2.8), (hx + 4.2, hy + 2.8)] + arc_pts(hx, hy + 2.8, 4.2, 3.0, 0, 180, 10)[1:-1]), SNAKE_MOUTH,
            sh=None, line_color=SNAKE_DK, lw=0.5)
        for s in (-1, 1):
            Poly([(hx + s * 2.5 - 0.9, hy + 2.9), (hx + s * 2.5 + 0.9, hy + 2.9), (hx + s * 2.4, hy + 5.6)]).draw(draw, fill=TOOTH)
        if frame % 2 == 0:
            stroke(draw, [(hx, hy + 4.6), (hx, hy + 8.0), (hx - 1.5, hy + 10.2)], 1.0, SNAKE_TONGUE)
            stroke(draw, [(hx, hy + 8.0), (hx + 1.5, hy + 10.2)], 1.0, SNAKE_TONGUE)
        return
    # profile: the neck rises in a curve, the hood stands behind it, the head is a wedge
    hx, hy = cx + d * (3.4 + sway * 0.8), base - 31.0
    spine = [(cx - d * 1.0, base - 10.5), (cx - d * 2.6, base - 17.0), (cx - d * 1.4, base - 23.5), (hx - d * 2.0, hy + 2.6)]
    flange = Poly(_bez((hx - d * 1.0, hy - 5.6), (hx - d * 12.0, hy - 3.6), (hx - d * 10.2, hy + 5.6))
                  + _bez((hx - d * 10.2, hy + 5.6), (hx - d * 9.2, hy + 12.6), (cx - d * 3.2, base - 14.4))[1:]
                  + [(cx + d * 0.6, base - 16.0), (hx + d * 1.0, hy + 2.0)])
    cel(draw, flange, SNAKE, sh=(0.8, 0.8), regions=[(flange.scaled(0.7, hx - d * 4.6, hy + 4.6), SNAKE_BELLY)])
    cel(draw, Limb(spine, [4.2, 3.8, 3.5, 3.4]), SNAKE, sh=(0.8, 0.6),
        regions=[(Limb([(x + d * 3.0, y) for (x, y) in spine], [4.2, 3.8, 3.5, 3.4]), SNAKE_BELLY)])
    blob(draw, [Ell(hx, hy, 6.6, 5.4), Ell(hx + d * 5.0, hy + 1.4, 4.6, 3.3)], SNAKE, sh=(1.0, 1.0),
         regions=[(Ell(hx + d * 4.2, hy + 4.6, 7.0, 2.3), SNAKE_BELLY)])
    stroke(draw, [(hx + d * 9.2, hy + 2.3), (hx + d * 5.0, hy + 3.0), (hx + d * 0.8, hy + 2.4)], 0.7, SNAKE_DK)
    Ell(hx + d * 8.2, hy - 0.2, 0.5, 0.4).draw(draw, fill=SNAKE_DK)
    cel(draw, Poly([(hx + d * 5.2, hy + 2.9), (hx + d * 7.2, hy + 2.6), (hx + d * 6.3, hy + 5.6)]), TOOTH, sh=None, lw=0.35)
    ms_eye(draw, hx + d * 2.6, hy - 1.2, 3.8, 4.4, SNAKE_EYE, (float(d), 0.0), "sharp", skin=SNAKE, side=-d, lash=SNAKE_LASH)
    if frame % 2 == 0:
        tx, ty = hx + d * 9.4, hy + 2.2
        stroke(draw, [(tx, ty), (tx + d * 2.6, ty + 1.2), (tx + d * 4.4, ty + 0.2)], 1.0, SNAKE_TONGUE)
        stroke(draw, [(tx + d * 2.6, ty + 1.2), (tx + d * 4.2, ty + 2.8)], 1.0, SNAKE_TONGUE)


# ===================================================================
# SPIDER (74) -- a round violet body, a red hourglass, big eyes, eight banded legs
# ===================================================================

SPI = (84, 64, 104)
SPI_DK = (60, 44, 80)
SPI_LT = (150, 122, 170)
SPI_RED = (226, 44, 60)
SPI_EYE = (255, 90, 90)

# Four legs a side as nested arches, front (nearest the body's middle) to rear: hip, raised knee,
# foot, each further out than the last so no two cross. Before, they rose to one knee height and
# fell side by side, and in game eight legs were one hatched curtain.
_SPI_FAN = (((4.6, -8.0), (8.8, -16.0), (7.6, 0.2)), ((6.0, -10.5), (13.6, -21.5), (12.9, -1.2)),
            ((6.0, -13.0), (18.0, -25.0), (18.0, -3.6)), ((5.0, -15.5), (21.6, -22.0), (22.6, -6.8)))
# In profile: two reaching ahead of the head, two trailing behind the abdomen
_SPI_SIDE = (((5.0, -10.0), (17.6, -19.0), (21.4, -0.6)), ((4.0, -10.5), (12.8, -22.5), (16.2, 0.2)),
             ((0.0, -11.0), (-15.4, -24.0), (-18.8, 0.2)), ((-1.0, -10.5), (-19.8, -20.0), (-22.4, -0.6)))


def _spi_leg(draw, hip, knee, foot, col, w=1.35):
    """One leg as one mass: a thigh up to the knee, a shin down to a pointed foot, and a pale band
    at the knee, which is what lets each leg be told from the next."""
    mid = ((knee[0] + foot[0]) / 2.0, (knee[1] + foot[1]) / 2.0)
    parts = [Limb([hip, knee], [w, w * 0.95]), Limb([knee, mid, foot], [w * 0.95, w * 0.8, 0.5], cap0=True)]
    blob(draw, parts, col, sh=None, lw=0.5, regions=[(Ell(knee[0], knee[1], w * 1.6, w * 1.6), SPI_LT)])


def _spi_mark(draw, x, y, k=1.0):
    """The red hourglass."""
    cel(draw, Poly([(x - 2.8 * k, y - 4.0 * k), (x + 2.8 * k, y - 4.0 * k), (x + 0.6 * k, y), (x + 2.8 * k, y + 4.0 * k),
                    (x - 2.8 * k, y + 4.0 * k), (x - 0.6 * k, y)]), SPI_RED, sh=None, lw=0.6)


def draw_spider(draw, ox, oy, direction, frame):
    d, back, base, cx, ph = _setup(ox, oy, direction, frame)
    ground = oy + 54.0      # the feet stay on the ground while the body bobs
    bob = base - ground

    def stepping(i, k):
        # the walk: two sets of four legs, one in the air on each stride
        return (frame == 1 and (i + k) % 2 == 0) or (frame == 3 and (i + k) % 2 == 1)

    if d:
        for near in (0, 1):
            for i in (0, 3, 1, 2):
                hip, knee, foot = _SPI_SIDE[i]
                sg = 1.0 if knee[0] > 0 else -1.0
                kx, ky, fx, fy = knee[0], knee[1], foot[0], foot[1]
                if not near:
                    # the far side's legs: inside the near ones, higher up the screen, darker
                    kx, ky, fx, fy = kx - sg * 2.6, ky + 1.8, fx - sg * 3.0, fy - 2.6
                if stepping(i, near):
                    kx, ky, fx, fy = kx + 0.9, ky - 1.4, fx + 1.8, fy - 2.4
                elif frame % 2:
                    fx -= 1.0
                _spi_leg(draw, (cx + d * hip[0], ground + hip[1] + bob), (cx + d * kx, ground + ky), (cx + d * fx, ground + fy),
                         SPI_DK if near else shade(SPI_DK, 0.9), 1.35 if near else 1.05)
    else:
        for i in (3, 2, 1, 0):
            hip, knee, foot = _SPI_FAN[i]
            for s in (-1, 1):
                kx, ky, fx, fy = knee[0], knee[1], foot[0], foot[1]
                if stepping(i, s > 0):
                    kx, ky, fx, fy = kx + 0.5, ky - 1.5, fx + 0.8, fy - 2.6
                _spi_leg(draw, (cx + s * hip[0], ground + hip[1] + bob - (4.0 if back else 0.0)), (cx + s * kx, ground + ky),
                         (cx + s * fx, ground + fy), SPI_DK if i < 2 else shade(SPI_DK, 0.7))
    head_col = lit(SPI, 0.5)
    lash = (44, 24, 60)
    if back:
        # walking away: the back of the head beyond, the abdomen and its hourglass nearest
        cel(draw, Ell(cx, base - 21.5, 6.6, 5.2), head_col, sh=(0.8, 0.8))
        cel(draw, Ell(cx, base - 2.8, 1.8, 1.5), SPI_DK, sh=None)
        cel(draw, Ell(cx, base - 12.0, 10.8, 9.6), SPI, sh=(1.4, 1.2), hi=(0.9, 0.9))
        _spi_mark(draw, cx, base - 12.4, 1.0)
        return
    if d:
        ax, ay = cx - d * 6.6, base - 12.0
        cel(draw, Ell(ax, ay, 9.2, 8.4), SPI, sh=(1.4, 1.2), hi=(0.9, 0.9))
        _spi_mark(draw, ax - d * 1.0, ay - 0.4, 0.85)
        hx, hy = cx + d * 5.8, base - 8.6
        cel(draw, Ell(hx, hy, 6.6, 6.4), head_col, sh=(1.0, 1.0))
        cel(draw, Ell(hx + d * 4.4, hy + 4.2, 1.9, 1.9), SPI_DK, sh=None, lw=0.5)
        cel(draw, Poly([(hx + d * 3.6, hy + 5.0), (hx + d * 5.4, hy + 5.0), (hx + d * 4.9, hy + 7.8)]), TOOTH, sh=None, lw=0.5)
        _eyes(draw, hx + d * 1.2, hx + d * 4.7, hy - 0.8, d, SPI_EYE, mood="bright", skin=head_col, w=4.4, h=5.2, lash=lash)
        for (u, v, r) in ((5.6, -4.3, 0.95), (2.6, -5.0, 1.05)):
            Ell(hx + d * u, hy + v, r, r).draw(draw, fill=SPI_RED)
            Ell(hx + d * u - 0.3, hy + v - 0.3, 0.35, 0.35).draw(draw, fill=(255, 220, 220))
        return
    # head-on: the abdomen rides behind and above, its mark showing over the head
    cel(draw, Ell(cx, base - 17.5, 10.4, 9.2), SPI, sh=(1.4, 1.2), hi=(0.9, 0.9))
    _spi_mark(draw, cx, base - 22.0, 0.72)
    hx, hy = cx, base - 8.4
    cel(draw, Ell(hx, hy, 8.4, 6.8), head_col, sh=(1.0, 1.0))
    for s in (-1, 1):
        cel(draw, Ell(hx + s * 2.1, hy + 4.6, 1.9, 2.0), SPI_DK, sh=None, lw=0.5)
        cel(draw, Poly([(hx + s * 2.1 - 0.9, hy + 5.4), (hx + s * 2.1 + 0.9, hy + 5.4), (hx + s * 1.5, hy + 8.2)]), TOOTH, sh=None, lw=0.5)
    _eyes(draw, hx - 3.3, hx + 3.3, hy - 0.8, 0, SPI_EYE, mood="bright", skin=head_col, w=4.8, h=5.4, lash=lash)
    for (u, v, r) in ((6.5, -2.4, 1.15), (4.9, -5.2, 0.95)):
        for s in (-1, 1):
            Ell(hx + s * u, hy + v, r, r).draw(draw, fill=SPI_RED)
            Ell(hx + s * u - 0.3, hy + v - 0.3, 0.35, 0.35).draw(draw, fill=(255, 220, 220))


# ===================================================================
# BEAR (75) -- a brown brawler on its hind legs, claws out
# ===================================================================

BEAR = (144, 94, 56)
BEAR_LT = (222, 186, 136)
BEAR_NOSE = (52, 40, 40)


def _bear_claws(draw, px, py, pts, col=TOOTH):
    """Three claws rooted behind a paw at (px, py): each of `pts` is (root offset, tip offset).
    Drawn before the paw, so they come out of it instead of floating over it."""
    for (rx, ry), (tx, ty) in pts:
        nx, ny = -(ty - ry), tx - rx
        ln = math.hypot(nx, ny) or 1.0
        nx, ny = nx / ln * 1.05, ny / ln * 1.05
        # no ink line: a claw this thin would be all line, and it is the ivory that has to show
        Poly([(px + rx + nx, py + ry + ny), (px + rx - nx, py + ry - ny), (px + tx, py + ty)]).draw(draw, fill=col)


def draw_bear(draw, ox, oy, direction, frame):
    d, back, base, cx, ph = _setup(ox, oy, direction, frame)
    bw = 11.0 if not d else 9.4
    pump = [0.0, 1.2, 0.0, -1.2][frame]

    def side_arm(near):
        # in profile both arms reach out in front of the chest, the far one higher: ready to grab
        col = BEAR if near else shade(BEAR, 0.6)
        sx, sy = (cx + d * 1.6, base - 20.0) if near else (cx - d * 0.2, base - 21.6)
        px, py = (cx + d * 9.8, base - 14.4 - pump) if near else (cx + d * 12.8, base - 20.4 + pump)
        _bear_claws(draw, px, py, [((d * 1.4, -1.9 + k * 1.7), (d * 5.1, -1.5 + k * 2.2)) for k in (0, 1, 2)],
                    TOOTH if near else shade(TOOTH, 1.0))
        blob(draw, [Limb([(sx, sy), ((sx + px) / 2.0, (sy + py) / 2.0 + 1.4), (px - d * 1.0, py)], [3.2, 2.8, 2.6]), Ell(px, py, 3.1, 2.9)], col,
             sh=(0.8, 0.6))

    if d:
        # a stub of a tail, and the far arm behind the body
        cel(draw, Ell(cx - d * 9.4, base - 9.6, 2.4, 2.2), BEAR, sh=(0.5, 0.5))
        side_arm(False)
    # legs: short and thick
    for s in ((-1, 1) if not d else (-d, d)):
        near = not d or s == d
        fwd = ph * s if not d else (ph if near else -ph)
        lx = cx + (s * 5.2 if not d else d * fwd * 2.6 + s * 1.4)
        lift = 1.0 if fwd < 0 and not d else 0.0
        col = BEAR if near else shade(BEAR, 0.6)
        cel(draw, Limb([(lx, base - 9.0), (lx, base - 2.4 - lift)], [3.2, 3.0]), col, sh=(0.8, 0.0))
        foot = BEAR_LT if not back else lit(BEAR, 0.4)
        cel(draw, Ell(lx + d * 1.0, base - 1.4 - lift, 3.4, 1.8), foot if near else shade(foot, 0.6), sh=(0.0, 0.5))
    # arms up, claws out, ready to maul: behind the body from the front, over it from behind
    def front_arms():
        for s in (-1, 1):
            sx = cx + s * (bw - 1.6)
            up = [0.0, 1.0, 0.0, 1.0][frame] * (1.0 if s > 0 else 0.4)
            px, py = sx + s * 4.4, base - 21.0 - up
            _bear_claws(draw, px, py, [((k * 1.7, -1.4), (k * 2.4, -5.2 if not back else -4.4)) for k in (-1, 0, 1)])
            blob(draw, [Limb([(sx, base - 20.0), ((sx + px) / 2.0 + s * 1.4, base - 17.0), (px, py)], [3.4, 3.0, 2.8]), Ell(px, py, 3.2, 3.0)], BEAR,
                 sh=(0.8, 0.6))
            if not back:
                cel(draw, Ell(px, py + 0.6, 1.7, 1.5), BEAR_LT, sh=None, line=False)

    # body: a big round barrel with a pale belly
    body = Ell(cx - d * 0.6, base - 15.0, bw, 10.4)
    if back:
        # from behind: the darker hump of the shoulders, and a tuft of a tail
        cel(draw, body, BEAR, sh=(1.6, 1.4), regions=[(Ell(cx, base - 23.0, 9.6, 5.6), shade(BEAR, 0.7))])
        cel(draw, Ell(cx, base - 7.6, 2.8, 2.4), lit(BEAR, 0.9), sh=(0.5, 0.5))
        front_arms()
    elif d:
        cel(draw, body, BEAR, sh=(1.6, 1.4), regions=[(Ell(cx + d * 2.6, base - 13.6, bw * 0.6, 7.0), BEAR_LT)])
        side_arm(True)
    else:
        cel(draw, body, BEAR, sh=(1.6, 1.4), regions=[(Ell(cx, base - 13.6, bw * 0.6, 7.0), BEAR_LT)])
        front_arms()
    # head
    hx, hy = cx + d * 2.6, base - 29.0
    for s in ((-1, 1) if not d else (-d, d)):
        near = not d or s == -d
        ex = hx + s * 7.4 if not d else hx - d * 1.4 + s * 3.4
        cel(draw, Ell(ex, hy - 7.4, 3.2, 3.2), BEAR if near else shade(BEAR, 0.6), sh=(0.5, 0.5))
        if not back:
            Ell(ex, hy - 7.2, 1.6, 1.6).draw(draw, fill=BEAR_LT)
    cel(draw, Ell(hx, hy, 10.4 if not d else 9.4, 9.2), BEAR, sh=(1.2, 1.0), hi=(0.6, 0.6))
    if back:
        return
    if d:
        cel(draw, Ell(hx + d * 7.4, hy + 2.6, 4.6, 3.6), BEAR_LT, sh=(0.0, 0.6))
        cel(draw, Ell(hx + d * 11.0, hy + 1.4, 1.8, 1.4), BEAR_NOSE, sh=None, line=False)
        stroke(draw, [(hx + d * 6.4, hy + 5.0), (hx + d * 9.4, hy + 5.0)], 0.6, BEAR_NOSE)
        _eyes(draw, hx + d * 2.4, hx + d * 6.0, hy - 1.8, d, (60, 40, 30), skin=BEAR, w=3.6, h=4.2)
        return
    cel(draw, Ell(hx, hy + 3.6, 5.2, 4.0), BEAR_LT, sh=(0.6, 0.6))
    cel(draw, Ell(hx, hy + 1.8, 2.2, 1.5), BEAR_NOSE, sh=None, line=False)
    stroke(draw, [(hx - 2.4, hy + 5.0), (hx, hy + 5.8), (hx + 2.4, hy + 5.0)], 0.6, BEAR_NOSE)
    for s in (-1, 1):
        Poly([(hx + s * 1.6 - 0.45, hy + 5.4), (hx + s * 1.6 + 0.45, hy + 5.4), (hx + s * 1.6, hy + 6.8)]).draw(draw, fill=TOOTH)
    _eyes(draw, hx - 4.6, hx + 4.6, hy - 1.6, 0, (70, 44, 30), skin=BEAR, w=3.6, h=4.4)


# ===================================================================
# SCORPION (76) -- pincers forward, a segmented tail arched over its head
# ===================================================================

# A red-orange shell (the old orange was the boardwalk's own colour), and a value plan: the pincers
# a step darker and redder than the shell, the legs and the sting darkest, the tail's bulb lightest,
# so the sting is a dark hook on a pale bulb with the green drop at its point.
SCO = (216, 98, 50)
SCO_LT = (244, 158, 84)
SCO_CLAW = (176, 58, 44)
SCO_DK = (128, 50, 44)
SCO_VENOM = (170, 255, 90)

# Three legs a side as (hip, knee, foot) from the middle of the body on the ground: head-on fanned
# out under the pincers with ground between them, in profile splayed from under the shell.
_SCO_LEGS_FRONT = (((6.6, -7.2), (14.8, -8.4), (17.2, -2.2)), ((6.0, -6.0), (12.0, -7.0), (13.4, -0.8)), ((5.0, -4.8), (8.4, -5.4), (9.2, -0.6)))
_SCO_LEGS_SIDE = (((3.6, -7.4), (6.8, -5.6), (8.8, -0.6)), ((-0.6, -7.4), (0.6, -4.8), (-0.2, -0.6)), ((-4.8, -7.4), (-8.0, -5.6), (-10.2, -0.6)))


def _sco_claw(draw, x, y, ang, k, col, gape=12.0, flip=1.0):
    """One pincer, its wrist at (x, y), pointing along `ang`: a fat hand running out into the fixed
    finger, and the thumb hinged under it, `gape` degrees open."""
    a = math.radians(gape)
    c, s = math.cos(a), math.sin(a)
    thumb = [(2.4 + (u - 2.4) * c - (v - 1.0) * s, 1.0 + (u - 2.4) * s + (v - 1.0) * c)
             for (u, v) in ((1.2, 0.4), (3.6, 2.9), (7.0, 2.9), (9.2, 1.2), (6.4, 1.4), (3.8, 0.8))]
    cel(draw, Poly(xform(thumb, x, y, ang, k, flip)), shade(col, 0.7), sh=None)
    hand = arc_pts(2.2, 0.0, 3.7, 3.3, 60, 300, 12) + [(5.0, -3.4), (8.4, -2.8), (10.0, -0.8), (6.8, -1.2), (4.6, 0.0), (3.6, 1.6)]
    cel(draw, Poly(xform(hand, x, y, ang, k, flip)), col, sh=(0.7, 0.7), hi=(0.5, 0.5))


def _sco_tail(draw, seg, r0, dr, squash=1.0):
    """The tail's segments, a bead between each pair of joints in `seg`, from the root outward."""
    for i in range(len(seg) - 1):
        (ax, ay), (bx, by) = seg[i], seg[i + 1]
        w = r0 + i * dr
        cel(draw, Ell((ax + bx) / 2.0, (ay + by) / 2.0, w, w * squash), SCO if i % 2 == 0 else lit(SCO, 0.5), sh=(0.6, 0.6))


def _sco_sting(draw, bulb, rx, ry, barb, frame, drop=True):
    """The telson: a pale bulb and the dark hook of the sting along `barb`, a drop of venom at its
    point."""
    n = len(barb) - 1
    cel(draw, Limb(barb, [1.9 - 1.75 * (i / float(n)) ** 1.5 for i in range(n + 1)], cap=False), SCO_DK, sh=None)
    cel(draw, Ell(bulb[0], bulb[1], rx, ry), SCO_LT, sh=(0.8, 0.8), hi=(0.5, 0.5))
    if drop:
        (ax, ay), (vx, vy) = barb[-2], barb[-1]
        ln = math.hypot(vx - ax, vy - ay) or 1.0
        vx, vy = vx + (vx - ax) / ln * 0.9, vy + (vy - ay) / ln * 0.9
        cel(draw, Ell(vx, vy, 1.4, 1.5), SCO_VENOM, sh=None, lw=0.5)
        if frame % 2 == 0:
            sparkle(draw, vx + 1.6, vy - 1.4, 1.7, SCO_VENOM)


def draw_scorpion(draw, ox, oy, direction, frame):
    d, back, base, cx, ph = _setup(ox, oy, direction, frame, bob=[0, 0, 0, 0])
    step = [0.0, 1.0, 0.0, -1.0][frame]
    rock = [0.0, -0.7, 0.0, -0.7][frame]        # the shell rides the stride; the feet stay down
    sway = step * 1.5

    def gape(s):
        """Each pincer snaps shut on one stride and gapes on the other."""
        return 13.0 + s * step * 12.0

    def leg(hip, knee, foot, s, k, col):
        sw = k * step
        lift = 1.3 if sw > 0 else 0.0
        pts = [(cx + s * hip[0], base + hip[1] + rock), (cx + s * knee[0] + (d * sw * 1.0 if d else 0.0), base + knee[1] + rock - abs(sw) * 0.8),
               (cx + s * foot[0] + (d * sw * 2.0 if d else s * sw * 0.8), base + foot[1] - lift)]
        cel(draw, Limb(pts, [1.5, 1.25, 0.6]), col, sh=None)

    if d:
        # in profile: the far pincer raised over the head and the near one held low before it, so the
        # face shows between them; the tail a hoop over the back, its sting brought forward
        cx -= d * 2.0
        by = base - 11.0 + rock
        for i, (hip, knee, foot) in enumerate(_SCO_LEGS_SIDE):
            leg((hip[0] + 2.2, hip[1]), (knee[0] + 2.2, knee[1] - 0.8), (foot[0] + 2.2, foot[1] - 1.0), d, -1 if i != 1 else 1, shade(SCO_DK, 0.9))
        far = shade(SCO_CLAW, 0.9)
        wx, wy = cx + d * 15.0, by - 7.6 - step * 0.8
        cel(draw, Limb([(cx + d * 5.6, by - 1.0), (cx + d * 10.6, by - 4.6), (wx, wy)], [2.0, 1.8, 1.7]), far, sh=None)
        _sco_claw(draw, wx, wy, (0 if d > 0 else 180) - d * 22.0, 1.05, far, gape(-1), d)
        seg = [(cx - d * 8.0, by - 1.0), (cx - d * 12.6, by - 6.6), (cx - d * 13.8, by - 13.8), (cx - d * 10.6, by - 20.4),
               (cx - d * 4.6, by - 24.0 + sway * 0.6), (cx + d * 1.2, by - 22.8 + sway)]
        _sco_tail(draw, seg, 3.6, -0.3)
        tx, ty = seg[-1]
        _sco_sting(draw, (tx, ty), 3.6, 3.2, _bez((tx + d * 1.6, ty - 1.0), (tx + d * 7.2, ty - 1.8), (tx + d * 6.4, ty + 4.8), 6), frame)
        # the shell, plated, pale along its underside, and the head-shield before it
        bcx = cx - d * 1.0
        body = Poly(_rot(arc_pts(bcx, by, 9.6, 5.2, 0, 360)[:-1], bcx, by, -d * 4))
        cel(draw, body, SCO, sh=(1.0, 1.0), hi=(0.6, 0.6), regions=[(_Band([body], (0.0, -2.2)), SCO_LT)])
        hx, hy = cx + d * 7.2, by - 1.6
        cel(draw, Ell(hx, hy, 5.4, 5.2), SCO, sh=(0.8, 0.8), hi=(0.5, 0.5))
        _eyes(draw, hx + d * 0.8, hx + d * 3.5, hy - 1.2, d, (60, 30, 30), skin=SCO, w=3.8, h=4.4)
        cel(draw, Ell(hx + d * 3.6, hy + 3.4, 1.7, 1.3), SCO_DK, sh=None, lw=0.5)
        for i, (hip, knee, foot) in enumerate(_SCO_LEGS_SIDE):
            leg(hip, knee, foot, d, 1 if i != 1 else -1, SCO_DK)
        wx, wy = cx + d * 13.6, by + 3.6 + step * 0.8
        cel(draw, Limb([(cx + d * 5.4, by + 1.6), (cx + d * 9.6, by + 4.0), (wx, wy)], [2.0, 1.8, 1.7]), SCO_CLAW, sh=(0.5, 0.5))
        _sco_claw(draw, wx, wy, 0 if d > 0 else 180, 1.1, SCO_CLAW, gape(1), d)
        return

    by = base - 8.0 + rock

    def pincers(col):
        for s in (-1, 1):
            wx, wy = cx + s * 12.0, by - 8.0 - s * step * 1.2
            cel(draw, Limb([(cx + s * 6.0, by), (cx + s * 11.6, by - 2.0), (wx, wy + 1.6)], [2.3, 2.1, 1.9]), col, sh=(0.5, 0.5))
            _sco_claw(draw, wx, wy, -90 + s * 8, 1.35, col, gape(s), -s)

    for i, (hip, knee, foot) in enumerate(_SCO_LEGS_FRONT):
        for s in (-1, 1):
            leg(hip, knee, foot, s, 1 if (i + (s > 0)) % 2 else -1, SCO_DK if i else shade(SCO_DK, 0.9))
    if back:
        # walking away: the pincers beyond, the plated back, and the tail rising off its near end
        # and over, each bead smaller than the last, the sting gone over the top
        pincers(shade(SCO_CLAW, 0.7))
        body = Ell(cx, by + 0.2, 9.4, 7.4)
        cel(draw, body, SCO, sh=(1.2, 1.0), hi=(0.6, 0.6))
        for v in (-3.4, 0.0, 3.4):
            stroke(draw, _bez((cx - 8.6, by + v - 1.0), (cx, by + v + 1.2), (cx + 8.6, by + v - 1.0), 6), 0.8, shade(SCO, 1.6))
        seg = [(cx, by + 4.2), (cx + 1.0, by - 3.0), (cx + 1.8 + sway * 0.4, by - 9.8), (cx + 1.6 + sway * 0.7, by - 16.0), (cx + 0.6 + sway, by - 21.4),
               (cx - 0.4 + sway, by - 25.6)]
        tx, ty = seg[-1]
        _sco_sting(draw, (tx, ty - 0.6), 3.3, 3.0, [(tx - 0.6, ty - 2.0), (tx + 0.2, ty - 5.2), (tx + 2.4, ty - 6.6)], frame, drop=False)
        cel(draw, Ell(tx + 3.0, ty - 7.0, 1.2, 1.2), SCO_VENOM, sh=None, lw=0.5)
        _sco_tail(draw, seg[::-1], 2.9, 0.3)
        return
    # head-on: the tail comes up behind, bows to one side and arcs toward the camera, each bead
    # nearer and so larger than the last; the bulb swings back across and the sting hangs clear of it
    seg = [(cx + 0.6, by - 6.0), (cx + 3.6, by - 11.0), (cx + 5.4 + sway * 0.4, by - 16.6), (cx + 4.8 + sway * 0.7, by - 22.0), (cx + 1.8 + sway, by - 26.6),
           (cx - 2.4 + sway, by - 28.8)]
    _sco_tail(draw, seg, 2.6, 0.36, 0.9)
    tx, ty = seg[-1]
    _sco_sting(draw, (tx - 0.6, ty - 0.2), 4.4, 3.9, _bez((tx - 2.8, ty + 1.4), (tx - 5.2, ty + 5.2), (tx - 2.6, ty + 9.2), 6), frame)
    cel(draw, Ell(cx, by - 1.0, 8.8, 7.4), SCO, sh=(1.2, 1.0), hi=(0.6, 0.6))
    hx, hy = cx, by - 1.8
    cel(draw, Ell(hx, hy, 6.8, 5.4), SCO, sh=(0.8, 0.8), hi=(0.5, 0.5))
    _eyes(draw, hx - 3.1, hx + 3.1, hy - 1.2, 0, (60, 30, 30), skin=SCO, w=3.8, h=4.4)
    for s in (-1, 1):
        cel(draw, Ell(hx + s * 1.5, hy + 3.6, 1.5, 1.3), SCO_DK, sh=None, lw=0.5)
    pincers(SCO_CLAW)


# ===================================================================
# HAWK (77) -- a slate-blue falcon: dark hood, moustache marks, barred chest, pointed wings
# ===================================================================

HAWK = (104, 122, 156)
HAWK_DK = (70, 82, 112)
HAWK_CHEST = (246, 236, 214)
HAWK_BAR = (150, 128, 110)
HAWK_BEAK = (250, 204, 70)
HAWK_EYE = (40, 32, 34)
HAWK_HOOK = (64, 64, 84)


def _hawk_lerp(a, b, t):
    return (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)


def _hawk_wing(draw, shoulder, wrist, tip, inner, bow=0.0):
    """A falcon's wing: long and pointed, going round shoulder, wrist, tip and inner in turn. The
    wrist is rounded, the edge from it to the tip bowed out by `bow`, and the edge back from the tip
    stepped where the flight feathers end. Dark flight feathers, the coverts by the shoulder the
    back's slate (feather lines at this size are noise: the two tones are the wing's whole drawing)."""
    mid = ((shoulder[0] + wrist[0] + tip[0] + inner[0]) / 4.0, (shoulder[1] + wrist[1] + tip[1] + inner[1]) / 4.0)
    turn = _hawk_lerp(wrist, tip, 0.3)
    belly = _hawk_lerp(_hawk_lerp(turn, tip, 0.45), mid, -bow)
    pts = [shoulder] + _bez(_hawk_lerp(shoulder, wrist, 0.6), wrist, turn, 5) + _bez(turn, belly, tip, 6)[1:]
    for k in (1, 2):
        pts.append(_hawk_lerp(_hawk_lerp(tip, inner, k / 3.0 - 0.04), mid, 0.16))
        pts.append(_hawk_lerp(_hawk_lerp(tip, inner, k / 3.0 + 0.06), mid, -0.14))
    pts.append(inner)
    c = _hawk_lerp(_hawk_lerp(shoulder, wrist, 0.5), inner, 0.3)
    r = math.hypot(wrist[0] - shoulder[0], wrist[1] - shoulder[1]) * 0.66
    cel(draw, Poly(pts), HAWK_DK, sh=(0.8, 0.8), regions=[(Ell(c[0], c[1], r, r), HAWK)])


def _hawk_foot(draw, fx, fy, d, col=HAWK_BEAK):
    """A yellow foot: three toes fanned head-on; two forward and one back in profile."""
    toes = ((-2.4, 1.1), (0.0, 1.8), (2.4, 1.1)) if not d else ((d * 3.4, 1.4), (d * 1.4, 1.8), (-d * 2.0, 1.4))
    blob(draw, [Limb([(fx, fy - 0.8), (fx + u, fy + v)], [1.1, 0.7]) for (u, v) in toes], col, sh=None, lw=0.5)


def draw_hawk(draw, ox, oy, direction, frame):
    d, back, base, cx, ph = _setup(ox, oy, direction, frame)
    spread = [0.0, 1.0, 0.0, 1.0][frame]
    if d:
        # profile: upright on its perch, the long wing folded over its back, the tail beyond it
        tail = Poly([(cx - d * 6.4, base - 11.0), (cx - d * 12.8, base - 4.6), (cx - d * 10.6, base - 1.8), (cx - d * 4.6, base - 7.0)])
        cel(draw, tail, HAWK_DK, sh=None,
            regions=[(Poly([(cx - d * 11.2, base - 6.6), (cx - d * 14.0, base - 4.4), (cx - d * 11.4, base - 0.8), (cx - d * 8.8, base - 2.8)]), HAWK_CHEST)])
        for near in (False, True):
            fwd = ph if near else -ph
            lx = cx + d * ((0.8 if near else -1.4) + fwd * 2.6)
            fy = base - 2.0 - (1.2 if fwd < 0 else 0.0)
            col = HAWK_BEAK if near else shade(HAWK_BEAK, 0.9)
            cel(draw, Limb([(cx + d * (0.6 if near else -1.0), base - 7.6), (lx, fy)], [1.1, 0.9]), col, sh=None, lw=0.6)
            _hawk_foot(draw, lx, fy, d, col)
        body = Poly(_rot(arc_pts(cx - d * 0.6, base - 14.0, 7.2, 9.2, 0, 360)[:-1], cx - d * 0.6, base - 14.0, d * 22))
        blob(draw, [body, Ell(cx + d * 0.4, base - 7.6, 3.2, 2.8)], HAWK_CHEST, sh=(0.8, 1.0),
             regions=[(Ell(cx - d * 6.4, base - 15.0, 7.0, 11.0), HAWK)])
        for (u, v) in ((3.2, -18.0), (4.4, -15.0), (4.2, -12.0)):
            stroke(draw, [(cx + d * (u - 1.3), base + v), (cx + d * (u + 1.3), base + v + 0.4)], 0.8, HAWK_BAR)
        flick = [0.0, 2.2, 0.0, 1.0][frame]
        _hawk_wing(draw, (cx + d * 3.6, base - 20.0), (cx - d * 5.4, base - 21.6), (cx - d * 11.8, base - 6.6 - flick),
                   (cx + d * 1.0, base - 10.6), bow=0.5)
        hx, hy = cx + d * 3.4, base - 26.0
        cel(draw, Ell(hx, hy, 9.4, 8.4), HAWK_DK, sh=(1.0, 1.0), hi=(0.6, 0.6),
            regions=[(Ell(hx + d * 3.0, hy + 4.6, 6.6, 4.8), HAWK_CHEST),
                     (Poly([(hx + d * 2.4, hy + 0.2), (hx + d * 6.4, hy + 0.2), (hx + d * 4.8, hy + 6.8)]), HAWK_DK)])
        beak = Poly([(hx + d * 7.8, hy - 2.2), (hx + d * 11.4, hy - 1.4), (hx + d * 13.2, hy + 1.2), (hx + d * 12.6, hy + 4.6),
                     (hx + d * 11.0, hy + 2.6), (hx + d * 7.4, hy + 2.8)])
        cel(draw, beak, HAWK_BEAK, sh=(0.0, 0.6),
            regions=[(Poly([(hx + d * 10.8, hy - 4.0), (hx + d * 15.0, hy - 4.0), (hx + d * 15.0, hy + 6.0), (hx + d * 10.4, hy + 6.0)]), HAWK_HOOK)])
        Ell(hx + d * 4.3, hy - 1.5, 2.45, 2.75).draw(draw, fill=HAWK_BEAK)
        ms_eye(draw, hx + d * 4.3, hy - 1.5, 3.8, 4.4, HAWK_EYE, (float(d), 0.0), "sharp", skin=HAWK_DK, side=-d)
        return
    hx, hy = cx, base - 27.0
    feet = []
    for s in (-1, 1):
        fwd = ph * s
        feet.append((cx + s * 3.2, base - 2.0 - (1.2 if fwd < 0 else 0.0)))
    if back:
        # from behind: the dark hood, the wings folded to a V with their tips crossed over a banded tail
        for (fx, fy) in feet:
            cel(draw, Limb([(fx, base - 7.0), (fx, fy)], [1.1, 0.9]), HAWK_BEAK, sh=None, lw=0.6)
            _hawk_foot(draw, fx, fy, 0)
        tail = Poly([(cx - 3.6, base - 9.0), (cx + 3.6, base - 9.0), (cx + 4.8, base - 2.6), (cx, base - 1.0), (cx - 4.8, base - 2.6)])
        cel(draw, tail, HAWK_DK, sh=None, regions=[(Poly([(cx - 6.0, base - 6.6), (cx + 6.0, base - 6.6), (cx + 6.0, base - 5.0), (cx - 6.0, base - 5.0)]), lit(HAWK, 1.2)),
                                                    (Poly([(cx - 6.0, base - 2.9), (cx + 6.0, base - 2.9), (cx + 6.0, base), (cx - 6.0, base)]), HAWK_CHEST)])
        cel(draw, Ell(cx, base - 14.0, 8.0, 9.0), HAWK, sh=(1.2, 1.0))
        for s in (-1, 1):
            _hawk_wing(draw, (cx + s * 2.0, base - 22.6), _hawk_lerp((cx + s * 9.4, base - 20.2), (cx + s * 14.0, base - 22.4), spread),
                       _hawk_lerp((cx + s * 0.4, base - 4.6), (cx + s * 17.4, base - 11.6), spread),
                       _hawk_lerp((cx + s * 2.4, base - 17.0), (cx + s * 4.0, base - 12.6), spread), bow=0.75 * (1.0 - spread))
        cel(draw, Ell(hx, hy, 10.2, 9.0), HAWK_DK, sh=(1.2, 1.0), hi=(0.6, 0.6))
        return
    # head-on: square shoulders, the wing tips pointing down past the body, the tail between the legs
    for s in (-1, 1):
        _hawk_wing(draw, (cx + s * 4.6, base - 22.6), _hawk_lerp((cx + s * 10.6, base - 20.0), (cx + s * 14.0, base - 22.4), spread),
                   _hawk_lerp((cx + s * 8.6, base - 4.8), (cx + s * 17.4, base - 11.6), spread),
                   _hawk_lerp((cx + s * 4.0, base - 10.6), (cx + s * 5.0, base - 12.0), spread))
    for (fx, fy) in feet:
        cel(draw, Limb([(fx, base - 7.0), (fx, fy)], [1.1, 0.9]), HAWK_BEAK, sh=None, lw=0.6)
        _hawk_foot(draw, fx, fy, 0)
    blob(draw, [Ell(cx, base - 14.0, 8.0, 9.0), Ell(cx - 3.2, base - 7.4, 2.9, 2.7), Ell(cx + 3.2, base - 7.4, 2.9, 2.7)], HAWK_CHEST, sh=(1.2, 1.0))
    for (u, v) in ((-3.0, -3.6), (0.0, -2.6), (3.0, -3.6), (-2.0, 0.6), (2.0, 0.6), (0.0, 3.8)):
        stroke(draw, [(cx + u - 1.3, base - 14.0 + v), (cx + u, base - 14.0 + v + 0.7), (cx + u + 1.3, base - 14.0 + v)], 0.8, HAWK_BAR)
    # the falcon's hood: dark over the eyes, white cheeks and throat, a moustache down from each eye
    marks = [(Ell(hx, hy + 4.2, 7.6, 5.4), HAWK_CHEST)]
    marks += [(Poly([(hx + s * 2.6, hy - 0.4), (hx + s * 6.6, hy - 0.4), (hx + s * 5.0, hy + 6.6)]), HAWK_DK) for s in (-1, 1)]
    marks.append((Poly([(hx - 4.0, hy - 1.8), (hx + 4.0, hy - 1.8), (hx, hy + 1.9)]), HAWK_DK))
    cel(draw, Ell(hx, hy, 10.2, 9.0), HAWK_DK, sh=(1.2, 1.0), hi=(0.6, 0.6), regions=marks)
    for s in (-1, 1):
        Ell(hx + s * 4.6, hy - 1.3, 2.45, 2.85).draw(draw, fill=HAWK_BEAK)
    _eyes(draw, hx - 4.6, hx + 4.6, hy - 1.4, 0, HAWK_EYE, mood="sharp", skin=HAWK_DK, w=3.8, h=4.6)
    beak = Poly([(hx - 2.1, hy + 1.2), (hx + 2.1, hy + 1.2), (hx + 1.9, hy + 4.0), (hx, hy + 7.0), (hx - 1.9, hy + 4.0)])
    cel(draw, beak, HAWK_BEAK, sh=(0.6, 0.4), regions=[(Poly([(hx - 3.0, hy + 4.2), (hx + 3.0, hy + 4.2), (hx, hy + 8.4)]), HAWK_HOOK)])


# ===================================================================
# SHARK (78) -- stands on its tail: a pointed snout, a grin full of teeth, fins for arms
# ===================================================================

SHARK = (104, 134, 176)
SHARK_BELLY = (240, 244, 250)
SHARK_GUM = (200, 70, 90)
SHARK_LASH = (28, 36, 60)


def _shark_teeth(draw, a, b, n, length):
    """A row of teeth along the jaw line from a to b, pointing down (length > 0) or up."""
    for i in range(n):
        t0, t1 = i / float(n), (i + 1) / float(n)
        p0 = (a[0] + (b[0] - a[0]) * t0, a[1] + (b[1] - a[1]) * t0)
        p1 = (a[0] + (b[0] - a[0]) * t1, a[1] + (b[1] - a[1]) * t1)
        Poly([p0, p1, ((p0[0] + p1[0]) / 2.0, (p0[1] + p1[1]) / 2.0 + length)]).draw(draw, fill=TOOTH)


def draw_shark(draw, ox, oy, direction, frame):
    d, back, base, cx, ph = _setup(ox, oy, direction, frame, bob=[0, -2, 0, -2])
    wag = [0.0, 1.0, 0.0, -1.0][frame]
    gill = shade(SHARK, 1.4)
    if d:
        # profile: the egg of the body with a shark's snout over an underslung jaw, one eye, the
        # near fin swept back down its side and the far one showing under the chin
        cel(draw, Poly([(cx - d * 4.0, base - 6.0), (cx - d * (11.0 - wag), base - 11.0), (cx - d * 9.0, base - 3.0), (cx - d * (11.0 + wag), base + 0.6),
                        (cx - d * 2.0, base - 1.4)]), SHARK, sh=(0.6, 0.6))
        cel(draw, Poly([(cx + d * 5.6, base - 19.0), (cx + d * 12.4, base - 12.8 - wag), (cx + d * 6.4, base - 13.6)]), shade(SHARK, 0.6), sh=None)
        cel(draw, Poly([(cx - d * 6.6, base - 34.4), (cx - d * 10.6, base - 45.0), (cx - d * 1.6, base - 38.8)]), SHARK, sh=(0.6, 0.6))
        egg = Poly(arc_pts(cx - d * 0.6, base - 22.0, 10.0, 18.0, 180, 360, 30) + arc_pts(cx - d * 0.6, base - 13.0, 8.4, 9.0, 0, 180, 16)[1:-1])
        snout = Poly([(cx + d * 0.6, base - 39.4), (cx + d * 8.0, base - 36.8), (cx + d * 13.6, base - 32.6), (cx + d * 13.2, base - 30.4),
                      (cx + d * 11.2, base - 21.0), (cx + d * 5.0, base - 18.6)])
        blob(draw, [egg, snout], SHARK, sh=(1.8, 1.4), regions=[(Ell(cx + d * 4.8, base - 13.6, 6.6, 11.6), SHARK_BELLY)])
        for k in range(3):
            gx = cx - d * (0.2 + k * 2.1)
            stroke(draw, [(gx, base - 27.6 + k * 0.4), (gx - d * 0.6, base - 22.8 + k * 0.4)], 0.8, gill)
        top = ((cx + d * 3.0, base - 25.4), (cx + d * 11.8, base - 27.8))
        low = ((cx + d * 4.2, base - 21.2), (cx + d * 11.2, base - 22.4))
        cel(draw, Poly([top[0], top[1], low[1], low[0]]), SHARK_GUM, sh=None, line_color=shade(SHARK, 1.8), lw=0.6)
        _shark_teeth(draw, top[0], top[1], 4, 2.1)
        _shark_teeth(draw, low[0], low[1], 3, -1.6)
        Ell(cx + d * 11.2, base - 31.2, 0.6, 0.5).draw(draw, fill=SHARK_LASH)
        ms_eye(draw, cx + d * 5.2, base - 31.8, 3.8, 4.4, (40, 40, 60), (float(d), 0.0), "sharp", skin=SHARK, side=-d, lash=SHARK_LASH)
        cel(draw, Poly([(cx - d * 2.6, base - 21.0), (cx + d * 3.0, base - 17.0), (cx - d * 1.0, base - 13.6), (cx - d * 7.2, base - 9.8 + wag)]), SHARK,
            sh=(0.6, 0.6), lw=0.8)
        return
    # tail fin as feet
    cel(draw, Poly([(cx - 8.0, base + 0.4), (cx - 2.0, base - 5.0), (cx + 2.0, base - 5.0), (cx + 8.0, base + 0.4), (cx + 3.0, base - 1.4), (cx, base - 0.4),
                    (cx - 3.0, base - 1.4)]), SHARK, sh=(0.6, 0.6))
    # body: a torpedo standing on end
    bw = 11.4
    body = Poly(arc_pts(cx, base - 22.0, bw, 18.0, 180, 360, 30) + arc_pts(cx, base - 13.0, bw * 0.84, 9.0, 0, 180, 16)[1:-1])
    if back:
        for s in (-1, 1):
            sx = cx + s * (bw - 1.4)
            cel(draw, Poly([(sx, base - 22.0), (sx + s * 6.0, base - 15.0 + s * wag), (sx - s * 1.0, base - 16.0)]), SHARK, sh=(0.6, 0.6))
        # from behind the dorsal fin is the nearest thing: big, low on the back, its shadow under it
        cel(draw, body, SHARK, sh=(1.8, 1.4), hi=(1.0, 1.0),
            regions=[(Ell(cx + 1.8, base - 28.0, 5.0, 2.6), shade(SHARK, 1.0))])
        cel(draw, Poly([(cx - 5.0, base - 30.4), (cx + 1.0, base - 46.0), (cx + 5.0, base - 30.8), (cx, base - 29.4)]), SHARK, sh=(0.9, 0.6),
            hi=(0.7, 0.4))
        return
    # the dorsal fin, showing over the head
    cel(draw, Poly([(cx - 3.4, base - 37.0), (cx + 0.6, base - 45.0), (cx + 3.4, base - 37.4)]), SHARK, sh=(0.6, 0.6))
    cel(draw, body, SHARK, sh=(1.8, 1.4), hi=(1.0, 1.0), regions=[(Ell(cx, base - 13.0, bw * 0.66, 10.0), SHARK_BELLY)])
    # pectoral fins as arms
    for s in (-1, 1):
        sx = cx + s * (bw - 1.4)
        cel(draw, Poly([(sx, base - 22.0), (sx + s * 6.0, base - 15.0 + s * wag), (sx - s * 1.0, base - 16.0)]), SHARK, sh=(0.6, 0.6))
    # eyes high on the head, a huge grin below
    _eyes(draw, cx - 5.2, cx + 5.2, base - 30.0, 0, (40, 40, 60), mood="sharp", skin=SHARK, w=3.6, h=4.2, lash=SHARK_LASH)
    mw, my = 7.4, base - 24.0
    cel(draw, Poly([(cx - mw, my - 1.0), (cx + mw, my - 1.0), (cx + mw * 0.6, my + 3.6), (cx - mw * 0.6, my + 3.6)]), SHARK_GUM, sh=None,
        line_color=shade(SHARK, 1.8))
    _shark_teeth(draw, (cx - mw + 0.4, my - 1.0), (cx + mw - 0.4, my - 1.0), 6, 2.4)
    _shark_teeth(draw, (cx - mw * 0.6 + 0.3, my + 3.6), (cx + mw * 0.6 - 0.3, my + 3.6), 5, -1.8)


# ===================================================================
# BEETLE (79) -- a rhinoceros beetle: a great horn, a shining shell
# ===================================================================

# Two things the blocks from here to the Chameleon share (the file's import
# list and the helpers above the Wolf are not theirs to edit).
from sprite_base import bez as _bez  # noqa: E402


class _Band(object):
    """A region for `blob` and `cel`: the band of a mass that a copy of it moved by `by` no longer
    covers. By (1.6, 1.6) it is the lit rim along the upper left, as cel's `hi` is for one shape;
    by (0, -1.6) the strip along its lower edge."""

    def __init__(self, shapes, by):
        self.shapes, self.by = shapes, by

    def draw(self, d, fill=None, outline=None, width=1):
        band = d.sub()
        for s in self.shapes:
            s.draw(band, fill=fill)
        for s in self.shapes:
            s.shift(self.by[0], self.by[1]).draw(band, fill=(0, 0, 0, 0))
        d.merge(band)


BTL = (66, 158, 112)
BTL_PRO = (48, 118, 96)
BTL_DK = (38, 86, 78)
BTL_HORN = (60, 60, 80)
BTL_HORN_LT = (128, 128, 164)
BTL_EYE = (250, 220, 60)

# Six legs as (hip, knee, foot) offsets from the middle of the body. Head-on they fan out from under
# the shell, the pair furthest from the camera standing highest; in profile the front pair reaches
# forward and the hind pair back.
_BTL_LEGS_FRONT = (((6.0, -10.0), (14.2, -14.6), (16.6, -8.4)), ((6.0, -8.0), (14.8, -9.4), (17.4, -2.6)),
                   ((5.0, -6.0), (10.2, -6.4), (12.2, -0.6)))
_BTL_LEGS_SIDE = (((5.0, -9.4), (8.6, -6.4), (9.8, -0.6)), ((-0.6, -9.0), (1.2, -5.2), (-0.4, -0.6)),
                  ((-6.6, -9.4), (-10.6, -6.8), (-12.6, -0.6)))


def _btl_gloss(cx, cy, rx, ry, a0, a1, thick):
    """A crescent of shine round a dome: the hard highlight of polished chitin."""
    return Poly(arc_pts(cx, cy, rx, ry, a0, a1, 10) + arc_pts(cx, cy, rx - thick, ry - thick, a1, a0, 10))


def _btl_horn(draw, x, y, d, back=False):
    """The great horn, rising off the head to a fork: head-on a tall Y, in profile swept forward and
    up with the fork at its tip."""
    if d:
        stem = Limb(_bez((x + d * 0.6, y), (x + d * 7.4, y - 4.6), (x + d * 5.8, y - 14.2), 6), [2.7, 2.5, 2.3, 2.1, 1.9, 1.7, 1.5])
        prongs = [Limb([(x + d * 5.8, y - 13.0), (x + d * 4.2, y - 16.8), (x + d * 2.2, y - 18.6)], [1.6, 1.3, 0.7]),
                  Limb([(x + d * 5.8, y - 13.0), (x + d * 7.8, y - 16.4), (x + d * 8.6, y - 18.8)], [1.6, 1.3, 0.7])]
    else:
        stem = Limb(_bez((x, y), (x - 1.2, y - 9.0), (x + 0.3, y - 18.0), 6), [2.9, 2.7, 2.5, 2.3, 2.1, 2.0, 1.9])
        prongs = [Limb([(x + 0.1, y - 17.0), (x - 2.4, y - 21.0), (x - 3.8, y - 24.4)], [1.8, 1.5, 0.8]),
                  Limb([(x + 0.5, y - 17.0), (x + 3.0, y - 21.0), (x + 4.6, y - 24.2)], [1.8, 1.5, 0.8])]
    shapes = [stem] + prongs
    blob(draw, shapes, BTL_HORN, sh=(0.8, 0.5), regions=[(_Band(shapes, (1.1, 0.5)), BTL_HORN_LT)])


def draw_beetle(draw, ox, oy, direction, frame):
    d, back, base, cx, ph = _setup(ox, oy, direction, frame, bob=[0, 0, 0, 0])
    step = [0.0, 1.0, 0.0, -1.0][frame]
    rock = [0.0, -0.6, 0.0, -0.6][frame]        # the shell rides the stride; the feet stay down

    def leg(hip, knee, foot, s, k, near=True):
        """One jointed leg; `k` is which way it swings this stride, `s` its side (or the facing)."""
        sw = k * step
        lift = 1.2 if sw > 0 else 0.0
        col = BTL_HORN if near else shade(BTL_HORN, 0.9)
        pts = [(cx + s * hip[0], base + hip[1] + rock), (cx + s * knee[0] + (d * sw * 1.2 if d else 0.0), base + knee[1] - abs(sw) * 0.8),
               (cx + s * foot[0] + (d * sw * 2.2 if d else s * sw * 1.0), base + foot[1] - lift)]
        cel(draw, Limb(pts, [1.7, 1.4, 0.7]), col, sh=None)

    if d:
        for near in (False, True):
            for i, (hip, knee, foot) in enumerate(_BTL_LEGS_SIDE):
                k = (1 if i != 1 else -1) * (1 if near else -1)
                off = -2.0 if near else -0.4        # the body sits a little back of the middle: the horn leads
                leg((hip[0] + off, hip[1]), (knee[0] + off, knee[1] - (0.0 if near else 0.8)),
                    (foot[0] + off, foot[1] - (0.0 if near else 1.0)), d, k, near)
            if not near:
                cel(draw, Ell(cx - d * 4.0, base - 9.8 + rock, 11.4, 3.4), BTL_HORN, sh=None)
        sy = base - 12.0 + rock
        scx = cx - d * 5.0
        shell = Poly(_rot(arc_pts(scx, sy, 12.6, 12.6, 180, 360, 28), scx, sy, -d * 4))
        cel(draw, shell, BTL, sh=(1.4, 1.2), hi=(0.9, 0.9),
            regions=[(_Band([shell], (0.0, -1.7)), shade(BTL, 1.3)), (_btl_gloss(scx, sy, 9.8, 9.8, 206, 262, 2.0), lit(BTL, 1.7))])
        cel(draw, Ell(cx + d * 7.4, base - 13.2 + rock, 5.6, 6.2), BTL_PRO, sh=(0.9, 0.9), hi=(0.6, 0.6))
        hx, hy = cx + d * 12.6, base - 9.0 + rock
        cel(draw, Ell(hx, hy, 4.4, 4.2), BTL_DK, sh=(0.7, 0.7))
        _btl_horn(draw, hx, hy - 3.0, d)
        _eyes(draw, hx + d * 1.2, hx + d * 3.3, hy - 0.2, d, BTL_EYE, mood="bright", skin=BTL_DK, w=3.2, h=3.6, lash=(20, 30, 30))
        return
    for i, (hip, knee, foot) in enumerate(_BTL_LEGS_FRONT if not back else _BTL_LEGS_FRONT[::-1]):
        if back:        # walking away the same fan is seen from the other end: the hind pair nearest
            foot = (foot[0], _BTL_LEGS_FRONT[i][2][1])
            knee = (knee[0], _BTL_LEGS_FRONT[i][1][1])
        for s in (-1, 1):
            leg(hip, knee, foot, s, (1 if (i + (s > 0)) % 2 else -1), near=i > 0)
    if back:
        # walking away: the horn's fork and the pronotum beyond the shell, the two wing cases meeting
        # down the middle
        _btl_horn(draw, cx, base - 11.6 + rock, 0, back=True)
        cel(draw, Ell(cx, base - 23.4 + rock, 9.4, 5.8), BTL_PRO, sh=(1.0, 0.8), hi=(0.6, 0.6))
        sy = base - 14.2 + rock
        shell = Ell(cx, sy, 13.0, 11.6)
        cel(draw, shell, BTL, sh=(1.6, 1.4), hi=(0.9, 0.9),
            regions=[(_btl_gloss(cx, sy, 10.6, 9.2, 196, 252, 2.0), lit(BTL, 1.7)), (_btl_gloss(cx, sy, 10.6, 9.2, 292, 318, 1.6), lit(BTL, 1.2))])
        stroke(draw, [(cx, sy - 8.6), (cx, sy + 10.4)], 0.9, BTL_DK)
        cel(draw, Poly([(cx - 2.4, sy - 11.0), (cx + 2.4, sy - 11.0), (cx, sy - 6.8)]), BTL_PRO, sh=None)
        return
    # head-on: the wing cases highest and furthest, the pronotum before them, the head lowest and
    # nearest, the horn standing over all three
    sy = base - 16.6 + rock
    cel(draw, Ell(cx, sy, 12.8, 11.4), BTL, sh=(1.6, 1.4), hi=(0.9, 0.9),
        regions=[(_btl_gloss(cx, sy, 10.4, 9.0, 198, 250, 2.0), lit(BTL, 1.7))])
    cel(draw, Ell(cx, base - 11.4 + rock, 10.0, 6.8), BTL_PRO, sh=(1.2, 1.0), hi=(0.7, 0.7))
    hx, hy = cx, base - 7.0 + rock
    cel(draw, Ell(hx, hy, 6.6, 4.8), BTL_DK, sh=(0.8, 0.8))
    _btl_horn(draw, hx, hy - 4.2, 0)
    _eyes(draw, hx - 3.4, hx + 3.4, hy + 0.1, 0, BTL_EYE, mood="bright", skin=BTL_DK, w=3.4, h=3.8, lash=(20, 30, 30))


# ===================================================================
# TREANT (80) -- a walking tree: a stern face carved in the bark under a dark crown in
# blossom, branch arms in leaf, root legs, moss down its back
# ===================================================================

# The crown is a darker, bluer green than the Meadow's grass (the old one was the grass's own
# colour, and on it the tree lost its top), its sunlit clumps the old green; the bark is a step
# darker than the Lagoon's boardwalk.
BARK = (132, 90, 58)
BARK_DK = (92, 62, 44)
LEAVES = (62, 138, 74)
LEAVES_LT = (108, 186, 84)
MOSS = (124, 158, 72)
BLOSSOM = (255, 150, 174)
TREANT_EYE = (250, 230, 110)


def _tre_leaves(draw, x, y, angs, flip, near=True):
    """A tuft of pointed leaves fanning from the end of a branch (`flip` mirrors the fan)."""
    for i, a in enumerate(angs):
        col = LEAVES_LT if i % 2 == 0 else LEAVES
        leaf(draw, x, y, a if flip > 0 else 180.0 - a, 5.2, 3.2, col if near else shade(col, 0.8), vein=False)


def _tre_root_leg(top, ankle, toes, ground, w=4.4):
    """A root for a leg: a stump from the trunk down to `ankle`, splitting into root toes that
    reach `toes` units along the ground either way and forward."""
    ax, ay = ankle
    return [Limb([top, ankle], [w, w * 0.72])] + [Limb([(ax, ay - 0.4), (ax + u, ground - v)], [1.8, 0.6]) for (u, v) in toes]


def _tre_canopy(draw, x, cy):
    """The crown: one dark mass of foliage, the clumps toward the light picked out, three
    blossoms. The same from every side: the light is always upper left."""
    parts = [Ell(x, cy, 13.6, 8.6), Ell(x - 9.4, cy + 3.2, 6.2, 5.2), Ell(x + 9.4, cy + 3.2, 6.2, 5.2), Ell(x - 5.0, cy - 6.0, 6.6, 5.2),
             Ell(x + 5.6, cy - 5.4, 6.2, 5.0), Ell(x, cy + 5.0, 6.0, 4.4)]
    clumps = [Ell(x - 6.4, cy - 6.8, 4.6, 3.0), Ell(x + 4.2, cy - 6.2, 4.0, 2.6), Ell(x - 10.4, cy + 1.4, 3.4, 2.4), Ell(x - 1.0, cy + 0.2, 4.4, 2.6)]
    blob(draw, parts, LEAVES, sh=(1.6, 1.4), regions=[(c, LEAVES_LT) for c in clumps])
    for (u, v) in ((-7.0, 3.6), (5.6, -1.6), (1.4, 5.6)):
        cel(draw, Ell(x + u, cy + v, 1.9, 1.9), BLOSSOM, sh=None, lw=0.5)
        Ell(x + u - 0.2, cy + v - 0.2, 0.8, 0.8).draw(draw, fill=(255, 232, 160))


def _tre_eye(draw, ex, ey, k=1.0):
    """A hollow in the bark with a light burning in it."""
    Ell(ex, ey, 2.7 * k, 2.9).draw(draw, fill=VOID)
    Ell(ex, ey + 0.4, 1.6 * k, 1.7).draw(draw, fill=TREANT_EYE)
    Ell(ex - 0.4 * k, ey, 0.7 * k, 0.7).draw(draw, fill=(255, 250, 206))


def draw_treant(draw, ox, oy, direction, frame):
    d, back, base, cx, ph = _setup(ox, oy, direction, frame)
    sway = [0.0, 1.0, 0.0, -1.0][frame]
    top = base - 31.0
    ground = base
    crown_x, crown_y = cx - d * 1.6 + sway * 0.5, base - 36.0
    if d:
        def u(k):
            return cx + d * k

        sw = ph * 3.2
        toes = ((d * 4.4, 0.9), (d * 1.6, 0.3), (-d * 3.4, 0.9))
        dark = shade(BARK, 0.7)
        # the far arm swinging out behind the trunk, and the far root
        hand = (u(-12.6 + sw * 1.0), base - 12.8)
        cel(draw, Limb([(u(-3.0), base - 20.0), (u(-9.4 + sw * 0.4), base - 17.2), hand], [2.4, 1.9, 1.4]), dark, sh=None)
        _tre_leaves(draw, hand[0], hand[1], (20.0, 70.0, 120.0), -d, near=False)
        lift = 1.2 if sw > 0 else 0.0
        blob(draw, _tre_root_leg((u(-2.4 - sw * 0.3), base - 9.5), (u(-2.4 - sw), base - 3.6 - lift), toes, ground - 0.6 - lift, 4.0), dark, sh=None)
        # the trunk: the brow, a broken branch for a nose and the near root are one mass with it
        lift = 1.2 if sw < 0 else 0.0
        trunk = Poly([(u(-6.6), top), (u(5.6), top), (u(6.2), base - 21.0), (u(6.0), base - 13.0), (u(6.6), base - 7.6), (u(4.0), base - 6.4),
                      (u(-5.0), base - 6.4), (u(-7.6), base - 8.0), (u(-7.2), base - 15.0), (u(-6.6), base - 24.0)])
        mass = [trunk, Limb([(u(5.0), base - 19.0), (u(9.8), base - 20.0)], [1.7, 1.1])] + \
            _tre_root_leg((u(1.4 + sw * 0.3), base - 9.5), (u(1.4 + sw), base - 3.0 - lift), toes, ground - lift, 4.2)
        grooves = [(Limb([(u(-3.6), base - 27.0), (u(-4.4), base - 20.0), (u(-4.0), base - 12.0)], [0.5, 0.9, 0.5]), BARK_DK),
                   (Limb([(u(2.2), base - 13.0), (u(3.0), base - 9.0), (u(2.0 + sw), base - 4.0 - lift)], [0.5, 0.9, 0.5]), BARK_DK)]
        moss = [(Ell(u(-5.4), base - 27.4, 3.6, 3.6), MOSS), (Ell(u(-6.2), base - 22.6, 2.6, 2.8), MOSS)]
        blob(draw, mass, BARK, sh=(1.8, 1.0), regions=[(_Band(mass, (1.1, 0.2)), lit(BARK, 0.8))] + grooves + _coat(mass, moss, (1.8, 1.0)))
        _tre_eye(draw, u(3.4), base - 22.8, 0.9)
        cel(draw, Poly([(u(1.0), base - 26.6), (u(8.4), base - 24.8), (u(8.2), base - 22.8), (u(1.8), base - 24.6)]), lit(BARK, 0.5), sh=(0.0, 0.6))
        Poly([(u(6.8), base - 16.4), (u(2.0), base - 15.8), (u(3.2), base - 13.8), (u(6.6), base - 14.2)]).draw(draw, fill=VOID)
        # the near arm, hanging forward under the mouth and swinging against the near root
        el, hand = (u(2.4 - sw * 0.4), base - 12.8), (u(8.4 - sw * 1.0), base - 10.8)
        blob(draw, [Limb([(u(-1.6), base - 21.0), el, hand], [2.5, 2.0, 1.5]), Limb([el, (el[0] - d * 3.0, el[1] + 2.4)], [1.1, 0.5])], lit(BARK, 0.5),
             sh=(0.6, 0.6))
        _tre_leaves(draw, hand[0], hand[1], (20.0, 70.0, 120.0), d)
        _tre_canopy(draw, crown_x, crown_y)
        return
    # head-on and from behind: branch arms out to the sides, each with a spur at the elbow and a
    # tuft of leaves for a hand; the trunk flares at the hips and splits into two root legs
    for s in (-1, 1):
        a = sway * s * 1.2
        el, hand = (cx + s * 12.4, base - 21.4 + a), (cx + s * 15.6, base - 15.8 + a)
        spur = (el[0] + s * 1.4, el[1] - 4.4)
        leaf(draw, spur[0], spur[1], -90.0 + s * 35.0, 4.2, 2.8, LEAVES_LT, vein=False)
        blob(draw, [Limb([(cx + s * 6.4, base - 22.6), el, hand], [2.6, 2.1, 1.5]), Limb([el, spur], [1.1, 0.5])], BARK, sh=(0.6, 0.6))
        _tre_leaves(draw, hand[0], hand[1], (15.0, 65.0, 115.0), s)
    trunk = Poly([(cx - 7.2, top), (cx + 7.2, top), (cx + 6.8, base - 21.0), (cx + 7.4, base - 14.0), (cx + 9.2, base - 8.0), (cx + 8.0, base - 6.2),
                  (cx, base - 7.4), (cx - 8.0, base - 6.2), (cx - 9.2, base - 8.0), (cx - 7.4, base - 14.0), (cx - 6.8, base - 21.0)])
    mass = [trunk]
    lifts = {}
    for s in (-1, 1):
        lifts[s] = 1.4 if ph * s < 0 else 0.0
        mass += _tre_root_leg((cx + s * 4.6, base - 9.5), (cx + s * 5.8, base - 3.0 - lifts[s]), ((s * 4.0, 0.9), (s * 0.6, 0.3), (-s * 3.2, 0.9)),
                              ground - lifts[s], 4.6)
    grooves = [(Limb([(cx - 4.4, base - 14.0), (cx - 5.4, base - 9.0), (cx - 5.6, base - 4.0 - lifts[-1])], [0.5, 0.9, 0.5]), BARK_DK),
               (Limb([(cx + 3.6, base - 13.0), (cx + 4.8, base - 9.0), (cx + 5.8, base - 4.0 - lifts[1])], [0.5, 0.9, 0.5]), BARK_DK)]
    rim = [(_Band(mass, (1.1, 0.2)), lit(BARK, 0.8))]
    if back:
        # from behind: moss hanging down from under the crown, a knot hole, the bark's grooves
        moss = [(Ell(cx - 3.4, base - 27.6, 6.0, 3.8), MOSS), (Ell(cx + 2.6, base - 24.4, 4.4, 3.2), MOSS), (Ell(cx - 5.8, base - 22.4, 3.2, 2.8), MOSS),
                (Ell(cx + 6.4, base - 28.0, 3.2, 2.8), MOSS)]
        grooves.append((Limb([(cx - 1.6, base - 13.0), (cx - 0.8, base - 9.0)], [0.5, 0.8]), BARK_DK))
        blob(draw, mass, BARK, sh=(1.8, 1.0), regions=rim + grooves + _coat(mass, moss, (1.8, 1.0)))
        cel(draw, Ell(cx + 2.6, base - 14.6, 2.6, 2.9), BARK_DK, sh=None, line_color=ink(BARK))
        Ell(cx + 2.8, base - 14.4, 1.4, 1.7).draw(draw, fill=VOID)
        _tre_canopy(draw, crown_x, crown_y)
        return
    grooves += [(Limb([(cx - 5.6, base - 30.0), (cx - 5.8, base - 27.0)], [0.6, 0.4]), BARK_DK),
                (Limb([(cx + 5.4, base - 21.0), (cx + 5.8, base - 16.0)], [0.4, 0.7]), BARK_DK)]
    blob(draw, mass, BARK, sh=(1.8, 1.0), regions=rim + grooves)
    # the face: two hollows with a light in each under heavy brows, a knot for a nose, a mouth
    # turned down at the corners
    for s in (-1, 1):
        _tre_eye(draw, cx + s * 3.7, base - 22.6)
        cel(draw, Poly([(cx + s * 0.5, base - 23.4), (cx + s * 0.9, base - 25.6), (cx + s * 7.5, base - 27.6), (cx + s * 7.5, base - 25.4)]), lit(BARK, 0.5),
            sh=(0.0, 0.6))
    cel(draw, Poly([(cx - 0.9, base - 21.8), (cx + 0.9, base - 21.8), (cx + 1.9, base - 18.4), (cx - 1.9, base - 18.4)]), lit(BARK, 0.5), sh=(0.5, 0.3))
    Poly([(cx - 4.4, base - 15.0), (cx - 2.6, base - 16.8), (cx + 2.6, base - 16.8), (cx + 4.4, base - 15.0), (cx + 2.4, base - 14.6), (cx - 2.4, base - 14.6)]).draw(
        draw, fill=VOID)
    _tre_canopy(draw, crown_x, crown_y)


# ===================================================================
# PHOENIX (81) -- a bird of fire: flame crest, blazing wings, long tail
# ===================================================================

PHX = (240, 96, 44)
PHX_RED = (212, 50, 38)
PHX_GOLD = (255, 196, 60)
PHX_CORE = (255, 240, 170)
PHX_FIRE = ((236, 70, 40), (255, 150, 50), (255, 234, 130))
PHX_EYE = (120, 30, 20)

# A wing in its own frame, pointing right: +x from the shoulder to the tip, +y its trailing edge,
# where the feathers end in tongues of flame.
_PHX_WING = ((0.0, -2.2), (7.0, -3.4), (17.0, -0.6), (14.6, 2.8), (16.0, 6.0), (11.8, 5.4), (12.4, 9.2), (7.8, 7.4),
             (7.4, 10.6), (0.6, 6.4))


def _phx_wing(draw, sx, sy, ang, mirror, k=1.0, near=True, back=False):
    """A wing of flame from the shoulder at (sx, sy): deep red at its edge, hotter toward the
    body, as the kit's flame is. Seen from behind it shows its red back and no gold."""
    wing = Poly([(sx + mirror * x, sy + y) for (x, y) in xform(_PHX_WING, 0.0, 0.0, ang, k)])
    dim = (lambda c: c) if near else (lambda c: shade(c, 0.8))
    cel(draw, wing, dim(PHX_RED), sh=None)
    for (kk, col) in (((0.6, PHX),) if back else ((0.76, PHX), (0.48, PHX_GOLD))):
        wing.scaled(kk, sx, sy).draw(draw, fill=dim(col))


def _phx_flame(draw, x, y, ang, w, h, near=True):
    """The kit's flame, standing on (x, y) and pointing along `ang` (90 is straight down)."""
    for i, (kw, kh, col) in enumerate(((1.0, 1.0, PHX_FIRE[0]), (0.64, 0.66, PHX_FIRE[1]), (0.34, 0.36, PHX_FIRE[2]))):
        shape = Poly(_rot(flame_pts(x, y, w * kw, h * kh, 0.0, -1.0 if i == 1 else 1.0), x, y, ang + 90.0))
        col = col if near else shade(col, 0.8)
        if i == 0:
            cel(draw, shape, col, sh=None)
        else:
            shape.draw(draw, fill=col)


def _phx_plume(draw, pts, w, h, near=True):
    """A tail plume: a quill of fire that opens into a tongue of flame `h` long at its end."""
    n = len(pts) - 1
    cel(draw, Limb(pts, [1.9 - 0.5 * i / n for i in range(n + 1)]), PHX if near else shade(PHX, 0.8), sh=None)
    Limb(pts, [0.7] * (n + 1)).draw(draw, fill=PHX_GOLD if near else shade(PHX_GOLD, 0.8))
    (x0, y0), (x1, y1) = pts[-2], pts[-1]
    ang = math.degrees(math.atan2(y1 - y0, x1 - x0))
    _phx_flame(draw, x1 - math.cos(math.radians(ang)) * 1.2, y1 - math.sin(math.radians(ang)) * 1.2, ang, w, h, near)


def draw_phoenix(draw, ox, oy, direction, frame):
    # on the wing: up, level, down, level, the body riding the beat
    d, back, base, cx, ph = _setup(ox, oy, direction, frame, bob=[-2, -1, -2, -3])
    sway = [0.0, 1.0, 0.0, -1.0][frame]
    if d:
        bx = cx + d * 1.5
        ang = [-50.0, -76.0, -50.0, 24.0][frame]
        hx, hy = bx + d * 6.5, base - 27.5
        _phx_wing(draw, bx - d * 0.6, base - 21.4, ang - 12.0, -d, near=False)
        # the tail streaming out behind: one plume high and far, two near
        _phx_plume(draw, _bez((bx - d * 6.4, base - 15.6), (bx - d * 11.6, base - 18.0), (bx - d * 16.6, base - 15.0 + sway), 5), 4.6, 7.0, near=False)
        _phx_plume(draw, _bez((bx - d * 5.6, base - 12.4), (bx - d * 8.0, base - 9.4), (bx - d * 9.6, base - 6.0 - sway), 5), 4.6, 8.0)
        _phx_plume(draw, _bez((bx - d * 6.4, base - 13.8), (bx - d * 11.6, base - 12.8), (bx - d * 15.6, base - 8.4 + sway), 5), 5.0, 8.0)
        body = Poly(_rot(arc_pts(bx - d * 0.8, base - 16.6, 8.2, 6.8, 0, 360)[:-1], bx - d * 0.8, base - 16.6, -d * 20))
        cel(draw, body, PHX, sh=(1.2, 1.0), regions=[(Ell(bx + d * 2.8, base - 15.4, 4.8, 5.2), PHX_GOLD), (Ell(bx + d * 3.6, base - 14.6, 2.4, 3.0), PHX_CORE)])
        for u in (0.6, 3.2):
            cel(draw, Ell(bx + d * u, base - 9.6, 1.9, 1.4), PHX_GOLD, sh=None, lw=0.7)
        flame(draw, hx - d * 5.4, hy + 0.6, 6.0, 9.0, PHX_FIRE, sway=sway - d * 4.0)
        flame(draw, hx - d * 2.2, hy - 3.6, 11.0, 16.0, PHX_FIRE, sway=sway * 1.4 - d * 5.0)
        cel(draw, Ell(hx, hy, 7.6, 7.0), PHX, sh=(0.8, 0.8), hi=(0.5, 0.5))
        beak = Poly([(hx + d * 5.4, hy - 1.0), (hx + d * 10.2, hy + 0.2), (hx + d * 11.4, hy + 3.4), (hx + d * 9.6, hy + 2.4), (hx + d * 5.8, hy + 3.0)])
        cel(draw, beak, PHX_GOLD, sh=(0.0, 0.7))
        ms_eye(draw, hx + d * 2.8, hy - 0.8, 4.0, 4.6, PHX_EYE, (float(d), 0.0), "sharp", skin=PHX, side=-d, lash=(90, 20, 20))
        _phx_wing(draw, bx - d * 0.6, base - 20.4, ang, -d)
        return
    hx, hy = cx, base - 27.6
    tail = [(_bez((cx + s * 2.4, base - 10.6), (cx + s * 5.6, base - 9.6), (cx + s * (7.8 + sway * 0.5 * s), base - 6.8), 5), 4.6, 6.8) for s in (-1, 1)] + \
        [(_bez((cx, base - 10.0), (cx + sway * 0.4, base - 8.4), (cx + sway * 0.6, base - 6.8), 4), 5.2, 8.6)]
    wing_ang = [-30.0, -52.0, -30.0, 12.0][frame]

    def wings():
        for s in (-1, 1):
            _phx_wing(draw, cx + s * 5.0, base - 20.6, wing_ang, s, back=back)

    if not back:
        for pts, w, h in tail:
            _phx_plume(draw, pts, w, h)
    wings()
    # the breast gold with a pale core; the back a red mantle between the wings
    cel(draw, Ell(cx, base - 16.4, 7.4, 8.6), PHX, sh=(1.2, 1.0),
        regions=[(Poly([(cx - 8.0, base - 26.0), (cx + 8.0, base - 26.0), (cx + 5.0, base - 17.0), (cx, base - 13.0), (cx - 5.0, base - 17.0)]), PHX_RED)]
        if back else [(Ell(cx, base - 15.4, 4.6, 6.2), PHX_GOLD), (Ell(cx, base - 14.6, 2.4, 3.6), PHX_CORE)])
    if back:
        # from behind the tail fans out over the body
        for pts, w, h in tail:
            _phx_plume(draw, pts, w, h)
    else:
        for s in (-1, 1):
            cel(draw, Ell(cx + s * 3.0, base - 8.6, 1.9, 1.4), PHX_GOLD, sh=None, lw=0.7)
    for s in (-1, 1):
        flame(draw, hx + s * 3.8, hy - 3.6, 5.6, 8.4, PHX_FIRE, sway=sway + s * 1.4, flip=float(s))
    flame(draw, hx, hy - 4.4, 12.0, 17.0, PHX_FIRE, sway=sway * 1.4)
    cel(draw, Ell(hx, hy, 8.4, 7.4), PHX, sh=(0.8, 0.8), hi=(0.5, 0.5))
    if back:
        return
    _eyes(draw, hx - 3.7, hx + 3.7, hy - 0.8, 0, PHX_EYE, mood="sharp", skin=PHX, w=4.0, h=4.8, lash=(90, 20, 20))
    beak = Poly([(hx - 2.0, hy + 2.0), (hx + 2.0, hy + 2.0), (hx + 1.1, hy + 4.8), (hx, hy + 6.2), (hx - 1.1, hy + 4.8)])
    cel(draw, beak, PHX_GOLD, sh=(0.5, 0.4))


# ===================================================================
# HYDRA (82) -- three wedge heads on long necks over a low, heavy body: purple crests and
# spines, cream throats and belly, slit gold eyes
# ===================================================================

# Kept apart from the Serpent (a slim green cobra, one hooded head, a pale chevroned belly) by its
# build as much as its heads: teal, four-legged, wide and low, a spined tail on the ground, and
# purple along every edge that faces away from the camera.
HYD = (74, 160, 138)
HYD_BELLY = (234, 222, 150)
HYD_FIN = (136, 82, 172)
HYD_EYE = (255, 200, 60)
HYD_PUPIL = (36, 28, 48)
_HYD_NECK_W = [3.5, 3.4, 3.3, 3.2, 3.1, 3.0, 2.9]
_HYD_TAIL_W = [3.8, 3.5, 3.1, 2.6, 2.0, 1.3, 0.4]


def _hyd_eye(draw, ex, ey, w, h, out, col):
    """A reptile's eye, drawn by hand (the kit's eye at this size was a white speck under a lash):
    a gold almond, a slit of a pupil, and the brow ridge coming down over it toward the snout.
    `out` is which way the outer corner lies."""
    rx, ry = w / 2.0, h / 2.0
    Ell(ex, ey, rx, ry).draw(draw, fill=HYD_EYE)
    Ell(ex, ey - ry * 0.35, rx * 0.96, ry * 0.6).draw(draw, fill=shade(HYD_EYE, 1.3))
    Ell(ex - out * rx * 0.1, ey + ry * 0.1, rx * 0.3, ry * 0.84).draw(draw, fill=HYD_PUPIL)
    Ell(ex + out * rx * 0.42, ey + ry * 0.3, 0.55, 0.55).draw(draw, fill=(255, 255, 255))
    x_in, x_out = ex - out * (rx + 0.5), ex + out * (rx + 0.7)
    Poly([(x_in, ey - ry - 0.9), (x_out, ey - ry - 0.9), (x_out, ey - ry * 0.72), (x_in, ey - ry * 0.02)]).draw(draw, fill=col)
    stroke(draw, [(x_in, ey - ry * 0.02), (x_out, ey - ry * 0.72)], 1.0, ink(col))


def _hyd_spines(draw, pts, at, out, rise, size=1.0, col=HYD_FIN):
    """Spines standing off a neck, a back or a tail that is drawn after them: at each index in `at`
    of the line `pts` a blade rooted on the line (`rise` is the limb's half-width there), leaning
    back toward the line's start, on the side `out` picks."""
    for i in at:
        (ax, ay), (bx, by) = pts[max(0, i - 1)], pts[min(len(pts) - 1, i + 1)]
        ln = math.hypot(bx - ax, by - ay) or 1.0
        tx, ty = (bx - ax) / ln, (by - ay) / ln
        nx, ny = -ty * out, tx * out
        px, py = pts[i]
        hw, h = 2.0 * size, rise + 3.4 * size
        cel(draw, Poly([(px - tx * hw, py - ty * hw), (px + nx * h - tx * hw * 0.9, py + ny * h - ty * hw * 0.9), (px + tx * hw, py + ty * hw)]),
            col, sh=None, lw=0.6)


def _hyd_plate(draw, x, y, w, h, col=HYD_FIN):
    """A spine seen from behind, standing on the back at (x, y)."""
    cel(draw, Poly([(x - w, y + 1.0), (x + 0.3, y - h), (x + w, y + 1.0)]), col, sh=(0.5, 0.0), lw=0.6)


def _hyd_foot(draw, x, y, col, d=0, toes=True):
    """A broad three-toed foot, flat on the ground (its claws used to hang under it)."""
    if d:
        shapes = [Ell(x + d * 1.0, y, 3.4, 1.8), Ell(x + d * 3.6, y + 0.5, 1.5, 1.2)]
    elif toes:
        shapes = [Ell(x, y, 3.3, 1.8), Ell(x - 2.5, y + 0.5, 1.5, 1.3), Ell(x + 2.5, y + 0.5, 1.5, 1.3)]
    else:
        shapes = [Ell(x, y, 3.3, 1.8)]
    blob(draw, shapes, col, sh=(0.0, 0.6))


def _hyd_skull(x, y, k):
    """A head seen head-on or from behind: a wedge, broad across the brow, narrowing to a blunt snout."""
    return Poly(arc_pts(x, y - 0.4 * k, 5.6 * k, 4.4 * k, 180, 360, 14)
                + _bez((x + 5.6 * k, y - 0.4 * k), (x + 5.2 * k, y + 4.6 * k), (x + 2.2 * k, y + 5.2 * k), 5)[1:]
                + _bez((x - 2.2 * k, y + 5.2 * k), (x - 5.2 * k, y + 4.6 * k), (x - 5.6 * k, y - 0.4 * k), 5)[:-1])


def _hyd_head(draw, x, y, d, k=1.0, col=HYD):
    """One head, facing the camera or in profile: a crest behind the skull, a gold eye under a heavy
    brow, nostrils, a fang or two."""
    fin = HYD_FIN if col == HYD else shade(HYD_FIN, 0.7)
    line = ink(col)
    if d:
        cel(draw, Poly([(x + d * 0.6 * k, y - 3.4 * k), (x - d * 6.8 * k, y - 6.6 * k), (x - d * 4.4 * k, y - 0.4 * k)]), fin, sh=None, lw=0.6)
        skull = [Ell(x, y, 5.2 * k, 4.4 * k), Ell(x + d * 5.2 * k, y + 1.3 * k, 4.6 * k, 3.0 * k)]
        blob(draw, skull, col, sh=(0.7, 0.7), regions=[(_Band(skull, (0.5, 0.5)), lit(col))])
        stroke(draw, [(x + d * 9.4 * k, y + 2.0 * k), (x + d * 5.0 * k, y + 2.9 * k), (x + d * 1.4 * k, y + 2.5 * k)], 0.7, line)
        Poly([(x + d * 6.4 * k, y + 2.5 * k), (x + d * 7.9 * k, y + 2.2 * k), (x + d * 7.3 * k, y + 4.5 * k)]).draw(draw, fill=TOOTH)
        Ell(x + d * 8.4 * k, y + 0.1 * k, 0.55, 0.45).draw(draw, fill=line)
        _hyd_eye(draw, x + d * 1.9 * k, y - 0.9 * k, 3.3 * k, 3.3 * k, -d, col)
        return
    cel(draw, Poly([(x - 2.6 * k, y - 3.0 * k), (x + 0.3 * k, y - 8.4 * k), (x + 2.6 * k, y - 3.0 * k)]), fin, sh=None, lw=0.6)
    cel(draw, _hyd_skull(x, y, k), col, sh=(0.7, 0.7), hi=(0.5, 0.5))
    for s in (-1, 1):
        _hyd_eye(draw, x + s * 2.7 * k, y - 0.7 * k, 3.2 * k, 3.4 * k, s, col)
        Ell(x + s * 1.0 * k, y + 2.5 * k, 0.5, 0.4).draw(draw, fill=line)
    stroke(draw, [(x - 3.0 * k, y + 3.5 * k), (x, y + 4.2 * k), (x + 3.0 * k, y + 3.5 * k)], 0.7, line)
    for s in (-1, 1):
        Poly([(x + s * 2.1 * k - 0.6, y + 3.7 * k), (x + s * 2.1 * k + 0.6, y + 3.7 * k), (x + s * 2.1 * k, y + 5.6 * k)]).draw(draw, fill=TOOTH)


def draw_hydra(draw, ox, oy, direction, frame):
    d, back, base, cx, ph = _setup(ox, oy, direction, frame)
    sway = [0.0, 1.0, 0.0, -1.0][frame]
    if d:
        # in profile the three heads fan out one above the other: the far side's reared highest
        # and darkest, the middle one out ahead, the near one low before the chest
        bx = cx - d * 2.6
        far = shade(HYD, 0.7)
        tail = _bez((bx - d * 7.0, base - 10.6), (bx - d * 16.6, base - 11.6 + sway * 0.6), (bx - d * 17.6, base - 2.8 + sway), 6)
        _hyd_spines(draw, tail, (2, 4), d, 2.6)
        cel(draw, Limb(tail, _HYD_TAIL_W, cap=False), HYD, sh=(0.6, 0.6))

        def leg(lx, phase, near):
            sw = ph * phase * (1.0 if near else -1.0) * 2.6
            lift = 1.2 if sw < 0 else 0.0
            off, up = (0.0, 0.0) if near else (2.0, 0.8)
            col = HYD if near else far
            x0 = bx + d * (lx + off)
            x1 = x0 + d * sw
            cel(draw, Limb([(x0, base - 9.0), (x0 + d * sw * 0.4, base - 5.0 - up), (x1, base - 2.2 - up - lift)], [3.2, 2.9, 2.6]), col,
                sh=(0.6, 0.0))
            _hyd_foot(draw, x1, base - 1.5 - up - lift, col, d)

        for near in (False, True):
            leg(6.4, 1.0, near)
            leg(-6.6, -1.0, near)
        # the far neck and head
        hx, hy = cx + d * (1.0 + sway * 0.8), base - 35.4
        neck = _bez((cx - d * 0.6, base - 16.0), (cx - d * 5.0, base - 27.0), (hx - d * 2.2, hy + 2.6), 6)
        _hyd_spines(draw, neck, (2, 4), -d, 3.2, col=shade(HYD_FIN, 0.7))
        limb = Limb(neck, _HYD_NECK_W)
        cel(draw, limb, far, sh=(0.6, 0.5),
            regions=_coat([limb], [(Limb([(x + d * 1.8, y + 0.4) for (x, y) in neck], [w * 0.55 for w in _HYD_NECK_W]), shade(HYD_BELLY, 0.7))], (0.6, 0.5)))
        _hyd_head(draw, hx, hy, d, 1.0, far)
        # the plates along the back, then the body and the two nearer necks as one mass
        spine = [(bx - d * 9.0, base - 15.6), (bx - d * 4.6, base - 19.2), (bx, base - 20.2), (bx + d * 4.0, base - 20.4), (bx + d * 8.0, base - 20.0)]
        _hyd_spines(draw, spine[::-1], (1, 2, 3), d, 0.6, 1.15)
        heads = [(cx + d * 12.0, base - 25.6 + sway, 1.05), (cx + d * 10.4, base - 14.4 - sway, 1.0)]
        paths = [_bez((cx + d * 3.4, base - 16.6), (cx + d * 5.4, base - 25.0), (heads[0][0] - d * 2.6, heads[0][1] + 2.2), 6),
                 _bez((cx + d * 3.6, base - 12.6), (cx + d * 6.8, base - 10.2), (heads[1][0] - d * 2.4, heads[1][1] + 2.0), 6)]
        mass = [Ell(bx, base - 11.6, 10.6, 7.8), Ell(bx + d * 5.6, base - 13.6, 6.0, 6.2)] + [Limb(p, _HYD_NECK_W) for p in paths]
        under = Poly([(bx + d * 16.0, base - 21.0), (bx + d * 11.4, base - 16.0), (bx + d * 8.0, base - 9.6), (bx - d * 3.0, base - 7.6), (bx - d * 8.0, base - 6.0),
                      (bx - d * 8.0, base), (bx + d * 16.0, base)])
        throats = [Limb([(x + d * off[0], y + off[1]) for (x, y) in p], [w * 0.55 for w in _HYD_NECK_W]) for p, off in zip(paths, ((1.3, 1.3), (0.4, 1.8)))]
        blob(draw, mass, HYD, sh=(1.4, 1.2), regions=_coat(mass, [(under, HYD_BELLY)] + [(t, HYD_BELLY) for t in throats], (1.4, 1.2)))
        for hx, hy, k in heads:
            _hyd_head(draw, hx, hy, d, k)
        return
    # head-on and from behind: a wide low body, the side necks curving out and up, the middle one
    # tallest; the tail lies on the ground to one side
    heads = [(-1, cx - 12.2, base - 27.4 + sway, 1.0), (1, cx + 12.2, base - 27.4 - sway, 1.0), (0, cx + sway * 0.6, base - 34.5, 1.15)]
    paths = [_bez((cx + s * 5.4, base - 14.6), (cx + s * 13.6, base - 15.0), (hx + s * 0.2, hy + 3.2), 6) if s else
             _bez((cx, base - 15.0), (cx + sway * 1.2, base - 24.0), (hx, hy + 3.6), 6) for (s, hx, hy, k) in heads]
    mass = [Ell(cx, base - 10.6, 12.0, 7.8)] + [Limb(p, _HYD_NECK_W) for p in paths]
    for s in (-1, 1):
        lift = 1.2 if ph * s < 0 else 0.0
        cel(draw, Limb([(cx + s * 8.6, base - 8.0), (cx + s * 9.0, base - 2.2 - lift)], [3.2, 2.8]), HYD, sh=(0.6, 0.0))
        _hyd_foot(draw, cx + s * 9.0, base - 1.5 - lift, HYD, toes=not back)
    if back:
        tail = _bez((cx + 0.5, base - 7.0), (cx + 15.6, base - 9.4 + sway * 0.5), (cx + 16.6 + sway * 0.8, base - 1.6), 6)
        for s, hx, hy, k in heads:
            cel(draw, _hyd_skull(hx, hy, k), HYD, sh=(0.7, 0.7), hi=(0.5, 0.5))
        blob(draw, mass, HYD, sh=(1.4, 1.2))
        # the spines, nearest last: down each neck from its crest, then down the back to the tail
        for (s, hx, hy, k), p in zip(heads, paths):
            _hyd_plate(draw, p[3][0], p[3][1], 2.2, 3.8)
            _hyd_plate(draw, hx, hy + 0.4 * k, 2.1 * k, 7.6 * k)
        for (v, w, h) in ((16.0, 2.7, 4.6), (11.6, 2.5, 4.0)):
            _hyd_plate(draw, cx, base - v, w, h)
        _hyd_spines(draw, tail, (2, 4), -1.0, 2.6)
        cel(draw, Limb(tail, _HYD_TAIL_W, cap=False, cap0=True), HYD, sh=(0.6, 0.6))
        return
    tail = _bez((cx + 7.0, base - 7.4), (cx + 19.4, base - 9.6 + sway * 0.5), (cx + 17.6 + sway * 0.8, base - 1.6), 6)
    _hyd_spines(draw, tail, (2, 4), -1.0, 2.6)
    cel(draw, Limb(tail, _HYD_TAIL_W, cap=False), HYD, sh=(0.6, 0.6))
    belly = Poly([(cx - 4.6, base - 19.0), (cx + 4.6, base - 19.0), (cx + 6.8, base - 11.0), (cx + 4.8, base - 4.8), (cx, base - 3.2), (cx - 4.8, base - 4.8),
                  (cx - 6.8, base - 11.0)])
    throats = [Limb(p, [w * 0.46 for w in _HYD_NECK_W]) for p in paths]
    blob(draw, mass, HYD, sh=(1.4, 1.2), regions=_coat(mass, [(belly, HYD_BELLY)] + [(t, HYD_BELLY) for t in throats], (1.4, 1.2)))
    for (v, w) in ((14.6, 4.4), (10.6, 5.6), (6.8, 4.4)):
        stroke(draw, [(cx - w, base - v - 0.5), (cx, base - v + 0.6), (cx + w, base - v - 0.5)], 0.7, shade(HYD_BELLY, 1.2))
    for s, hx, hy, k in heads:
        _hyd_head(draw, hx, hy, 0, k)


# ===================================================================
# MANTIS (83) -- bug eyes, a triangle face, scythe arms raised to strike
# ===================================================================

# A yellower lime than the grass it hunts in, a cream underside and cutting
# edge, dark green legs: on the Meadow the old body was the ground's own colour.
MAN = (164, 214, 84)
MAN_DK = (74, 136, 58)
MAN_BELLY = (244, 236, 158)
MAN_WING = (206, 238, 166)
MAN_EDGE = (248, 250, 208)
MAN_EYE = (250, 120, 60)


def _man_scythe(draw, sh, el, tip, out, near=True):
    """One raptorial foreleg: the upper arm out to the elbow, then the blade hooking back from it --
    a sickle bowed toward `out`, green along its spine and pale down its toothed cutting edge."""
    col = MAN if near else shade(MAN, 1.0)
    dx, dy = tip[0] - el[0], tip[1] - el[1]
    ln = math.hypot(dx, dy) or 1.0
    nx, ny = -dy / ln, dx / ln
    if nx * out[0] + ny * out[1] < 0:
        nx, ny = -nx, -ny
    mid = ((el[0] + tip[0]) / 2.0, (el[1] + tip[1]) / 2.0)

    def curve(k):
        return _bez(tip, (mid[0] + nx * k, mid[1] + ny * k), el, 7)

    edge = [(x - nx * (1.0 if i % 2 else 0.0), y - ny * (1.0 if i % 2 else 0.0)) for i, (x, y) in enumerate(curve(1.0))]
    shapes = [Limb([sh, el], [2.0, 1.9], cap0=True), Ell(el[0], el[1], 2.1, 2.1), Poly(curve(8.6)[::-1] + edge[1:-1])]
    blob(draw, shapes, col, sh=(0.6, 0.6),
         regions=[(Poly(edge + curve(5.4)[::-1][1:-1]), MAN_EDGE if near else shade(MAN_EDGE, 1.2))])


def _man_leg(draw, hip, knee, foot, near=True):
    cel(draw, Limb([hip, knee, foot], [1.3, 1.1, 0.6]), MAN_DK if near else shade(MAN_DK, 0.8), sh=None, lw=0.6)


def _man_antennae(draw, hx, hy, d, step):
    for s in (-1, 1):
        bx = hx + s * 1.6 - d * 0.6
        tip = (bx + (s * 5.6 if not d else -d * 3.4 + s * 2.0), hy - 12.4 + (step if s > 0 else -step) * 0.6)
        stroke(draw, [(bx, hy - 3.0), ((bx + tip[0]) / 2 + (s * 1.4 if not d else d * 1.0), hy - 9.0), tip], 0.8, MAN_DK)


def draw_mantis(draw, ox, oy, direction, frame):
    d, back, base, cx, ph = _setup(ox, oy, direction, frame)
    step = [0.0, 1.0, 0.0, -1.0][frame]
    if d:
        # the praying pose in profile: the thorax upright, the long abdomen and its leaf of wings
        # out behind, the scythes held up before the face, four legs bent high at the knee
        def legs(near):
            k = step if near else -step
            off = 0.0 if near else 1.8
            for (hip, knee, foot, w) in (((-2.0, -12.0), (2.6, -16.4), (4.4, -0.6), 1), ((-5.0, -11.4), (-9.6, -15.6), (-12.6, -0.6), -1)):
                sw = k * w * 2.0
                lift = 1.0 if sw > 0 else 0.0
                _man_leg(draw, (cx + d * (hip[0] + off), base + hip[1]), (cx + d * (knee[0] + off + sw * 0.5), base + knee[1] - (0.0 if near else 0.8)),
                         (cx + d * (foot[0] + off + sw), base + foot[1] - lift - (0.0 if near else 1.0)), near)

        def scythe(near):
            k = (step if near else -step) * 0.8
            off = 0.0 if near else 2.6
            _man_scythe(draw, (cx + d * 3.0, base - 21.6), (cx + d * (10.4 + off), base - 22.6 - off * 0.8 + k),
                        (cx + d * (7.4 + off), base - 8.6 - off * 0.8 + k), (d, 0.0), near)

        legs(False)
        scythe(False)
        legs(True)
        acx, acy = cx - d * 7.4, base - 11.2
        ab = Poly(_rot(arc_pts(acx, acy, 8.4, 4.0, 0, 360)[:-1], acx, acy, d * 14))
        cel(draw, ab, MAN, sh=(1.0, 1.0), regions=[(_Band([ab], (0.0, -1.8)), MAN_BELLY)])
        wing = Poly([(cx - d * 0.2, base - 18.6)] + _bez((cx - d * 5.0, base - 18.4), (cx - d * 13.0, base - 16.0), (cx - d * 17.4, base - 9.4), 6) +
                    _bez((cx - d * 13.0, base - 10.6), (cx - d * 7.0, base - 13.6), (cx - d * 0.4, base - 14.0), 5))
        cel(draw, wing, MAN_WING, sh=(0.6, 0.8))
        stroke(draw, [(cx - d * 2.4, base - 16.4), (cx - d * 9.0, base - 15.0), (cx - d * 15.0, base - 11.0)], 0.6, MAN_DK)
        thorax = Limb([(cx - d * 1.6, base - 12.4), (cx + d * 0.8, base - 19.0), (cx + d * 2.8, base - 25.6)], [3.6, 3.0, 2.4])
        cel(draw, thorax, MAN, sh=(0.8, 0.6),
            regions=[(Limb([(cx + d * 0.6, base - 12.4), (cx + d * 3.0, base - 19.0), (cx + d * 5.0, base - 25.6)], [2.0, 1.8, 1.6]), MAN_BELLY)])
        hx, hy = cx + d * 4.2, base - 29.4
        far = Ell(hx + d * 4.0, hy - 3.2, 1.9, 2.1)
        cel(draw, far, shade(MAN, 0.6), sh=None, regions=[(_Band([far], (-d * 1.4, 0.4)), shade(MAN_EYE, 0.6))])
        head = Poly([(hx - d * 3.8, hy - 2.6), (hx + d * 3.0, hy - 4.6), (hx + d * 6.6, hy + 1.4), (hx + d * 5.0, hy + 5.6),
                     (hx + d * 2.8, hy + 5.4), (hx - d * 3.2, hy + 1.2)])
        cel(draw, head, MAN, sh=(0.8, 0.8))
        stroke(draw, [(hx + d * 3.0, hy + 4.0), (hx + d * 5.2, hy + 3.4)], 0.6, MAN_DK)
        _man_antennae(draw, hx, hy - 1.0, d, step)
        cel(draw, Ell(hx - d * 0.4, hy - 2.4, 3.8, 3.6), MAN, sh=None)
        ms_eye(draw, hx - d * 0.2, hy - 2.2, 4.6, 5.0, MAN_EYE, (float(d), 0.0), "bright", skin=MAN, side=-d, lash=(40, 70, 30))
        scythe(True)
        return
    # head-on and from behind
    for (hip, knee, foot, near, w) in (((2.4, -9.0), (8.4, -11.6), (10.4, -0.7), False, -1), ((2.6, -12.0), (10.6, -16.4), (14.0, -0.7), True, 1)):
        for s in (-1, 1):
            sw = step * w * s
            lift = 1.0 if sw > 0 else 0.0
            _man_leg(draw, (cx + s * hip[0], base + hip[1]), (cx + s * knee[0], base + knee[1] - sw * 0.6), (cx + s * foot[0], base + foot[1] - lift), near)

    def scythes():
        for s in (-1, 1):
            k = step * s * 0.8
            _man_scythe(draw, (cx + s * 3.0, base - 22.4), (cx + s * 9.6, base - 23.4 + k), (cx + s * 6.4, base - 8.4 + k), (s, 0.0))

    hx, hy = cx, base - 29.0
    thorax = Limb([(cx, base - 12.0), (cx, base - 25.6)], [3.8, 2.6])
    if back:
        # from behind: the scythes beyond the body, and the folded wings as one long leaf down its back
        cel(draw, thorax, MAN, sh=(0.8, 0.6))
        scythes()
        wing = Poly([(cx - 3.4, base - 21.4), (cx + 3.4, base - 21.4)] + _bez((cx + 6.4, base - 15.0), (cx + 6.0, base - 7.0), (cx, base - 1.8), 6) +
                    _bez((cx, base - 1.8), (cx - 6.0, base - 7.0), (cx - 6.4, base - 15.0), 6)[1:])
        cel(draw, wing, MAN_WING, sh=(1.0, 1.0))
        stroke(draw, [(cx + 0.4, base - 20.6), (cx - 0.2, base - 3.4)], 0.8, MAN_DK)
        for s in (-1, 1):
            for v in (-16.0, -11.0):
                stroke(draw, [(cx + s * 0.6, base + v), (cx + s * 4.0, base + v + 3.4)], 0.5, MAN_DK)
    else:
        cel(draw, Ell(cx, base - 8.6, 5.4, 6.6), MAN, sh=(1.0, 1.0), regions=[(Ell(cx, base - 6.4, 3.0, 4.2), MAN_BELLY)])
        cel(draw, thorax, MAN, sh=(0.8, 0.6), regions=[(Limb([(cx, base - 12.0), (cx, base - 25.6)], [1.9, 1.3]), MAN_BELLY)])
        scythes()
    # head: a triangle with huge bulb eyes on its corners
    cel(draw, Poly([(hx - 8.0, hy - 3.4), (hx + 8.0, hy - 3.4), (hx + 1.6, hy + 6.0), (hx - 1.6, hy + 6.0)]), MAN, sh=(0.8, 0.8), hi=(0.4, 0.4))
    for s in (-1, 1):
        ex = hx + s * 7.0
        bulb = Ell(ex, hy - 3.4, 3.6, 3.4)
        if back:
            # the eyes wrap round the corners of the head: from behind their outer rims still show
            cel(draw, bulb, MAN, sh=None, regions=[(_Band([bulb], (-s * 1.6, 0.6)), shade(MAN_EYE, 0.6))])
        else:
            cel(draw, bulb, MAN, sh=None)
            ms_eye(draw, ex, hy - 3.2, 4.6, 5.0, MAN_EYE, (0.0, 0.0), "bright", skin=MAN, side=s, lash=(40, 70, 30))
    _man_antennae(draw, hx, hy, 0, step)
    if not back:
        stroke(draw, [(hx - 1.4, hy + 4.2), (hx, hy + 4.8), (hx + 1.4, hy + 4.2)], 0.6, MAN_DK)


# ===================================================================
# JELLYFISH (84) -- a pink bell with a face, frilly hem, trailing tentacles
# ===================================================================

JELLY = (236, 150, 220)
JELLY_LT = (255, 206, 244)
JELLY_DK = (176, 96, 180)
JELLY_GLOW = (255, 250, 150)
JELLY_RIM = (200, 112, 198)         # the bell's margin, the frill the lights are strung along
JELLY_AMBER = (232, 160, 40)        # the line round a spark (the kit's bolt lines it in an olive: brown twigs)


def _jel_spark(draw, pts):
    """A crackle of current off the jellyfish: one width along its length, pale yellow lined in
    amber, a white core, a glint where it ends."""
    cel(draw, Limb(pts, 1.0, cap0=True), JELLY_GLOW, sh=None, line_color=JELLY_AMBER, lw=0.6)
    stroke(draw, pts, 0.5, (255, 255, 255))
    sparkle(draw, pts[-1][0], pts[-1][1], 1.9, JELLY_GLOW)


def draw_jellyfish(draw, ox, oy, direction, frame):
    d, back, base, cx, ph = _setup(ox, oy, direction, frame, bob=[0, -2, -3, -1])
    wave = frame * math.pi / 2.0
    # the pulse: the bell draws in and stands tall as it rises, flares as it sinks
    squeeze = [0.0, 1.0, 0.4, -0.6][frame]
    bw, bh = 12.6 - squeeze * 1.4, 13.0 + squeeze * 1.0
    bell_y = base - 24.0
    # in profile the bell leans into the way it goes and everything under it trails, more on the
    # strides than at rest
    tilt = d * (9.0, 15.0, 10.0, 15.0)[frame]
    trail = (3.4, 7.0, 4.4, 7.0)[frame]

    def at(u, v):
        """A point of the bell, `u` across and `v` down from the line of its hem, leaning as it leans."""
        return _rot([(cx + u, bell_y + v)], cx, bell_y, tilt)[0]

    def tentacle(u, length, w0, col, k, frill=False):
        rx, ry = at(u * bw / 12.6, 1.4)
        n = 6
        pts, widths = [], []
        for j in range(n + 1):
            t = j / float(n)
            pts.append((rx + math.sin(wave + k + t * 3.4) * (0.4 + 2.1 * t) - d * t * t * trail, ry + t * length))
            widths.append(w0 * (1.0 - 0.55 * t) * ((1.25 if j % 2 else 0.8) if frill else 1.0))
        cel(draw, Limb(pts, widths), col, sh=(0.5, 0.0) if frill else None, lw=0.6)

    # the inside of the bell, dark under the hem, and what hangs from it: four tentacles, the outer
    # pair shorter, and two frilled arms before them in the middle
    cel(draw, Poly(_rot(arc_pts(cx, bell_y + 0.8, bw - 1.0, 3.0, 0, 360)[:-1], cx, bell_y, tilt)), shade(JELLY_DK, 1.2), sh=None)
    for (u, length, col, k) in ((-9.0, 15.0, JELLY_DK, 0.0), (9.0, 15.0, JELLY_DK, 0.9), (-5.4, 20.5, JELLY, 0.5), (5.4, 20.5, JELLY, 1.3)):
        tentacle(u, length, 1.5, col, k)
    for (u, k) in ((-2.0, 0.3), (2.0, 1.0)):
        tentacle(u, 13.0, 2.2, JELLY_LT, k, frill=True)
    # the bell: a dome over a scalloped hem
    n = 5
    hem = []
    for i in range(n):
        x1 = cx + bw - 2.0 * bw * i / n
        hem += arc_pts(x1 - bw / n, bell_y, bw / n, 3.0, 0, 180, 6)[1:]
    bell = Poly(_rot(arc_pts(cx, bell_y, bw, bh, 180, 360, 30) + hem[:-1], cx, bell_y, tilt))
    margin = Poly([at(-16.0, -1.2), at(16.0, -1.2), at(16.0, 5.0), at(-16.0, 5.0)])
    gx, gy = at(-5.4, -8.6)
    cel(draw, bell, JELLY, sh=(1.6, 1.4), hi=(1.0, 1.0), regions=_coat([bell], [(margin, JELLY_RIM)], (1.6, 1.4)) + [(Ell(gx, gy, 3.3, 1.9), JELLY_LT)])
    # lights strung along the margin, one a scallop, running round it as it swims
    for i in range(n):
        lx, ly = at(bw - 2.0 * bw * (i + 0.5) / n, 0.9)
        Ell(lx, ly, 1.3, 1.3).draw(draw, fill=JELLY_GLOW if (i + frame) % 2 == 0 else (250, 208, 104))
    iris, lash = (120, 40, 110), (110, 40, 100)
    blush = mix(JELLY, (255, 104, 140), 0.5)
    if back:
        # from behind: the four-leaved flower a jellyfish carries on the crown of its bell
        for (u, v) in ((-2.3, -9.6), (2.3, -9.6), (-2.3, -6.0), (2.3, -6.0)):
            Ell(cx + u, bell_y + v, 2.3, 1.9).draw(draw, fill=JELLY_LT)
        Ell(cx, bell_y - 7.8, 1.5, 1.4).draw(draw, fill=JELLY_GLOW)
    elif d:
        (x1, y1), (x2, y2) = at(d * 3.2, -5.8), at(d * 8.0, -5.4)
        bx, by = at(d * 0.2, -3.0)
        Ell(bx, by, 1.7, 0.8).draw(draw, fill=blush)
        ms_eye(draw, x2, y2, 4.0 * 0.7, 5.0 * 0.94, iris, (float(d), 0.0), "bright", skin=JELLY, side=d, lash=lash)
        ms_eye(draw, x1, y1, 4.0, 5.0, iris, (float(d), 0.0), "bright", skin=JELLY, side=-d, lash=lash)
        mx, my = at(d * 6.6, -2.2)
        ms_mouth(draw, mx, my, "cat", JELLY, 0.8)
    else:
        for s in (-1, 1):
            Ell(cx + s * 7.6, bell_y - 3.2, 1.8, 0.85).draw(draw, fill=blush)
        _eyes(draw, cx - 4.2, cx + 4.2, bell_y - 6.0, 0, iris, mood="bright", skin=JELLY, w=4.0, h=5.0, lash=lash)
        ms_mouth(draw, cx, bell_y - 2.4, "cat", JELLY, 0.9)
    # a crackle of electricity on the strides, from the hem on one side and among the tentacles on the other
    if frame % 2 == 1:
        k = 1 if frame == 1 else -1
        x0, y0 = at(-k * (bw - 0.4), 3.4)
        _jel_spark(draw, [(x0, y0), (x0 - k * 3.0, y0 + 2.4), (x0 - k * 1.4, y0 + 4.2), (x0 - k * 4.2, y0 + 7.2)])
        x1, y1 = cx + k * 8.4 - d * trail * 0.5, bell_y + 12.6
        _jel_spark(draw, [(x1, y1), (x1 + k * 3.2, y1 + 1.6), (x1 + k * 1.8, y1 + 3.6), (x1 + k * 4.8, y1 + 5.6)])


# ===================================================================
# GORILLA (85) -- knuckle-walking: huge arms, a silver back, a heavy brow
# ===================================================================

# The fur is dark and the bare skin a few steps lighter, as it always was; what
# keeps the face the first thing seen is the darkest shape (the brow) set on
# the lightest (the face) with amber eyes between, a sheen on the fur's lit
# side to give the arms and the skull a side, and the silver saddle.
GOR = (70, 67, 88)
GOR_LT = (104, 100, 130)
GOR_FACE = (172, 152, 158)
GOR_HAND = (150, 136, 148)
GOR_CHEST = (128, 116, 132)
GOR_SILVER = (184, 188, 204)
GOR_BROW = (46, 42, 58)
GOR_EYE = (255, 184, 52)
GOR_INK = ink(GOR)       # bare skin is lined in the fur's ink: its own would be a raw pink


def _gor_head(draw, hx, hy, d, back=False):
    """The head: a crested cranium and the bare face set in it -- a brow shelf over amber eyes and a
    muzzle that stands out past the skull in profile."""
    skull = [Ell(hx, hy, 8.6 if not d else 7.8, 7.6), Ell(hx - d * 2.4, hy - 5.2, 5.6, 3.8)]
    blob(draw, skull, GOR, sh=(1.2, 1.0), regions=[(_Band(skull, (1.7, 1.7)), GOR_LT)])
    if back:
        return
    dark = shade(GOR_FACE, 3.0)
    if d:
        fx = hx + d * 3.2
        blob(draw, [Ell(fx, hy + 0.6, 4.6, 3.8), Ell(fx + d * 2.8, hy + 3.6, 4.4, 3.2)], GOR_FACE, sh=(0.6, 0.7), line_color=GOR_INK)
        _eyes(draw, fx + d * 0.4, fx + d * 3.5, hy + 1.0, d, GOR_EYE, mood="sharp", skin=GOR_FACE, w=3.4, h=3.8)
        cel(draw, Poly([(fx - d * 2.6, hy - 2.6), (fx + d * 2.6, hy - 3.9), (fx + d * 5.4, hy - 2.8), (fx + d * 5.0, hy - 0.7),
                        (fx + d * 0.6, hy - 0.3), (fx - d * 2.4, hy - 1.2)]), GOR_BROW, sh=None)
        Ell(fx + d * 5.8, hy + 2.8, 0.8, 0.6).draw(draw, fill=dark)
        stroke(draw, [(fx + d * 2.6, hy + 5.4), (fx + d * 6.6, hy + 5.0)], 0.7, dark)
        return
    fx = hx
    blob(draw, [Ell(fx, hy + 0.4, 5.8, 3.6), Ell(fx, hy + 3.4, 4.8, 3.2)], GOR_FACE, sh=(0.7, 0.6), line_color=GOR_INK)
    _eyes(draw, fx - 3.0, fx + 3.0, hy + 1.0, 0, GOR_EYE, mood="sharp", skin=GOR_FACE, w=3.6, h=3.8)
    cel(draw, Poly([(fx - 6.8, hy - 2.8), (fx - 3.4, hy - 4.2), (fx, hy - 3.2), (fx + 3.4, hy - 4.2), (fx + 6.8, hy - 2.8),
                    (fx + 6.4, hy - 0.8), (fx + 3.0, hy - 1.4), (fx, hy - 0.2), (fx - 3.0, hy - 1.4), (fx - 6.4, hy - 0.8)]),
        GOR_BROW, sh=None)
    for s in (-1, 1):
        Ell(fx + s * 1.3, hy + 3.4, 0.8, 0.6).draw(draw, fill=dark)
    stroke(draw, [(fx - 2.8, hy + 5.5), (fx, hy + 5.1), (fx + 2.8, hy + 5.5)], 0.7, dark)


def _gor_fist(draw, kx, ky, near=True, back=False):
    col = GOR_HAND if near and not back else shade(GOR_HAND, 1.2)
    cel(draw, Ell(kx, ky, 4.0, 2.8), col, sh=(0.6, 0.8), line_color=GOR_INK)
    if near and not back:
        for k in (-1.3, 1.3):
            stroke(draw, [(kx + k, ky - 1.7), (kx + k, ky + 0.2)], 0.6, shade(GOR_HAND, 2.4))


def draw_gorilla(draw, ox, oy, direction, frame):
    d, back, base, cx, ph = _setup(ox, oy, direction, frame)
    if d:
        # knuckle-walking in profile: a high shoulder hump, the back sloping down to the rump, the
        # head low and forward, long arms planted ahead of short bent legs
        bx = cx - d * 3.0
        arm_sw, leg_sw = ph * 3.0, -ph * 2.6

        def hind(near):
            sw = leg_sw if near else -leg_sw
            off = 0.0 if near else 2.6
            lift = 1.0 if sw < 0 else 0.0
            col = GOR if near else shade(GOR, 0.7)
            fx = bx - d * (7.4 - off - sw)
            cel(draw, Limb([(bx - d * (8.4 - off), base - 11.0), (bx - d * (6.6 - off - sw * 0.4), base - 6.4),
                            (fx - d * 0.8, base - 2.8 - lift)], [3.6, 3.0, 2.6]), col, sh=(0.8, 0.4))
            cel(draw, Ell(fx + d * 0.8, base - 1.4 - lift, 3.4, 1.7), shade(GOR_HAND, 0.8 if near else 1.8), sh=(0.0, 0.5),
                line_color=GOR_INK)

        def arm(near):
            sw = arm_sw if near else -arm_sw
            off = 0.0 if near else 2.8
            lift = 1.0 if sw < 0 else 0.0
            col = GOR if near else shade(GOR, 0.7)
            kx = bx + d * (7.0 + off + sw)
            limb = Limb(_bez((bx + d * (3.0 + off * 0.6), base - 21.4), (bx + d * (6.0 + off + sw * 0.3), base - 12.0),
                                 (kx - d * 0.6, base - 4.6 - lift), 5), [5.0, 4.8, 4.5, 4.2, 3.9, 3.6], cap0=True)
            if near:
                cel(draw, limb, col, sh=(1.2, 0.8), hi=(1.7, 1.5), hi_tone=GOR_LT)
            else:
                cel(draw, limb, col, sh=(1.2, 0.8))
            _gor_fist(draw, kx, base - 2.9 - lift, near)

        hind(False)
        arm(False)
        trunk = [Ell(bx + d * 1.0, base - 17.4, 8.6, 8.4), Ell(bx - d * 8.0, base - 12.8, 6.6, 6.4)]
        # the silver saddle: a band along the top of the back, from behind the shoulder to the rump
        saddle = Poly([(bx - d * 16.0, base - 10.4), (bx - d * 11.4, base - 11.0), (bx - d * 6.4, base - 14.0), (bx - d * 2.0, base - 17.8),
                       (bx + d * 0.6, base - 20.0), (bx + d * 0.6, base - 30.0), (bx - d * 16.0, base - 30.0)])
        blob(draw, trunk, GOR, sh=(1.4, 1.2), regions=[(saddle, GOR_SILVER), (_Band([saddle], (-0.4, -1.5)), shade(GOR_SILVER, 1.2))])
        hind(True)
        _gor_head(draw, bx + d * 10.4, base - 25.4, d)
        arm(True)
        return
    # head-on and from behind: the shoulders as wide as it is tall, arms bowed out to the knuckles
    for s in (-1, 1):
        fwd = ph * s
        lift = 1.2 if fwd < 0 else 0.0
        lx = cx + s * 4.8
        cel(draw, Limb([(lx - s * 0.6, base - 10.0), (lx, base - 2.4 - lift)], [3.2, 2.8]), shade(GOR, 0.6), sh=(0.6, 0.0))
        cel(draw, Ell(lx, base - 1.3 - lift, 3.2, 1.7), shade(GOR_HAND, 1.8), sh=(0.0, 0.5), line_color=GOR_INK)
    trunk = [Ell(cx, base - 17.6, 12.0, 9.4), Ell(cx, base - 11.6, 9.4, 6.8)]
    if back:
        # the silver saddle: broad across the shoulder blades, narrowing to the small of the back
        saddle = Poly([(cx - 8.6, base - 23.0), (cx + 8.6, base - 23.0), (cx + 8.0, base - 15.0), (cx + 5.4, base - 10.4), (cx, base - 9.0),
                       (cx - 5.4, base - 10.4), (cx - 8.0, base - 15.0)])
        blob(draw, trunk, GOR, sh=(1.6, 1.4), regions=[(saddle, GOR_SILVER), (_Band([saddle], (-1.4, -1.4)), shade(GOR_SILVER, 1.2))])
        stroke(draw, [(cx, base - 19.0), (cx, base - 10.6)], 0.8, shade(GOR_SILVER, 2.2))
        _gor_head(draw, cx, base - 27.0, 0, back=True)
    else:
        blob(draw, trunk, GOR, sh=(1.6, 1.4))
        blob(draw, [Ell(cx - 3.9, base - 17.0, 4.6, 3.6), Ell(cx + 3.9, base - 17.0, 4.6, 3.6)], GOR_CHEST, sh=(0.8, 0.9),
             line_color=GOR_INK)
        stroke(draw, [(cx, base - 18.6), (cx, base - 14.4)], 0.7, shade(GOR_CHEST, 2.0))
    for s in (-1, 1):
        fwd = -ph * s
        drop = 0.5 if fwd > 0 else (-1.4 if fwd < 0 else 0.0)
        kx, ky = cx + s * 12.6, base - 2.9 + drop
        cel(draw, Limb(_bez((cx + s * 10.2, base - 22.4), (cx + s * 15.8, base - 13.0 + drop * 0.5), (kx, ky - 1.6), 5),
                       [5.2, 4.9, 4.6, 4.2, 3.9, 3.6], cap0=True), GOR, sh=(1.2, 0.8), hi=(1.7, 1.5), hi_tone=GOR_LT)
        _gor_fist(draw, kx, ky, back=back)
    if not back:
        _gor_head(draw, cx, base - 27.0, 0)


# ===================================================================
# CHAMELEON (86) -- turret eyes, a curled tail, colours that shift
# ===================================================================

# A lizard, not a frog: a narrow head under a tall casque, a turret eye bulging off either side of
# it and each looking its own way, thin bent legs on mitten feet, an arched and banded back, the
# tail wound into a spiral. The green is an emerald, cooler than the Meadow's grass; as it walks
# the green washes toward teal and the bands from yellow to coral, and back, one step a frame: a
# pulse, where the old order jumped both ways across the wheel and strobed.
CHAM = ((70, 184, 98), (66, 182, 122), (64, 178, 146), (66, 182, 122))
CHAM_BAND = ((252, 216, 78), (255, 180, 84), (255, 146, 104), (255, 180, 84))
CHAM_PUPIL = (40, 30, 44)
CHAM_TONGUE = (244, 110, 142)


def _cham_eye(draw, ex, ey, rad, look, col, ring):
    """A turret eye: a cone of scaly lid standing off the head, ringed in the band colour, and at
    its tip a small dark pupil that points wherever `look` says."""
    cel(draw, Ell(ex, ey, rad, rad), shade(col, 0.5), sh=(0.5, 0.5))
    Ell(ex + look[0] * rad * 0.2, ey + look[1] * rad * 0.2, rad * 0.7, rad * 0.7).draw(draw, fill=ring)
    px, py = ex + look[0] * rad * 0.38, ey + look[1] * rad * 0.38
    Ell(px, py, rad * 0.41, rad * 0.41).draw(draw, fill=CHAM_PUPIL)
    Ell(px - rad * 0.14, py - rad * 0.15, 0.7, 0.7).draw(draw, fill=(255, 255, 255))


def _cham_leg(draw, hip, knee, foot, col, d=0):
    """A thin leg bent at the knee, on a mitten of a foot: two bunches of toes gripping either
    way, flat on the ground (the old toes and claws hung under it)."""
    cel(draw, Limb([hip, knee, (foot[0], foot[1] - 0.8)], [2.0, 1.7, 1.4]), col, sh=(0.5, 0.0))
    if d:
        toes = [Ell(foot[0] + d * 1.3, foot[1], 2.1, 1.3), Ell(foot[0] - d * 1.3, foot[1] + 0.1, 1.6, 1.2)]
    else:
        toes = [Ell(foot[0] - 1.3, foot[1], 1.8, 1.3), Ell(foot[0] + 1.3, foot[1], 1.8, 1.3)]
    blob(draw, toes, col, sh=(0.0, 0.5))


def _cham_tail(draw, root, x, y, rad, flip, col, start=270.0, turns=1.15):
    """The tail: from the rump at `root` into a spiral wound about (x, y), tapering to its tip at
    the middle, with clear ground between the coils so it is a curl at game size and not a knob."""
    n = 14
    pts, widths = [root], [2.5]
    for i in range(n + 1):
        t = i / float(n)
        a = math.radians(start - 360.0 * turns * t)
        r = rad * (1.0 - 0.8 * t)
        pts.append((x + flip * math.cos(a) * r, y + math.sin(a) * r))
        widths.append(2.0 - 1.15 * t)
    cel(draw, Limb(pts, widths), col, sh=(0.5, 0.5))


def _cham_crest(draw, pts, at, col, h=2.7, hw=1.6):
    """The saw along the spine: a tooth standing up off the line `pts` at each index in `at`,
    drawn before the back that hides its foot."""
    for i in at:
        (ax, ay), (bx, by) = pts[max(0, i - 1)], pts[min(len(pts) - 1, i + 1)]
        ln = math.hypot(bx - ax, by - ay) or 1.0
        tx, ty = (bx - ax) / ln, (by - ay) / ln
        nx, ny = (ty, -tx) if tx > 0 else (-ty, tx)
        px, py = pts[i]
        cel(draw, Poly([(px - tx * hw - nx, py - ty * hw - ny), (px + nx * h, py + ny * h), (px + tx * hw - nx, py + ty * hw - ny)]),
            col, sh=None, lw=0.6)


def _cham_casque(hx, hy):
    """The casque seen head-on or from behind: a tall arch over the skull, and its far half, which
    is in shade the other side of the ridge down its middle."""
    arch = Poly(_bez((hx - 6.6, hy - 3.0), (hx - 5.8, hy - 11.8), (hx + 0.2, hy - 15.8), 7) +
                _bez((hx + 0.2, hy - 15.8), (hx + 6.2, hy - 11.8), (hx + 6.6, hy - 3.0), 7)[1:])
    far = Poly([(hx + 0.2, hy - 17.0), (hx + 9.0, hy - 17.0), (hx + 9.0, hy - 5.0), (hx + 0.9, hy - 4.2)])
    return arch, far


def draw_chameleon(draw, ox, oy, direction, frame):
    d, back, base, cx, ph = _setup(ox, oy, direction, frame, bob=[0, 0, 0, 0])
    col, band = CHAM[frame], CHAM_BAND[frame]
    belly = mix(lit(col, 1.3), (246, 240, 176), 0.5)
    crest = shade(col, 1.3)
    line = ink(col)
    by = base + [0.0, -0.8, 0.0, -0.8][frame]       # the body rides the stride; the feet stay down
    sway = [0.0, 0.7, 0.0, -0.7][frame]
    if d:
        cx -= d * 1.6       # a little back of the middle: the head and its tongue lead
        _cham_tail(draw, (cx - d * 9.4, by - 14.0), cx - d * 15.0, by - 9.8 + sway, 5.6, d, col)

        def leg(u, bend, phase, near):
            sw = ph * phase * (1.0 if near else -1.0) * 2.8
            lift = 1.2 if sw < 0 else 0.0
            off, up = (0.0, 0.0) if near else (2.8 if u > 0 else -2.6, 0.9)
            x0 = cx + d * (u + off)
            _cham_leg(draw, (x0, by - 9.4), (x0 + d * (bend + sw * 0.4), by - 5.2 - up), (x0 + d * (sw - bend * 0.3), base - 1.3 - up - lift),
                      col if near else shade(col, 0.8), d)

        leg(4.0, -1.5, 1.0, False)
        leg(-6.0, 1.5, -1.0, False)
        # the body: a leaf on its edge, the back arched high, the saw of the crest along it
        top = _bez((cx + d * 6.6, by - 17.0), (cx - d * 3.0, by - 28.4), (cx - d * 11.4, by - 13.4), 10)
        bot = _bez((cx - d * 11.4, by - 13.4), (cx - d * 3.0, by - 3.0), (cx + d * 7.0, by - 9.6), 8)
        _cham_crest(draw, top[::d], (2, 4, 6, 8), crest)
        body = Poly(top + bot[1:-1])
        # saddle stripes: down from the spine and no further than the flank, so the lizard stays green
        bars = [Limb([(cx + d * (u - 1.2), by - 27.0), (cx + d * (u + 0.5), by + v)], 1.2) for (u, v) in ((-6.6, -12.4), (-1.4, -11.8))]
        cel(draw, body, col, sh=(1.2, 1.2), regions=_coat([body], [(b, band) for b in bars] + [(_Band([body], (0.0, -3.2)), belly)], (1.2, 1.2)))
        leg(4.0, -1.5, 1.0, True)
        leg(-6.0, 1.5, -1.0, True)
        # the head: a blunt snout, the casque swept up and back off the skull, the turret eye on its side
        hx, hy = cx + d * 10.0, by - 19.8
        casque = Poly(_bez((hx + d * 5.0, hy - 3.4), (hx - d * 0.4, hy - 7.0), (hx - d * 6.8, hy - 12.4), 6) +
                      _bez((hx - d * 6.8, hy - 12.4), (hx - d * 9.2, hy - 4.6), (hx - d * 5.4, hy + 3.0), 6)[1:])
        skull = [Ell(hx, hy, 6.8, 6.0), Ell(hx + d * 5.0, hy + 2.0, 4.8, 4.2), casque]
        jaw = [(hx + d * 9.8, hy + 2.4), (hx + d * 4.4, hy + 3.3), (hx - d * 0.6, hy + 2.6)]
        throat = Poly(jaw + [(hx - d * 8.0, hy + 3.0), (hx - d * 8.0, hy + 9.0), (hx + d * 11.0, hy + 9.0)])
        blob(draw, skull, col, sh=(0.9, 0.9), regions=_coat(skull, [(throat, belly)], (0.9, 0.9)))
        stroke(draw, jaw, 0.8, line)
        Ell(hx + d * 8.4, hy - 0.2, 0.55, 0.5).draw(draw, fill=line)
        look = ((0.9, 0.1), (0.5, -0.7), (0.9, 0.2), (-0.7, -0.5))[frame]
        _cham_eye(draw, hx + d * 0.2, hy - 1.4, 4.6, (d * look[0], look[1]), col, band)
        if frame == 2:
            # the tongue, snapping out to the edge of the tile and no further: the renderer puts a
            # sprite in front of or behind a wall as one no wider than its tile (client/render/
            # CLAUDE.md, *Everything is sorted where it is drawn*), and out at 16.6 it lay over the
            # corner of a wall beside it
            tip = (hx + d * 15.0, hy + 2.2)
            cel(draw, Limb([(hx + d * 9.0, hy + 3.2), (hx + d * 12.0, hy + 3.0), tip], [1.1, 1.0, 1.0]), CHAM_TONGUE, sh=None, lw=0.6)
            cel(draw, Ell(tip[0], tip[1], 2.1, 2.1), CHAM_TONGUE, sh=(0.5, 0.5), lw=0.6)
        return
    # head-on and from behind: the head biggest, the turrets standing out either side of it, the
    # narrow body behind on thin legs with their elbows out, the tail's curl held out to one side
    # (its own side: it changes sides on screen as the lizard turns round)
    hx, hy = cx, by - 22.8
    arch, far = _cham_casque(hx, hy)
    skull = [Ell(hx, hy - 0.6, 8.2, 6.6), Ell(hx, hy + 2.4, 6.6, 5.6), arch]
    body = Ell(cx, by - 10.6, 8.8, 7.8)
    if back:
        # walking away: the head beyond, the banded back and its crest nearest, the tail last
        for s in (-1, 1):
            bulb = Ell(hx + s * 8.3, hy - 1.0, 4.4, 4.4)
            cel(draw, bulb, shade(col, 0.5), sh=(0.5, 0.5), regions=[(_Band([bulb], (-s * 1.5, 0.5)), band)])
        blob(draw, skull, col, sh=(1.2, 1.0), regions=_coat(skull, [(far, shade(col, 0.9))]))
        for s in (-1, 1):       # the elbows of the front legs, beyond the body
            up = 0.8 if ph * s < 0 else 0.0
            cel(draw, Limb([(cx + s * 6.0, by - 15.6), (cx + s * 11.2, by - 14.2 - up), (cx + s * 10.4, by - 8.6 - up)], [2.0, 1.7, 1.4]),
                shade(col, 0.8), sh=(0.5, 0.0))
        for s in (-1, 1):
            lift = 1.2 if ph * s > 0 else 0.0
            _cham_leg(draw, (cx + s * 6.0, by - 8.6), (cx + s * 10.6, by - 8.2), (cx + s * 8.0, base - 1.3 - lift), col)
        arcs = [Limb(_bez((cx - w, by + v - 1.2), (cx, by + v + 1.6), (cx + w, by + v - 1.2), 6), 1.2) for (v, w) in ((-14.4, 6.2), (-8.6, 6.6))]
        cel(draw, body, col, sh=(1.4, 1.2), regions=_coat([body], [(a, band) for a in arcs], (1.4, 1.2)))
        for (v, w, h) in ((-17.0, 1.7, 3.0), (-11.6, 1.9, 3.2), (-6.0, 2.0, 3.4)):
            cel(draw, Poly([(cx - w, by + v + 1.0), (cx + 0.3, by + v - h), (cx + w, by + v + 1.0)]), crest, sh=(0.5, 0.0), lw=0.6)
        _cham_tail(draw, (cx - 0.4, by - 4.4), cx - 14.6, by - 10.8 + sway, 5.4, -1, col, start=135.0)
        return
    _cham_tail(draw, (cx + 6.0, by - 8.6), cx + 15.2, by - 11.8 + sway, 5.4, 1, col, start=135.0)
    for s in (-1, 1):       # the hind feet, behind
        lift = 1.2 if ph * s > 0 else 0.0
        _cham_leg(draw, (cx + s * 8.0, by - 7.0), (cx + s * 9.8, by - 4.6), (cx + s * 10.4, base - 2.3 - lift), shade(col, 0.8))
    cel(draw, body, col, sh=(1.4, 1.2), regions=_coat([body], [(Ell(cx, by - 8.6, 4.8, 6.0), belly)], (1.4, 1.2)))
    for s in (-1, 1):
        lift = 1.2 if ph * s < 0 else 0.0
        _cham_leg(draw, (cx + s * 4.6, by - 12.0), (cx + s * 8.6, by - 7.4), (cx + s * 5.6, base - 1.3 - lift), col)
    jaw = [(hx - 5.0, hy + 4.4), (hx, hy + 5.3), (hx + 5.0, hy + 4.4)]
    throat = Poly(jaw + [(hx + 9.0, hy + 4.0), (hx + 9.0, hy + 9.0), (hx - 9.0, hy + 9.0), (hx - 9.0, hy + 4.0)])
    blob(draw, skull, col, sh=(1.2, 1.0), regions=_coat(skull, [(far, shade(col, 0.9)), (throat, belly)]))
    stroke(draw, jaw, 0.8, line)
    for s in (-1, 1):
        Ell(hx + s * 1.5, hy + 2.4, 0.55, 0.5).draw(draw, fill=line)
    if frame == 2:
        tip = (hx + 0.6, hy + 10.4)
        cel(draw, Limb([(hx, hy + 5.2), tip], [1.1, 1.0]), CHAM_TONGUE, sh=None, lw=0.6)
        cel(draw, Ell(tip[0], tip[1], 2.1, 2.1), CHAM_TONGUE, sh=(0.5, 0.5), lw=0.6)
    # each turret looks its own way, and a different way every step
    looks = (((0.0, 0.3), (0.8, -0.5)), ((-0.8, -0.3), (0.1, 0.4)), ((0.2, 0.5), (-0.2, 0.5)), ((-0.1, 0.4), (0.8, 0.3)))[frame]
    for s, lk in zip((-1, 1), looks):
        _cham_eye(draw, hx + s * 8.5, hy - 1.2, 4.6, lk, col, band)


BEAST_DRAW_FUNCTIONS = {
    'wolf': draw_wolf,
    'serpent': draw_serpent,
    'spider': draw_spider,
    'bear': draw_bear,
    'scorpion': draw_scorpion,
    'hawk': draw_hawk,
    'shark': draw_shark,
    'beetle': draw_beetle,
    'treant': draw_treant,
    'phoenix': draw_phoenix,
    'hydra': draw_hydra,
    'mantis': draw_mantis,
    'jellyfish': draw_jellyfish,
    'gorilla': draw_gorilla,
    'chameleon': draw_chameleon,
}


def main():
    for name, draw_func in BEAST_DRAW_FUNCTIONS.items():
        generate_character(name, draw_func=draw_func)
    print(f"\nGenerated {len(BEAST_DRAW_FUNCTIONS)} beast character sprites.")


if __name__ == "__main__":
    main()
