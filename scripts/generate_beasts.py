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
# WOLF (72) -- blue-grey, white muzzle and ruff, gold eyes, bushy tail
# ===================================================================

WOLF = (150, 162, 188)
WOLF_W = (238, 240, 248)
WOLF_NOSE = (52, 46, 60)
WOLF_EYE = (250, 196, 60)


def _wolf_head(draw, hx, hy, d, frame, back=False):
    # ears
    for s in ((-1, 1) if not d else (-d, d)):
        near = not d or s == -d
        ex = hx + s * 6.0 if not d else hx - d * 1.6 + s * 2.4
        col = WOLF if near else shade(WOLF, 0.6)
        cel(draw, Poly([(ex - 3.0, hy - 5.4), (ex + (s * 1.0 if not d else -d * 1.4), hy - 13.4), (ex + 3.0, hy - 5.4)]), col, sh=(0.5, 0.5))
        if not back:
            Poly([(ex - 1.4, hy - 6.4), (ex + (s * 0.8 if not d else -d * 1.0), hy - 11.0), (ex + 1.4, hy - 6.4)]).draw(draw, fill=(236, 186, 190))
    parts = [Ell(hx, hy, 9.8 if not d else 8.8, 8.6)]
    # cheek ruff
    for s in ((-1, 1) if not d else (-d,)):
        parts.append(Poly([(hx + s * 7.0, hy - 1.0), (hx + s * 11.6, hy + 2.6), (hx + s * 8.4, hy + 3.6), (hx + s * 10.4, hy + 6.4),
                           (hx + s * 5.4, hy + 6.4)]))
    blob(draw, parts, WOLF, sh=(1.2, 1.0))
    if back:
        return
    if d:
        mz = [Ell(hx + d * 7.4, hy + 2.6, 5.4, 3.6)]
        blob(draw, mz, WOLF_W, sh=(0.0, 0.8))
        cel(draw, Ell(hx + d * 12.0, hy + 1.2, 1.8, 1.5), WOLF_NOSE, sh=None, line=False)
        stroke(draw, [(hx + d * 5.0, hy + 4.4), (hx + d * 8.6, hy + 5.0), (hx + d * 11.4, hy + 4.0)], 0.6, WOLF_NOSE)
        Poly([(hx + d * 9.0, hy + 4.8), (hx + d * 9.8, hy + 4.7), (hx + d * 9.4, hy + 6.2)]).draw(draw, fill=TOOTH)
        _eyes(draw, hx + d * 2.6, hx + d * 6.6, hy - 1.6, d, WOLF_EYE, skin=WOLF, w=3.8, h=4.4)
        return
    cel(draw, Ell(hx, hy + 3.6, 5.2, 4.0), WOLF_W, sh=(0.6, 0.6))
    cel(draw, Ell(hx, hy + 1.4, 2.2, 1.6), WOLF_NOSE, sh=None, line=False)
    Ell(hx - 0.7, hy + 0.9, 0.6, 0.4).draw(draw, fill=(150, 150, 170))
    stroke(draw, [(hx - 2.6, hy + 4.6), (hx, hy + 5.4), (hx + 2.6, hy + 4.6)], 0.6, WOLF_NOSE)
    for s in (-1, 1):
        Poly([(hx + s * 1.8 - 0.4, hy + 5.0), (hx + s * 1.8 + 0.4, hy + 5.0), (hx + s * 1.8, hy + 6.4)]).draw(draw, fill=TOOTH)
    _eyes(draw, hx - 4.6, hx + 4.6, hy - 1.8, 0, WOLF_EYE, skin=WOLF, w=3.8, h=4.6)


def draw_wolf(draw, ox, oy, direction, frame):
    d, back, base, cx, ph = _setup(ox, oy, direction, frame)
    wag = [0.0, 1.0, 0.0, -1.0][frame]
    if d:
        # bushy tail
        tail = Limb([(cx - d * 8.0, base - 14.0), (cx - d * 13.0, base - 17.0 + wag), (cx - d * 15.0, base - 22.0 + wag)], [2.6, 3.2, 1.0])
        cel(draw, tail, WOLF, sh=(0.8, 0.8))
        cel(draw, Ell(cx - d * 15.0, base - 22.0 + wag, 1.8, 1.8), WOLF_W, sh=None)
        _quad_legs(draw, cx, base, d, ph, WOLF, 5.6, -6.4, base - 11.0, paw=WOLF_W)
        body = Poly(_rot(arc_pts(cx - d * 0.6, base - 13.0, 10.4, 6.2, 0, 360)[:-1], cx, base - 13.0, -d * 6))
        cel(draw, body, WOLF, sh=(1.2, 1.2))
        blob(draw, [Ell(cx + d * 6.0, base - 13.0, 4.4, 4.6), Ell(cx + d * 7.4, base - 9.6, 3.0, 2.6)], WOLF_W, sh=(0.6, 0.8))
        _wolf_head(draw, cx + d * 8.0, base - 23.0, d, frame)
        return
    if back:
        for s in (-1, 1):
            fwd = ph * s
            cel(draw, Limb([(cx + s * 5.0, base - 11.0), (cx + s * 5.4, base - 1.8 - (1.0 if fwd < 0 else 0))], [2.4, 2.0]), WOLF, sh=(0.6, 0.0))
            _paw(draw, cx + s * 5.4, base - 1.2 - (1.0 if fwd < 0 else 0), WOLF_W, claws=False)
        cel(draw, Ell(cx, base - 13.0, 9.4, 7.4), WOLF, sh=(1.2, 1.2))
        tail = Limb([(cx, base - 12.0), (cx + 2.0 + wag, base - 16.0), (cx + 1.0 + wag * 1.6, base - 22.0)], [2.8, 3.4, 1.2])
        cel(draw, tail, WOLF, sh=(0.8, 0.8))
        cel(draw, Ell(cx + 1.0 + wag * 1.6, base - 22.0, 2.0, 2.0), WOLF_W, sh=None)
        _wolf_head(draw, cx, base - 25.0, 0, frame, back=True)
        return
    # head-on: the tail flicks up behind, back legs peek out, chest ruff, front legs
    cel(draw, Limb([(cx + 6.0, base - 12.0), (cx + 12.0, base - 16.0 + wag), (cx + 13.0, base - 22.0 + wag)], [2.4, 3.0, 1.0]), WOLF, sh=(0.8, 0.8))
    for s in (-1, 1):
        cel(draw, Limb([(cx + s * 7.4, base - 9.0), (cx + s * 7.6, base - 1.8)], [2.0, 1.8]), shade(WOLF, 0.6), sh=None)
        _paw(draw, cx + s * 7.6, base - 1.4, shade(WOLF_W, 0.6), claws=False, r=2.0)
    cel(draw, Ell(cx, base - 12.0, 8.4, 6.4), WOLF, sh=(1.2, 1.0))
    for s in (-1, 1):
        fwd = ph * s
        lift = 1.0 if fwd < 0 else 0.0
        cel(draw, Limb([(cx + s * 3.8, base - 12.0), (cx + s * 3.8, base - 1.8 - lift)], [2.4, 2.1]), WOLF, sh=(0.6, 0.0))
        _paw(draw, cx + s * 3.8, base - 1.0 - lift, WOLF_W)
    blob(draw, [Ell(cx, base - 15.0, 6.4, 4.4), Ell(cx - 3.0, base - 12.0, 2.6, 2.2), Ell(cx + 3.0, base - 12.0, 2.6, 2.2),
                Ell(cx, base - 11.0, 2.4, 2.4)], WOLF_W, sh=(0.6, 0.8))
    _wolf_head(draw, cx, base - 25.0, 0, frame)


# ===================================================================
# SERPENT (73) -- a coiled cobra, hood flared, forked tongue
# ===================================================================

SNAKE = (84, 176, 84)
SNAKE_BELLY = (236, 222, 132)
SNAKE_MARK = (48, 110, 66)
SNAKE_EYE = (250, 214, 60)


def draw_serpent(draw, ox, oy, direction, frame):
    d, back, base, cx, ph = _setup(ox, oy, direction, frame, bob=[0, 0, 0, 0])
    sway = [0.0, 1.2, 0.0, -1.2][frame]
    # coils on the ground
    for (u, v, rx, ry) in ((0.0, -3.6, 11.4, 4.0), (-1.0, -7.4, 9.0, 3.6), (0.6, -10.6, 6.6, 3.0)):
        cel(draw, Ell(cx + u - d * 1.0, base + v, rx, ry), SNAKE, sh=(1.2, 1.0))
        if not back:
            cel(draw, Poly(arc_pts(cx + u - d * 1.0, base + v, rx - 1.4, ry - 1.2, 20, 160, 12)), SNAKE_BELLY, sh=None, line=False)
    # the tail tip curling out
    tip = cx + (9.0 if not d else -d * 10.0)
    cel(draw, Limb([(tip - (3.0 if not d else -d * 3.0), base - 2.6), (tip, base - 3.6), (tip + (2.4 if not d else -d * 2.4), base - 6.6 + sway)],
                   [2.0, 1.4, 0.4]), SNAKE, sh=(0.4, 0.4))
    # the neck rising
    nx = cx + sway * 0.6 + d * 2.0
    neck = Limb([(cx - d * 0.6, base - 11.0), (nx - d * 1.0, base - 18.0), (nx, base - 24.0)], [4.0, 3.6, 3.4])
    cel(draw, neck, SNAKE, sh=(0.8, 0.6))
    if not back and not d:
        cel(draw, Limb([(cx, base - 11.0), (nx - d * 1.0, base - 18.0), (nx, base - 23.0)], [2.2, 2.0, 1.8]), SNAKE_BELLY, sh=None, line=False)
    # the hood
    hx, hy = nx + d * 1.4, base - 30.0
    if d:
        hood = Poly([(hx - d * 5.0, hy - 2.0), (hx - d * 1.0, hy - 6.0), (hx + d * 2.0, hy - 2.0), (hx + d * 1.6, hy + 8.0), (hx - d * 4.0, hy + 9.0)])
        cel(draw, hood, SNAKE, sh=(0.8, 0.8))
    else:
        hood = Poly([(hx - 3.0, hy - 6.0), (hx + 3.0, hy - 6.0), (hx + 10.6, hy + 2.0), (hx + 8.4, hy + 9.4), (hx + 3.4, hy + 11.0),
                     (hx - 3.4, hy + 11.0), (hx - 8.4, hy + 9.4), (hx - 10.6, hy + 2.0)])
        cel(draw, hood, SNAKE, sh=(1.2, 1.0))
        if not back:
            cel(draw, Poly([(hx - 3.0, hy + 2.0), (hx + 3.0, hy + 2.0), (hx + 2.6, hy + 11.0), (hx - 2.6, hy + 11.0)]), SNAKE_BELLY, sh=None, line=False)
            for s in (-1, 1):
                cel(draw, Ell(hx + s * 6.6, hy + 4.6, 1.8, 2.4), SNAKE_MARK, sh=None, line=False)
                Ell(hx + s * 6.6, hy + 4.6, 0.8, 1.1).draw(draw, fill=SNAKE_BELLY)
        else:
            for s in (-1, 1):
                cel(draw, Ell(hx + s * 5.0, hy + 3.4, 2.4, 2.4), SNAKE_MARK, sh=None, line=False)
    # head
    head = Ell(hx + d * 3.0, hy - 1.0, 7.4 if not d else 7.8, 6.2)
    cel(draw, head, SNAKE, sh=(1.0, 1.0), hi=(0.6, 0.6))
    if back:
        return
    if d:
        cel(draw, Ell(hx + d * 7.4, hy + 1.4, 3.6, 2.4), SNAKE_BELLY, sh=None, line=False)
        _eyes(draw, hx + d * 3.4, hx + d * 7.4, hy - 2.6, d, SNAKE_EYE, skin=SNAKE, w=3.6, h=4.2)
        mx = hx + d * 10.4
    else:
        _eyes(draw, hx - 3.4, hx + 3.4, hy - 1.8, 0, SNAKE_EYE, skin=SNAKE, w=3.8, h=4.4)
        mx = hx
        for s in (-1, 1):
            Ell(hx + s * 1.0, hy + 2.0, 0.4, 0.3).draw(draw, fill=SNAKE_MARK)
    if frame % 2 == 0:
        ty = hy + 3.4
        tx = mx + d * 1.4
        stroke(draw, [(tx, ty), (tx + d * 2.4, ty + 2.4), (tx + d * 2.4 - 1.0, ty + 4.0)], 0.5, (230, 60, 80))
        stroke(draw, [(tx + d * 2.4, ty + 2.4), (tx + d * 2.4 + 1.0, ty + 4.0)], 0.5, (230, 60, 80))
    for s in ((-1, 1) if not d else (d,)):
        fx = mx + s * 1.4 if not d else mx - d * 1.0
        Poly([(fx - 0.5, hy + 2.8), (fx + 0.5, hy + 2.8), (fx, hy + 4.4)]).draw(draw, fill=TOOTH)


# ===================================================================
# SPIDER (74) -- a round fuzzy body, a red mark, big eyes, eight legs
# ===================================================================

SPI = (84, 64, 104)
SPI_LT = (150, 122, 170)
SPI_RED = (226, 44, 60)
SPI_EYE = (255, 90, 90)


def draw_spider(draw, ox, oy, direction, frame):
    d, back, base, cx, ph = _setup(ox, oy, direction, frame)
    by = base - 10.0
    step = [0.0, 1.2, 0.0, -1.2][frame]
    # eight legs radiating out: knees raised above the body, feet fanned
    # front to back -- the front pair reach forward, the back pair trail
    if d:
        legs = [((3.0, -1.0), (12.0, -10.0), (16.0, 0.0)), ((1.0, -1.0), (7.0, -13.0), (9.0, 0.0)),
                ((-1.0, -1.0), (-4.4, -13.0), (-6.0, 0.0)), ((-3.0, -1.0), (-11.0, -11.0), (-15.0, 0.0))]
        for layer in (0, 1):
            for i, (hip, knee, foot) in enumerate(legs):
                k = 1 if (i + layer) % 2 else -1
                near = layer == 1
                off = 0.0 if near else -1.6
                col = SPI if near else shade(SPI, 0.5)
                h = (cx + d * hip[0], by + hip[1] + off)
                kn = (cx + d * (knee[0] + (0.0 if near else -1.0)), by + knee[1] + k * step + off)
                ft = (cx + d * (foot[0] + (0.0 if near else -1.6)), base - 0.6 + off)
                cel(draw, Limb([h, kn], [1.4, 1.1]), col, sh=None)
                cel(draw, Limb([kn, ft], [1.1, 0.5]), col, sh=None)
                if near:
                    Ell(kn[0], kn[1], 1.1, 1.1).draw(draw, fill=SPI_LT)
    else:
        legs = [((4.0, 1.0), (11.0, -6.0), (10.4, 0.0)), ((5.0, -1.0), (15.0, -10.0), (17.0, 0.0)),
                ((5.0, -3.0), (18.0, -12.4), (21.0, -1.6)), ((4.0, -5.0), (15.4, -15.4), (19.4, -4.0))]
        for i in (3, 2, 1, 0):
            hip, knee, foot = legs[i]
            for s in (-1, 1):
                k = 1 if (i + (s > 0)) % 2 else -1
                col = SPI if i < 2 else shade(SPI, 0.3 + (i - 2) * 0.2)
                h = (cx + s * hip[0], by + hip[1])
                kn = (cx + s * knee[0], by + knee[1] + k * step)
                ft = (cx + s * foot[0], (base - 0.6) + (foot[1] if i >= 2 else 0.0))
                cel(draw, Limb([h, kn], [1.5, 1.2]), col, sh=None)
                cel(draw, Limb([kn, ft], [1.2, 0.5]), col, sh=None)
                Ell(kn[0], kn[1], 1.2, 1.2).draw(draw, fill=SPI_LT)
                Ell((kn[0] + ft[0]) / 2, (kn[1] + ft[1]) / 2, 0.8, 0.8).draw(draw, fill=SPI_LT)
    # abdomen behind, head-thorax in front
    ab = (cx - d * 6.0, by - 3.0) if d else (cx, by - 5.0)
    cel(draw, Ell(ab[0], ab[1], 10.0 if not d else 9.4, 9.0), SPI, sh=(1.4, 1.2), hi=(0.8, 0.8))
    if back or d:
        cel(draw, Poly([(ab[0], ab[1] - 5.0), (ab[0] + 2.6, ab[1] - 2.0), (ab[0], ab[1] + 1.0), (ab[0] - 2.6, ab[1] - 2.0)]), SPI_RED, sh=None)
        cel(draw, Poly([(ab[0], ab[1] + 1.6), (ab[0] + 2.2, ab[1] + 4.0), (ab[0], ab[1] + 6.2), (ab[0] - 2.2, ab[1] + 4.0)]), SPI_RED, sh=None)
    if back:
        return
    hx, hy = (cx + d * 5.0, by + 0.6) if d else (cx, by + 2.0)
    blob(draw, [Ell(hx, hy, 8.0 if not d else 6.6, 6.6)] + [Ell(hx + u, hy - 6.0, 1.4, 1.8) for u in ((-4.0, -1.2, 1.6, 4.4) if not d else (-2.0, 1.4))],
         SPI, sh=(1.0, 1.0))
    # eyes: two big, four little
    e1, e2 = (hx - 2.8, hx + 2.8) if not d else (hx + d * 1.4, hx + d * 4.4)
    _eyes(draw, e1, e2, hy - 1.0, d, SPI_EYE, mood="bright", skin=SPI, w=3.6, h=4.2)
    for (u, v) in ((-5.4, -3.6), (5.4, -3.6), (-3.0, -4.8), (3.0, -4.8)) if not d else ((d * 5.4, -3.6), (d * 3.0, -4.6)):
        cel(draw, Ell(hx + u, hy + v, 0.9, 0.9), SPI_EYE, sh=None, lw=0.4)
    for s in ((-1, 1) if not d else (d,)):
        fx = hx + s * 1.6 if not d else hx + d * 6.4
        cel(draw, Poly([(fx - 0.8, hy + 3.6), (fx + 0.8, hy + 3.6), (fx + (0.6 * s if not d else d * 0.4), hy + 6.6)]), TOOTH, sh=None, lw=0.5)


# ===================================================================
# BEAR (75) -- a brown brawler on its hind legs, claws out
# ===================================================================

BEAR = (156, 104, 62)
BEAR_LT = (222, 186, 136)
BEAR_NOSE = (52, 40, 40)


def draw_bear(draw, ox, oy, direction, frame):
    d, back, base, cx, ph = _setup(ox, oy, direction, frame)
    # legs: short and thick
    for s in ((-1, 1) if not d else (-d, d)):
        near = not d or s == d
        fwd = ph * s if not d else (ph if near else -ph)
        lx = cx + (s * 5.2 if not d else d * fwd * 2.6 + s * 1.4)
        lift = 1.0 if fwd < 0 and not d else 0.0
        col = BEAR if near else shade(BEAR, 0.6)
        cel(draw, Limb([(lx, base - 9.0), (lx, base - 2.4 - lift)], [3.2, 3.0]), col, sh=(0.8, 0.0))
        cel(draw, Ell(lx + d * 1.0, base - 1.4 - lift, 3.4, 1.8), BEAR_LT if near else shade(BEAR_LT, 0.6), sh=(0.0, 0.5))
    # body: a big round barrel with a pale belly
    bw = 11.0 if not d else 9.4
    cel(draw, Ell(cx - d * 0.6, base - 15.0, bw, 10.4), BEAR, sh=(1.6, 1.4))
    if not back:
        cel(draw, Ell(cx + d * 2.6, base - 13.6, bw * 0.6, 7.0), BEAR_LT, sh=None, line=False)
    else:
        cel(draw, Ell(cx, base - 7.4, 2.4, 2.0), BEAR, sh=None)
    # arms up, claws out, ready to maul
    for s in ((-1, 1) if not d else (-d, d)):
        near = not d or s == d
        col = BEAR if near else shade(BEAR, 0.6)
        sx = cx + (s * (bw - 1.6) if not d else s * 1.0)
        raise_ = [0.0, 1.0, 0.0, 1.0][frame]
        end = (sx + (s * 4.4 if not d else d * 5.6), base - 21.0 - raise_ * (1 if s > 0 else 0.4))
        cel(draw, Limb([(sx, base - 20.0), ((sx + end[0]) / 2 + (s * 1.4 if not d else 0), base - 17.0), end], [3.4, 3.0, 2.8]), col, sh=(0.8, 0.6))
        px, py = end
        cel(draw, Ell(px, py, 3.2, 3.0), col, sh=(0.6, 0.6))
        if near and not back:
            cel(draw, Ell(px, py + 0.6, 1.6, 1.4), BEAR_LT, sh=None, line=False)
            for k in (-1.4, 0.0, 1.4):
                Poly([(px + k - 0.5, py - 2.6), (px + k + 0.5, py - 2.6), (px + k * 1.3, py - 4.4)]).draw(draw, fill=TOOTH)
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

SCO = (214, 110, 58)
SCO_DK = (150, 70, 44)
SCO_VENOM = (170, 255, 90)


def draw_scorpion(draw, ox, oy, direction, frame):
    d, back, base, cx, ph = _setup(ox, oy, direction, frame, bob=[0, 0, 0, 0])
    step = [0.0, 1.0, 0.0, -1.0][frame]
    by = base - 8.0
    sway = [0.0, 0.8, 0.0, -0.8][frame]

    def tail(front):
        """The tail: segments rising from the rear, arching over, sting forward."""
        if d:
            seg = [(cx - d * 8.0, by - 1.0), (cx - d * 12.0, by - 7.0), (cx - d * 12.4, by - 14.4), (cx - d * 9.0, by - 20.4),
                   (cx - d * 3.0, by - 23.0 + sway), (cx + d * 2.6, by - 21.4 + sway)]
        else:
            # head-on the tail comes up behind, swings to one side and arcs
            # toward the camera: each segment nearer, so larger, than the last
            seg = [(cx + 0.4, by - 4.0), (cx + 2.4, by - 9.4), (cx + 4.0 + sway, by - 15.0), (cx + 3.4 + sway, by - 20.6),
                   (cx + 1.0 + sway, by - 25.4), (cx - 0.6 + sway, by - 28.6)]
        for i in range(len(seg) - 1):
            a, b = seg[i], seg[i + 1]
            w = (3.4 - i * 0.3) if d else (2.6 + i * 0.36)
            cel(draw, Ell((a[0] + b[0]) / 2, (a[1] + b[1]) / 2, w, w * (1.0 if d else 0.86)), SCO if i % 2 == 0 else lit(SCO, 0.4),
                sh=(0.6, 0.6))
        tip = seg[-1]
        if d:
            sting = [(tip[0] - d * 1.6, tip[1] - 2.4), (tip[0] + d * 3.4, tip[1] - 1.4), (tip[0] + d * 5.4, tip[1] + 2.4),
                     (tip[0] + d * 1.6, tip[1] + 1.6), (tip[0] - d * 1.2, tip[1] + 2.0)]
        else:
            cel(draw, Ell(tip[0], tip[1] - 0.4, 4.2, 3.6), SCO, sh=(0.8, 0.8), hi=(0.5, 0.5))
            sting = [(tip[0] - 1.6, tip[1] + 1.4), (tip[0] + 1.6, tip[1] + 1.4), (tip[0] + 0.4, tip[1] + 5.4)]
        cel(draw, Poly(sting), SCO_DK, sh=None)
        vx, vy = (tip[0] + d * 5.2, tip[1] + 2.2) if d else (tip[0] + 0.4, tip[1] + 6.0)
        cel(draw, Ell(vx, vy, 1.2, 1.2), SCO_VENOM, sh=None, lw=0.5)
        if frame % 2 == 0:
            sparkle(draw, vx + 1.4, vy - 1.4, 1.6, SCO_VENOM)

    def pincer(s, near=True):
        col = SCO if near else shade(SCO, 0.6)
        if d:
            shx = cx + d * 5.0
            px, py = cx + d * 12.6 + (step if s > 0 else -step) * 0.6, by - 3.0 + (0 if near else -2.0)
            cel(draw, Limb([(shx, by + 0.4), (shx + d * 3.4, by - 1.6), (px - d * 2.4, py)], [1.6, 1.4, 1.3]), col, sh=None)
            claw_c = (px, py)
            ang = 0 if d > 0 else 180
        else:
            shx = cx + s * 6.0
            px, py = cx + s * 12.0, by - 9.4 + (step * s) * 0.6
            cel(draw, Limb([(shx, by), (cx + s * 11.4, by - 2.0), (px, py + 3.4)], [2.2, 2.0, 1.8]), col, sh=None)
            claw_c = (px, py)
            ang = -90 + s * 8
        k = 1.0 if d else 1.4
        top = xform([(0.0, -1.6), (3.0, -3.2), (6.4, -2.6), (7.2, -0.8), (4.4, -1.0), (2.4, 0.4), (0.0, 1.4)], claw_c[0], claw_c[1], ang, k)
        bot = xform([(0.0, 1.4), (2.6, 2.6), (5.4, 2.4), (4.0, 1.0), (1.6, 0.4)], claw_c[0], claw_c[1], ang, k)
        cel(draw, Ell(claw_c[0], claw_c[1], 3.0, 3.0), col, sh=(0.6, 0.6))
        cel(draw, Poly(top), col, sh=(0.4, 0.4))
        cel(draw, Poly(bot), shade(col, 0.4), sh=None)

    # legs: three pairs, low and splayed
    for i in range(3):
        for s in (-1, 1):
            k = 1 if (i + (s > 0)) % 2 else -1
            if d:
                near = s == 1
                x0 = cx + d * (2.0 - i * 3.0)
                knee = (x0 + d * (1.0 - i * 1.4), by - 3.6 + k * step * 0.6 - (0 if near else 1.0))
                foot = (x0 + d * (2.4 - i * 2.4), base - 0.6 - (0 if near else 1.2))
                col = SCO_DK if near else shade(SCO_DK, 0.6)
            else:
                x0 = cx + s * 4.6
                knee = (cx + s * (8.4 + i * 1.4), by - 3.0 + i * 1.4 + k * step * 0.6)
                foot = (cx + s * (10.4 + i * 1.8), base - 0.6)
                col = SCO_DK
            cel(draw, Limb([(x0, by + i * 0.8), knee, foot], [1.1, 0.9, 0.4]), col, sh=None)
    if back or d:
        if d:
            pincer(-1, near=False)
        tail(False)
    # body: a segmented carapace, the head at the front
    if d:
        body = Poly(_rot(arc_pts(cx - d * 1.4, by, 9.6, 4.8, 0, 360)[:-1], cx, by, -d * 4))
        cel(draw, body, SCO, sh=(1.0, 1.0), hi=(0.6, 0.6))
        for k in (-3.0, 0.0, 3.0):
            stroke(draw, [(cx - d * 1.4 + d * k, by - 4.2), (cx - d * 1.4 + d * k - d * 0.6, by + 3.4)], 0.5, SCO_DK)
    else:
        body = Ell(cx, by - 1.0 if not back else by, 8.4, 7.4)
        cel(draw, body, SCO, sh=(1.2, 1.0), hi=(0.6, 0.6))
        if back:
            for k in (2.4, 5.0):
                stroke(draw, [(cx - 7.4, by - 1.0 + (k - 3.0)), (cx + 7.4, by - 1.0 + (k - 3.0))], 0.5, SCO_DK)
    if not back:
        hx, hy = (cx + d * 7.4, by - 2.4) if d else (cx, by - 2.0)
        cel(draw, Ell(hx, hy, 6.4 if not d else 5.0, 5.0), SCO, sh=(0.8, 0.8), hi=(0.5, 0.5))
        e1, e2 = (hx - 2.8, hx + 2.8) if not d else (hx + d * 0.6, hx + d * 3.2)
        _eyes(draw, e1, e2, hy - 1.4, d, (60, 30, 30), skin=SCO, w=3.4, h=4.0)
        for s in ((-1, 1) if not d else (d,)):
            fx = hx + s * 1.4 if not d else hx + d * 4.4
            Poly([(fx - 0.6, hy + 2.4), (fx + 0.6, hy + 2.4), (fx + (s * 0.4 if not d else d * 0.5), hy + 4.0)]).draw(draw, fill=SCO_DK)
    if not back:
        if not d:
            tail(True)
        for s in ((-1, 1) if not d else (1,)):
            pincer(s, near=True)
    else:
        for s in (-1, 1):
            pincer(s, near=False)


# ===================================================================
# HAWK (77) -- a slate-blue falcon: moustache marks, barred chest
# ===================================================================

HAWK = (104, 122, 156)
HAWK_DK = (70, 82, 112)
HAWK_CHEST = (246, 236, 214)
HAWK_BAR = (150, 128, 110)
HAWK_BEAK = (250, 204, 70)
HAWK_EYE = (40, 32, 34)


def _hawk_wing(draw, sx, sy, side, spread, near=True):
    col = HAWK if near else shade(HAWK, 0.6)
    tipx = sx + side * (5.0 + spread * 6.0)
    tipy = sy + 10.0 - spread * 7.0
    pts = [(sx - side * 1.0, sy - 1.6), (sx + side * 4.4, sy - 1.4 - spread * 3.0), (tipx + side * 2.4, tipy - 2.0),
           (tipx + side * 0.6, tipy + 1.6), (tipx - side * 1.6, tipy + 0.6), (tipx - side * 2.6, tipy + 2.8),
           (sx + side * 1.6, sy + 8.0), (sx - side * 1.2, sy + 5.0)]
    cel(draw, Poly(pts), col, sh=(0.8, 0.8))
    for k in (0.0, 1.4, 2.8):
        stroke(draw, [(tipx - side * (1.0 + k), tipy - 3.0 + k * 0.3), (tipx - side * (0.4 + k), tipy + 1.8)], 0.5, HAWK_DK)


def draw_hawk(draw, ox, oy, direction, frame):
    d, back, base, cx, ph = _setup(ox, oy, direction, frame)
    spread = [0.3, 0.9, 0.3, 0.9][frame]
    # feet
    for s in ((-1, 1) if not d else (-1, 1)):
        fwd = ph * s
        fx = cx + (s * 3.0 if not d else d * fwd * 2.4)
        fy = base - 1.2 - (1.0 if fwd < 0 else 0.0)
        cel(draw, Limb([(fx, base - 7.0), (fx, fy)], [1.1, 0.9]), HAWK_BEAK, sh=None, lw=0.7)
        for k in ((-1, 0, 1) if not d else (0, 1, 2)):
            tx = fx + (k * 1.5 if not d else d * (1.2 + k * 0.9))
            stroke(draw, [(fx, fy), (tx, fy + 1.2)], 0.7, HAWK_BEAK)
            Ell(tx, fy + 1.4, 0.45, 0.45).draw(draw, fill=VOID)
    if d:
        # sleek profile: the body tilted forward, tail straight back, wing folded
        cel(draw, Poly([(cx - d * 6.0, base - 12.0), (cx - d * 14.4, base - 9.0), (cx - d * 14.0, base - 6.2), (cx - d * 6.4, base - 8.4)]),
            HAWK_DK, sh=None)
        body = Poly(_rot(arc_pts(cx, base - 13.4, 8.4, 7.0, 0, 360)[:-1], cx, base - 13.4, -d * 22))
        cel(draw, body, HAWK_CHEST, sh=(0.8, 1.0))
        for k in range(3):
            y = base - 16.0 + k * 2.6
            stroke(draw, [(cx + d * 1.6, y), (cx + d * 4.0, y + 0.4)], 0.5, HAWK_BAR)
        wing = Poly([(cx + d * 3.0, base - 20.0), (cx - d * 3.4, base - 20.6), (cx - d * 12.4, base - 12.0 - spread * 2.0),
                     (cx - d * 13.4, base - 8.4 - spread * 2.0), (cx - d * 6.0, base - 8.2), (cx + d * 1.0, base - 11.0)])
        cel(draw, wing, HAWK, sh=(0.8, 1.0))
        for k in (0.0, 2.2, 4.4):
            stroke(draw, [(cx - d * (3.0 + k), base - 15.0 + k * 0.5), (cx - d * (8.4 + k * 0.7), base - 10.0 - spread * 1.6)], 0.5, HAWK_DK)
        hx, hy = cx + d * 3.4, base - 26.0
        cel(draw, Ell(hx, hy, 9.4, 8.4), HAWK, sh=(1.0, 1.0), hi=(0.6, 0.6))
        cel(draw, Poly(arc_pts(hx + d * 1.4, hy + 1.4, 6.4, 5.4, 0 if d > 0 else 90, 90 if d > 0 else 180, 8) + [(hx + d * 1.4, hy + 1.4)]),
            HAWK_CHEST, sh=None, line=False)
        # the falcon's moustache
        cel(draw, Poly([(hx + d * 2.6, hy + 0.4), (hx + d * 4.6, hy + 1.0), (hx + d * 3.4, hy + 6.0), (hx + d * 2.0, hy + 5.6)]), HAWK_DK, sh=None,
            line=False)
        beak = Poly([(hx + d * 7.0, hy - 1.6), (hx + d * 10.8, hy - 0.6), (hx + d * 12.0, hy + 1.8), (hx + d * 11.0, hy + 3.4), (hx + d * 10.0, hy + 1.8),
                     (hx + d * 7.0, hy + 2.2)])
        cel(draw, beak, HAWK_BEAK, sh=(0.0, 0.6))
        ms_eye(draw, hx + d * 4.2, hy - 1.4, 3.8, 4.4, HAWK_EYE, (float(d), 0.0), "sharp", skin=HAWK, side=-d)
        return
    # head-on / from behind: upright, wings half open
    if back:
        cel(draw, Poly([(cx - 3.0, base - 10.0), (cx + 3.0, base - 10.0), (cx + 4.4, base - 3.0), (cx, base - 4.4), (cx - 4.4, base - 3.0)]),
            HAWK_DK, sh=None)
    for s in (-1, 1):
        _hawk_wing(draw, cx + s * 6.0, base - 20.0, s, spread)
    cel(draw, Ell(cx, base - 14.0, 8.0, 9.0), HAWK if back else HAWK_CHEST, sh=(1.2, 1.0))
    if not back:
        for (u, v) in ((-3.0, -3.6), (0.0, -2.6), (3.0, -3.6), (-2.0, 0.4), (2.0, 0.4), (0.0, 3.4)):
            stroke(draw, [(cx + u - 1.2, base - 14.0 + v), (cx + u, base - 14.0 + v + 0.6), (cx + u + 1.2, base - 14.0 + v)], 0.5, HAWK_BAR)
    hx, hy = cx, base - 27.0
    cel(draw, Ell(hx, hy, 10.2, 9.0), HAWK, sh=(1.2, 1.0), hi=(0.6, 0.6))
    if back:
        return
    cel(draw, Ell(hx, hy + 3.4, 6.4, 5.0), HAWK_CHEST, sh=None, line=False)
    for s in (-1, 1):
        cel(draw, Poly([(hx + s * 4.0, hy + 0.6), (hx + s * 6.2, hy + 1.2), (hx + s * 5.4, hy + 6.6), (hx + s * 3.8, hy + 6.0)]), HAWK_DK, sh=None, line=False)
    _eyes(draw, hx - 4.4, hx + 4.4, hy - 1.4, 0, HAWK_EYE, mood="sharp", skin=HAWK, w=3.8, h=4.6)
    beak = Poly([(hx - 2.2, hy + 0.8), (hx + 2.2, hy + 0.8), (hx + 1.6, hy + 4.4), (hx, hy + 6.0), (hx - 1.6, hy + 4.4)])
    cel(draw, beak, HAWK_BEAK, sh=(0.6, 0.4))


# ===================================================================
# SHARK (78) -- stands on its tail: a grin full of teeth, fins for arms
# ===================================================================

SHARK = (104, 134, 176)
SHARK_BELLY = (240, 244, 250)
SHARK_GUM = (200, 70, 90)


def draw_shark(draw, ox, oy, direction, frame):
    d, back, base, cx, ph = _setup(ox, oy, direction, frame, bob=[0, -2, 0, -2])
    wag = [0.0, 1.0, 0.0, -1.0][frame]
    # tail fin as feet
    tf = [(cx - 8.0, base + 0.4), (cx - 2.0, base - 5.0), (cx + 2.0, base - 5.0), (cx + 8.0, base + 0.4), (cx + 3.0, base - 1.4), (cx, base - 0.4),
          (cx - 3.0, base - 1.4)] if not d else \
        [(cx - d * 4.0, base - 6.0), (cx - d * 11.0 + wag, base - 11.0), (cx - d * 9.0, base - 3.0), (cx - d * 11.0 - wag, base + 0.6), (cx - d * 2.0, base - 1.4)]
    cel(draw, Poly(tf), SHARK, sh=(0.6, 0.6))
    # body: a torpedo standing on end
    bw = 11.4 if not d else 10.0
    body = Poly(arc_pts(cx - d * 0.6, base - 22.0, bw, 18.0, 180, 360, 30) + arc_pts(cx - d * 0.6, base - 13.0, bw * 0.84, 9.0, 0, 180, 16)[1:-1])
    cel(draw, body, SHARK, sh=(1.8, 1.4), hi=(1.0, 1.0))
    if not back:
        belly = Ell(cx + d * 3.0, base - 13.0, bw * (0.66 if not d else 0.52), 10.0)
        cel(draw, belly, SHARK_BELLY, sh=None, line=False)
    # dorsal fin
    fx = cx - d * 4.0
    cel(draw, Poly([(fx - 3.4, base - 37.0), (fx + (0.6 if not d else -d * 4.4), base - 45.0), (fx + 3.4, base - 37.4)]), SHARK, sh=(0.6, 0.6))
    # pectoral fins as arms
    for s in ((-1, 1) if not d else (d, -d)):
        near = not d or s == d
        sx = cx + s * (bw - 1.4) if not d else cx + s * 2.0
        tip = (sx + (s * 6.0 if not d else s * 6.4), base - 15.0 + (wag if s > 0 else -wag))
        cel(draw, Poly([(sx, base - 22.0), tip, (sx - (s * 1.0 if not d else 0.0), base - 16.0)]), SHARK if near else shade(SHARK, 0.6), sh=(0.6, 0.6))
    if back:
        for k in (-1.6, 1.6):
            stroke(draw, [(cx + k, base - 30.0), (cx + k, base - 18.0)], 0.4, shade(SHARK, 1.2))
        return
    # gills
    for k in range(3):
        gx = cx + (-bw + 2.4 + k * 1.4 if not d else -d * (1.0 + k * 1.4))
        stroke(draw, [(gx, base - 25.0 + k * 0.4), (gx + 0.4, base - 21.0 + k * 0.4)], 0.5, shade(SHARK, 1.4))
        if not d:
            gx2 = cx + bw - 2.4 - k * 1.4
            stroke(draw, [(gx2, base - 25.0 + k * 0.4), (gx2 - 0.4, base - 21.0 + k * 0.4)], 0.5, shade(SHARK, 1.4))
    # eyes high on the head, a huge grin below
    hx = cx + d * 4.0
    e1, e2 = (hx - 6.0, hx + 6.0) if not d else (hx + d * 0.6, hx + d * 4.8)
    _eyes(draw, e1, e2, base - 30.0, d, (40, 40, 60), mood="sharp", skin=SHARK, w=3.6, h=4.2)
    mw = 7.4 if not d else 5.4
    mx = hx + d * 1.6
    my = base - 24.0
    mouth = Poly([(mx - mw, my - 1.0), (mx + mw, my - 1.0), (mx + mw * 0.6, my + 3.6), (mx - mw * 0.6, my + 3.6)])
    cel(draw, mouth, SHARK_GUM, sh=None, line_color=shade(SHARK, 1.8))
    n = 6 if not d else 4
    for i in range(n):
        tx = mx - mw + (2 * mw) * (i + 0.5) / n
        Poly([(tx - 0.9, my - 1.0), (tx + 0.9, my - 1.0), (tx, my + 1.4)]).draw(draw, fill=TOOTH)
        tb = mx - mw * 0.6 + (1.2 * mw) * (i + 0.5) / n
        Poly([(tb - 0.7, my + 3.6), (tb + 0.7, my + 3.6), (tb, my + 1.8)]).draw(draw, fill=TOOTH)


# ===================================================================
# BEETLE (79) -- a rhinoceros beetle: a great horn, a shining shell
# ===================================================================

BTL = (70, 150, 110)
BTL_DK = (40, 90, 80)
BTL_HORN = (60, 60, 76)


def draw_beetle(draw, ox, oy, direction, frame):
    d, back, base, cx, ph = _setup(ox, oy, direction, frame, bob=[0, 0, 0, 0])
    step = [0.0, 1.0, 0.0, -1.0][frame]
    # six legs
    for i in range(3):
        for s in (-1, 1):
            k = 1 if (i + (s > 0)) % 2 else -1
            if d:
                near = s == 1
                x0 = cx + d * (3.0 - i * 4.0)
                knee = (x0 + d * (1.6 - i * 1.0), base - 7.0 + k * step * 0.6 - (0 if near else 1.0))
                foot = (x0 + d * (2.6 - i * 1.6), base - 0.6 - (0 if near else 1.2))
                col = BTL_HORN if near else shade(BTL_HORN, 0.4)
            else:
                x0 = cx + s * 5.0
                knee = (cx + s * (10.0 + i * 1.0), base - 8.0 + i * 1.6 + k * step * 0.6)
                foot = (cx + s * (11.6 + i * 1.4), base - 0.6)
                col = BTL_HORN
            cel(draw, Limb([(x0, base - 9.0 + i), knee, foot], [1.2, 1.0, 0.5]), col, sh=None)
    # the shell: two elytra meeting down the middle, with a highlight
    sy = base - 15.0
    if d:
        shell = Poly(_rot(arc_pts(cx - d * 2.0, sy, 13.2, 10.6, 180, 360, 26) + [(cx + d * 10.6, sy + 3.4), (cx - d * 15.2, sy + 3.8)],
                          cx, sy, -d * 6))
        cel(draw, shell, BTL, sh=(1.4, 1.2), hi=(1.0, 1.0), regions=[(Ell(cx - d * 4.0, sy - 6.0, 4.4, 2.0), lit(BTL, 1.6))])
    else:
        shell = Ell(cx, sy, 13.6, 12.4)
        cel(draw, shell, BTL, sh=(1.6, 1.4), hi=(1.0, 1.0), regions=[(Ell(cx - 5.0, sy - 5.4, 3.4, 2.0), lit(BTL, 1.6)),
                                                                   (Ell(cx + 5.0, sy - 5.4, 2.4, 1.4), lit(BTL, 1.2))])
        if back:
            stroke(draw, [(cx, sy - 10.6), (cx, sy + 10.6)], 0.8, BTL_DK)
            return
    if back:
        return
    # the head in front of the shell, the great horn sweeping up off it
    if d:
        hx, hy = cx + d * 9.0, base - 12.0
        cel(draw, Ell(hx, hy, 5.4, 4.8), BTL_DK, sh=(0.8, 0.8), hi=(0.4, 0.4))
        horn = [(hx + d * 1.4, hy - 3.6), (hx + d * 6.4, hy - 8.0), (hx + d * 7.0, hy - 14.4), (hx + d * 5.0, hy - 17.6)]
        cel(draw, Limb(horn, [2.2, 1.8, 1.2, 0.4]), BTL_HORN, sh=(0.4, 0.4))
        cel(draw, Limb([(hx + d * 5.2, hy - 11.0), (hx + d * 8.6, hy - 12.4)], [0.9, 0.3]), BTL_HORN, sh=None)
        _eyes(draw, hx + d * 1.4, hx + d * 3.6, hy - 0.4, d, (250, 220, 60), mood="bright", skin=BTL_DK, w=3.0, h=3.6, lash=(20, 30, 30))
        return
    hx, hy = cx, base - 10.0
    cel(draw, Ell(hx, hy, 6.4, 5.0), BTL_DK, sh=(0.8, 0.8), hi=(0.4, 0.4))
    horn = [(hx, hy - 3.0), (hx - 0.6, hy - 12.0), (hx + 0.8, hy - 20.0), (hx + 3.6, hy - 24.0)]
    cel(draw, Limb(horn, [2.6, 2.2, 1.4, 0.4]), BTL_HORN, sh=(0.6, 0.4))
    cel(draw, Limb([(hx + 0.4, hy - 16.0), (hx - 3.4, hy - 19.4)], [1.0, 0.3]), BTL_HORN, sh=None)
    _eyes(draw, hx - 3.4, hx + 3.4, hy + 0.2, 0, (250, 220, 60), mood="bright", skin=BTL_DK, w=3.0, h=3.6, lash=(20, 30, 30))


# ===================================================================
# TREANT (80) -- a walking tree: bark face, branch arms, a leafy crown
# ===================================================================

BARK = (140, 98, 64)
BARK_DK = (96, 66, 46)
LEAVES = (90, 170, 76)
TREANT_EYE = (250, 230, 110)


def draw_treant(draw, ox, oy, direction, frame):
    d, back, base, cx, ph = _setup(ox, oy, direction, frame)
    sway = [0.0, 1.0, 0.0, -1.0][frame]
    # root feet
    for s in ((-1, 1) if not d else (-1, 1)):
        fwd = ph * s
        fx = cx + (s * 5.0 if not d else d * fwd * 2.4 + s * 1.2)
        lift = 1.0 if fwd < 0 else 0.0
        for k in (-1, 0, 1):
            cel(draw, Limb([(fx, base - 5.0 - lift), (fx + k * 2.4 + d * 1.0, base - 0.6 - lift)], [1.8, 0.7]), BARK_DK, sh=None)
    # the trunk
    w = 8.4 if not d else 7.0
    trunk = Poly([(cx - w, base - 30.0), (cx + w, base - 30.0), (cx + w + 1.0, base - 12.0), (cx + w + 2.4, base - 4.0),
                  (cx - w - 2.4, base - 4.0), (cx - w - 1.0, base - 12.0)])
    cel(draw, trunk, BARK, sh=(1.8, 1.0))
    for (u, v0, v1) in ((-4.0, 26.0, 16.0), (3.4, 22.0, 9.0), (-1.0, 12.0, 6.0)):
        stroke(draw, [(cx + u, base - v0), (cx + u + 0.8, base - v1)], 0.6, BARK_DK)
    # branch arms with leaf tufts
    for s in ((-1, 1) if not d else (d, -d)):
        near = not d or s == d
        col = BARK if near else shade(BARK, 0.6)
        sx = cx + s * (w - 1.0) if not d else cx + s * 1.0
        mid = (sx + (s * 5.0 if not d else s * 4.4), base - 22.0 + (sway if s > 0 else -sway))
        end = (sx + (s * 8.0 if not d else s * 8.4), base - 17.0 + (sway if s > 0 else -sway))
        cel(draw, Limb([(sx, base - 24.0), mid, end], [2.4, 1.8, 1.2]), col, sh=(0.5, 0.5))
        cel(draw, Limb([mid, (mid[0] + (s * 2.0 if not d else s * 2.0), mid[1] - 4.0)], [1.0, 0.4]), col, sh=None)
        blob(draw, [Ell(end[0], end[1] + 1.0, 3.2, 2.8), Ell(end[0] + (s * 1.8 if not d else s * 1.8), end[1] - 1.0, 2.4, 2.2)],
             LEAVES if near else shade(LEAVES, 0.6), sh=(0.6, 0.6))
    # face in the bark
    if not back:
        fx = cx + d * 2.4
        for side, ex in (((-1, fx - 3.6), (1, fx + 3.6)) if not d else ((d, fx + d * 2.6), (-d, fx - d * 2.0))):
            k = 1.0 if not d or side == d else 0.62
            Ell(ex, base - 24.0, 2.6 * k, 2.6).draw(draw, fill=VOID)
            Ell(ex, base - 23.8, 1.4 * k, 1.4).draw(draw, fill=TREANT_EYE)
        Poly([(fx - 3.0, base - 18.6), (fx + 3.0, base - 18.6), (fx + 1.8, base - 16.4), (fx - 1.8, base - 16.4)]).draw(draw, fill=VOID)
    # canopy crown
    cy = base - 36.0
    parts = [Ell(cx - d * 1.0, cy, 14.0, 9.4), Ell(cx - 9.0 - d * 1.0, cy + 3.0, 6.0, 5.4), Ell(cx + 9.0 - d * 1.0, cy + 3.0, 6.0, 5.4),
             Ell(cx - 4.0 - d * 1.0, cy - 6.0, 6.4, 5.0), Ell(cx + 5.0 - d * 1.0, cy - 5.4, 6.0, 4.8)]
    blob(draw, parts, LEAVES, sh=(1.6, 1.4), regions=[(Ell(cx - 6.0, cy - 5.0, 4.0, 2.4), lit(LEAVES, 1.0))])
    for (u, v) in ((-6.0, 1.0), (5.0, -1.0), (0.0, 4.0)):
        Ell(cx + u + sway * 0.4, cy + v, 0.9, 0.9).draw(draw, fill=(255, 150, 170))


# ===================================================================
# PHOENIX (81) -- a bird of fire: flame crest, blazing wings, long tail
# ===================================================================

PHX = (240, 96, 44)
PHX_GOLD = (255, 196, 60)
PHX_CORE = (255, 240, 170)
PHX_FIRE = ((236, 70, 40), (255, 150, 50), (255, 234, 130))


def draw_phoenix(draw, ox, oy, direction, frame):
    d, back, base, cx, ph = _setup(ox, oy, direction, frame, bob=[-1, -3, -4, -3])
    flap = [0.0, 1.0, 0.4, 1.0][frame]
    sway = [0.0, 1.0, 0.0, -1.0][frame]
    # tail plumes streaming down and back
    tails = ((-4.0, 0.0), (0.0, 1.0), (4.0, 0.0)) if not d else ((0.0, 0.0), (1.0, 1.0))
    for i, (u, k) in enumerate(tails):
        if d:
            flame(draw, cx - d * (8.0 + i * 3.0), base - 10.0 + i * 2.0, 5.0, 14.0, PHX_FIRE, sway=sway - d * 6.0, flip=-1.0)
        else:
            flame(draw, cx + u, base + 1.0, 5.0, 12.0 + k * 3.0, PHX_FIRE, sway=sway * (1 if i % 2 else -1), flip=-1.0)
    # wings: great flames spread wide
    for s in ((-1, 1) if not d else (-d,)):
        wx = cx + s * 5.0 if not d else cx - d * 2.0
        tip = (wx + s * (15.0 + flap * 2.0), base - 26.0 - flap * 4.0)
        pts = [(wx, base - 20.0), (wx + s * 6.0, base - 24.0 - flap * 2.0), tip, (tip[0] - s * 1.6, tip[1] + 4.0), (tip[0] + s * 0.6, tip[1] + 6.0),
               (tip[0] - s * 3.6, tip[1] + 7.4), (tip[0] - s * 2.4, tip[1] + 10.0), (wx + s * 5.0, base - 12.0), (wx, base - 14.0)]
        cel(draw, Poly(pts), PHX, sh=(0.6, 0.6), tone=(214, 60, 40))
        cel(draw, Poly([(wx + s * 1.0, base - 19.0), (wx + s * 6.0, base - 22.0 - flap * 2.0), (tip[0] - s * 3.0, tip[1] + 3.0),
                        (wx + s * 5.0, base - 15.0)]), PHX_GOLD, sh=None, line=False)
    # body
    body = Ell(cx - d * 0.6, base - 16.0, 7.4 if not d else 7.0, 8.4)
    cel(draw, body, PHX, sh=(1.2, 1.0))
    if not back:
        cel(draw, Ell(cx + d * 1.6, base - 15.0, 4.4, 6.0), PHX_GOLD, sh=None, line=False)
    # legs: gold, tucked
    for s in (-1, 1):
        lx = cx + s * 2.4 + d * 0.6
        stroke(draw, [(lx, base - 9.0), (lx + d * 0.6, base - 5.4)], 0.9, PHX_GOLD)
        for k in (-1, 0, 1):
            stroke(draw, [(lx + d * 0.6, base - 5.4), (lx + d * 0.6 + k * 1.1, base - 4.2)], 0.6, PHX_GOLD)
    # head with a flame crest
    hx, hy = cx + d * 3.0, base - 28.0
    flame(draw, hx - d * 2.0, hy - 4.0, 12.0, 15.0, PHX_FIRE, sway=sway * 1.4 - d * 4.0)
    cel(draw, Ell(hx, hy, 7.6 if not d else 7.2, 6.8), PHX, sh=(0.8, 0.8), hi=(0.5, 0.5))
    if back:
        return
    if d:
        beak = Poly([(hx + d * 5.0, hy - 1.4), (hx + d * 9.4, hy + 0.0), (hx + d * 10.0, hy + 2.6), (hx + d * 8.6, hy + 2.0), (hx + d * 5.4, hy + 2.4)])
        cel(draw, beak, PHX_GOLD, sh=(0.0, 0.6))
        ms_eye(draw, hx + d * 2.6, hy - 1.0, 3.4, 4.0, (120, 30, 20), (float(d), 0.0), "sharp", skin=PHX, side=-d, lash=(90, 20, 20))
        return
    _eyes(draw, hx - 3.4, hx + 3.4, hy - 1.0, 0, (120, 30, 20), mood="sharp", skin=PHX, w=3.4, h=4.2, lash=(90, 20, 20))
    beak = Poly([(hx - 1.8, hy + 1.8), (hx + 1.8, hy + 1.8), (hx + 1.0, hy + 4.4), (hx, hy + 5.6), (hx - 1.0, hy + 4.4)])
    cel(draw, beak, PHX_GOLD, sh=(0.5, 0.4))
    for i in range(2):
        a = math.radians(frame * 90 + i * 180)
        sparkle(draw, cx + math.cos(a) * 16.0, base - 8.0 + math.sin(a) * 3.0, 1.6, PHX_CORE)


# ===================================================================
# HYDRA (82) -- three heads on short necks over a squat scaled body
# ===================================================================

HYD = (74, 160, 138)
HYD_BELLY = (234, 222, 150)
HYD_FIN = (120, 70, 150)
HYD_EYE = (255, 200, 60)


def _hydra_head(draw, x, y, d, big=1.0, back=False, frame=0):
    k = big
    for s in ((-1, 1) if not d else (-d,)):
        cel(draw, Poly([(x + s * 3.0 * k, y - 2.0 * k), (x + s * 7.0 * k, y - 5.4 * k), (x + s * 5.4 * k, y + 0.6 * k)]), HYD_FIN, sh=None, lw=0.6)
    cel(draw, Ell(x, y, 5.4 * k, 4.6 * k), HYD, sh=(0.6, 0.6), hi=(0.4, 0.4))
    if back:
        return
    if d:
        cel(draw, Ell(x + d * 4.4 * k, y + 1.4 * k, 3.4 * k, 2.4 * k), HYD, sh=(0.0, 0.6))
        ms_eye(draw, x + d * 1.4 * k, y - 1.0 * k, 2.8 * k, 3.2 * k, HYD_EYE, (float(d), 0.0), "sharp", skin=HYD, side=-d)
        Poly([(x + d * 5.4 * k, y + 2.6 * k), (x + d * 6.2 * k, y + 2.6 * k), (x + d * 5.8 * k, y + 4.2 * k)]).draw(draw, fill=TOOTH)
        return
    ms_eye(draw, x - 2.2 * k, y - 0.6 * k, 2.8 * k, 3.4 * k, HYD_EYE, (0.0, 0.0), "sharp", skin=HYD, side=-1)
    ms_eye(draw, x + 2.2 * k, y - 0.6 * k, 2.8 * k, 3.4 * k, HYD_EYE, (0.0, 0.0), "sharp", skin=HYD, side=1)
    stroke(draw, [(x - 2.2 * k, y + 2.6 * k), (x, y + 3.2 * k), (x + 2.2 * k, y + 2.6 * k)], 0.5, shade(HYD, 1.8))
    for s in (-1, 1):
        Poly([(x + s * 1.4 * k - 0.4, y + 2.8 * k), (x + s * 1.4 * k + 0.4, y + 2.8 * k), (x + s * 1.4 * k, y + 4.2 * k)]).draw(draw, fill=TOOTH)


def draw_hydra(draw, ox, oy, direction, frame):
    d, back, base, cx, ph = _setup(ox, oy, direction, frame)
    sway = [0.0, 1.0, 0.0, -1.0][frame]
    # tail
    tx = cx - (d * 8.0 if d else 7.0)
    cel(draw, Limb([(tx, base - 8.0), (tx - (d * 5.0 if d else 5.0), base - 6.0 + sway), (tx - (d * 8.0 if d else 8.0), base - 10.0)],
                   [3.4, 2.4, 0.5]), HYD, sh=(0.6, 0.6))
    # legs
    for s in ((-1, 1) if not d else (-1, 1)):
        fwd = ph * s
        for (u, near) in (((5.0, True), (-5.0, False)) if d else ((6.4, True),)):
            lx = cx + (s * u if not d else d * (u + fwd * 1.6) + s * 0.8)
            lift = 1.0 if fwd < 0 else 0.0
            col = HYD if (not d or s == 1) else shade(HYD, 0.6)
            cel(draw, Limb([(lx, base - 9.0), (lx, base - 1.8 - lift)], [2.6, 2.4]), col, sh=(0.6, 0.0))
            _paw(draw, lx, base - 1.2 - lift, col, d)
    # body
    body = Ell(cx - d * 1.0, base - 12.0, 11.4 if not d else 10.6, 8.4)
    cel(draw, body, HYD, sh=(1.4, 1.2))
    if not back:
        cel(draw, Ell(cx + d * 2.0, base - 10.0, 7.0 if not d else 5.4, 5.4), HYD_BELLY, sh=None, line=False)
    for (u, v) in ((-4.0, -6.0), (0.0, -8.0), (4.0, -6.0)):
        Poly([(cx + u - 1.6 - d, base - 12.0 + v), (cx + u - d, base - 15.4 + v), (cx + u + 1.6 - d, base - 12.0 + v)]).draw(draw, fill=HYD_FIN)
    # three necks and heads
    if d:
        heads = [(cx + d * 3.0, base - 18.0, cx + d * 9.4, base - 25.0 + sway, 0.9), (cx + d * 1.0, base - 18.0, cx + d * 4.4, base - 33.0 - sway, 1.1),
                 (cx - d * 1.0, base - 18.0, cx - d * 3.4, base - 28.0 + sway, 0.9)]
        order = [2, 1, 0]
    else:
        heads = [(cx - 5.0, base - 18.0, cx - 10.4, base - 27.0 + sway, 0.9), (cx, base - 19.0, cx, base - 33.0 - sway, 1.1),
                 (cx + 5.0, base - 18.0, cx + 10.4, base - 27.0 - sway, 0.9)]
        order = [0, 2, 1]
    for i in order:
        x0, y0, x1, y1, k = heads[i]
        cel(draw, Limb([(x0, y0), ((x0 + x1) / 2, (y0 + y1) / 2 + 1.0), (x1, y1 + 3.0)], [3.0, 2.6, 2.4]), HYD, sh=(0.6, 0.4))
        if not back and not d:
            cel(draw, Limb([(x0, y0 + 0.6), ((x0 + x1) / 2, (y0 + y1) / 2 + 1.4), (x1, y1 + 3.4)], [1.4, 1.2, 1.1]), HYD_BELLY, sh=None, line=False)
        _hydra_head(draw, x1, y1, d, k, back, frame)


# ===================================================================
# MANTIS (83) -- bug eyes, a triangle face, scythe arms folded to strike
# ===================================================================

MAN = (130, 200, 86)
MAN_DK = (84, 140, 62)
MAN_WING = (196, 236, 170)


def draw_mantis(draw, ox, oy, direction, frame):
    d, back, base, cx, ph = _setup(ox, oy, direction, frame)
    step = [0.0, 1.0, 0.0, -1.0][frame]
    # four walking legs
    for i in range(2):
        for s in (-1, 1):
            k = 1 if (i + (s > 0)) % 2 else -1
            if d:
                near = s == 1
                x0 = cx - d * (1.0 + i * 4.0)
                knee = (x0 + d * (2.0 - i * 3.0), base - 8.0 + k * step - (0 if near else 1.0))
                foot = (x0 + d * (3.0 - i * 5.0), base - 0.6 - (0 if near else 1.2))
                col = MAN_DK if near else shade(MAN_DK, 0.6)
            else:
                x0 = cx + s * 2.4
                knee = (cx + s * (7.0 + i * 2.0), base - 9.0 + i * 2.0 + k * step)
                foot = (cx + s * (8.0 + i * 3.0), base - 0.6)
                col = MAN_DK
            cel(draw, Limb([(x0, base - 10.0), knee, foot], [0.9, 0.7, 0.4]), col, sh=None)
    # abdomen: long, curving down behind
    if d:
        ab = Poly(_rot(arc_pts(cx - d * 6.0, base - 10.0, 7.0, 4.0, 0, 360)[:-1], cx - d * 6.0, base - 10.0, d * 20))
    else:
        ab = Ell(cx, base - 10.0, 5.0, 6.4)
    cel(draw, ab, MAN, sh=(1.0, 1.0))
    # folded wings on the back
    if back or d:
        wing = Poly([(cx - d * 1.0, base - 20.0), (cx - d * 9.0, base - 12.0), (cx - d * 10.0, base - 8.0), (cx - d * 2.0, base - 14.0)]) if d else \
            Poly([(cx - 4.4, base - 20.0), (cx + 4.4, base - 20.0), (cx + 3.6, base - 5.0), (cx, base - 3.6), (cx - 3.6, base - 5.0)])
        cel(draw, wing, MAN_WING, sh=(0.6, 0.6))
        stroke(draw, [(cx, base - 20.0), (cx, base - 5.0)] if not d else [(cx - d * 2.0, base - 18.0), (cx - d * 8.4, base - 10.0)], 0.4, MAN_DK)
    # thorax, upright
    cel(draw, Limb([(cx - d * 1.0, base - 12.0), (cx + d * 1.0, base - 24.0)], [3.2, 2.6]), MAN, sh=(0.6, 0.4))
    # raptorial arms: folded scythes held up in front
    for s in ((-1, 1) if not d else (d, -d)):
        near = not d or s == d
        col = MAN if near else shade(MAN, 0.6)
        sx = cx + (s * 2.0 if not d else d * 1.6)
        elbow = (sx + (s * 5.4 if not d else d * 6.0), base - 26.0 - step * 0.4)
        wrist = (sx + (s * 3.4 if not d else d * 7.4), base - 18.0)
        cel(draw, Limb([(sx, base - 22.0), elbow], [1.6, 1.4]), col, sh=None)
        cel(draw, Limb([elbow, wrist], [1.8, 1.0]), col, sh=None)
        for t in (0.4, 0.7):
            p = (elbow[0] + (wrist[0] - elbow[0]) * t, elbow[1] + (wrist[1] - elbow[1]) * t)
            Poly([(p[0] - 0.5, p[1]), (p[0] + 0.5, p[1]), (p[0] - (s if not d else d) * 1.4, p[1] + 1.0)]).draw(draw, fill=MAN_DK)
        cel(draw, Limb([wrist, (wrist[0] - (s * 1.6 if not d else -d * 1.0), wrist[1] - 3.4)], [0.8, 0.3]), col, sh=None)
    # head: a triangle with huge bulb eyes on its corners
    hx, hy = cx + d * 2.4, base - 29.0
    tri = Poly([(hx - 8.0, hy - 3.4), (hx + 8.0, hy - 3.4), (hx + 1.6, hy + 6.0), (hx - 1.6, hy + 6.0)]) if not d else \
        Poly([(hx - d * 3.0, hy - 4.0), (hx + d * 6.0, hy - 3.0), (hx + d * 5.0, hy + 5.4), (hx + d * 2.0, hy + 5.4), (hx - d * 3.4, hy + 1.0)])
    cel(draw, tri, MAN, sh=(0.8, 0.8), hi=(0.4, 0.4))
    for s in ((-1, 1) if not d else (-d, d)):
        near = not d or s == d
        ex = hx + s * 7.0 if not d else hx + (d * 5.0 if s == d else -d * 2.4)
        cel(draw, Ell(ex, hy - 3.4, 3.4 if near else 2.4, 3.2), MAN, sh=None)
        if not back:
            ms_eye(draw, ex, hy - 3.2, 4.2 if near else 2.8, 4.6, (250, 120, 60), (float(d), 0.0), "bright", skin=MAN,
                   side=s if not d else (-d if near else d), lash=(40, 70, 30))
    # antennae
    for s in ((-1, 1) if not d else (-1, 1)):
        bx = hx + s * 1.6
        tip = (bx + (s * 6.0 if not d else d * 4.0 + s * 1.6), hy - 13.0 + (step if s > 0 else -step) * 0.6)
        stroke(draw, [(bx, hy - 3.0), ((bx + tip[0]) / 2 + (s if not d else 0) * 1.4, hy - 9.0), tip], 0.5, MAN_DK)
    if not back:
        stroke(draw, [(hx + d * 2.0 - 1.4, hy + 4.2), (hx + d * 2.0, hy + 4.8), (hx + d * 2.0 + 1.4, hy + 4.2)], 0.5, MAN_DK)


# ===================================================================
# JELLYFISH (84) -- a pink bell with a face, frilly hem, trailing tentacles
# ===================================================================

JELLY = (236, 150, 220)
JELLY_LT = (255, 206, 244)
JELLY_DK = (176, 96, 180)
JELLY_GLOW = (255, 250, 150)


def draw_jellyfish(draw, ox, oy, direction, frame):
    d, back, base, cx, ph = _setup(ox, oy, direction, frame, bob=[0, -2, -3, -1])
    wave = frame * math.pi / 2.0
    bell_y = base - 24.0
    pulse = [0.0, 0.8, 1.2, 0.6][frame]
    # tentacles
    for i in range(6):
        u = (i - 2.5) * 3.2
        pts = []
        for j in range(6):
            t = j / 5.0
            pts.append((cx + u + math.sin(wave + i + t * 3.0) * 1.8 - d * t * 4.0, bell_y + 3.0 + t * 20.0))
        cel(draw, Limb(pts, [1.2, 1.1, 1.0, 0.9, 0.7, 0.4]), JELLY if i % 2 else JELLY_DK, sh=None, lw=0.6)
    # oral arms, frilly, in the middle
    for s in (-1, 1):
        pts = [(cx + s * 1.6, bell_y + 3.0), (cx + s * 2.6 + math.sin(wave) * 1.0, bell_y + 9.0), (cx + s * 1.4, bell_y + 14.0)]
        cel(draw, Limb(pts, [2.0, 1.8, 0.8]), JELLY_LT, sh=None, lw=0.6)
    # the bell
    bw = 12.4 + pulse * 0.6
    bh = 11.0 - pulse * 0.6
    bell = arc_pts(cx - d * 0.6, bell_y, bw, bh, 180, 360, 30)
    hem = []
    n = 8
    for i in range(n + 1):
        t = i / float(n)
        x = cx - d * 0.6 + bw - 2 * bw * t
        hem.append((x, bell_y + (2.4 if i % 2 else 0.6)))
    cel(draw, Poly(bell + hem[1:-1]), JELLY, sh=(1.6, 1.4), hi=(1.0, 1.0),
        regions=[(Ell(cx - 5.0, bell_y - 6.0, 3.4, 2.0), JELLY_LT)])
    # glowing spots
    for (u, v) in ((-7.0, -3.0), (7.0, -3.0), (0.0, -8.0)):
        Ell(cx + u - d * 0.6, bell_y + v, 1.2, 1.2).draw(draw, fill=JELLY_GLOW if frame % 2 == 0 else lit(JELLY_GLOW, 0.6))
    if not back:
        fx = cx + d * 3.0
        _eyes(draw, fx - 4.0 if not d else fx + d * 1.0, fx + 4.0 if not d else fx + d * 5.0, bell_y - 2.0, d, (120, 40, 110), mood="bright",
              skin=JELLY, w=3.6, h=4.4, lash=(110, 40, 100))
        ms_mouth(draw, fx + d * 1.0, bell_y + 1.6, "cat", JELLY, 0.9)
        for s in (-1, 1):
            Ell(fx + s * 7.0, bell_y + 0.4, 1.6, 0.8).draw(draw, fill=lit(JELLY, 0.8))
    # a crackle of electricity
    if frame % 2 == 1:
        bolt(draw, [(cx + 13.0, bell_y + 4.0), (cx + 15.0, bell_y + 7.0), (cx + 13.6, bell_y + 8.0), (cx + 15.6, bell_y + 11.0)], 1.0, JELLY_GLOW)
        bolt(draw, [(cx - 13.0, bell_y + 8.0), (cx - 15.0, bell_y + 11.0), (cx - 13.6, bell_y + 12.0), (cx - 15.6, bell_y + 15.0)], 1.0, JELLY_GLOW)


# ===================================================================
# GORILLA (85) -- knuckle-walking: huge arms, a silver back, a heavy brow
# ===================================================================

GOR = (70, 66, 82)
GOR_FACE = (150, 136, 150)
GOR_SILVER = (184, 186, 200)


def draw_gorilla(draw, ox, oy, direction, frame):
    d, back, base, cx, ph = _setup(ox, oy, direction, frame)
    # short legs
    for s in ((-1, 1) if not d else (-1, 1)):
        fwd = ph * s
        lx = cx + (s * 4.4 if not d else -d * 3.0 + d * fwd * 2.0 + s * 1.0)
        lift = 1.0 if fwd < 0 else 0.0
        col = GOR if (not d or s == 1) else shade(GOR, 0.6)
        cel(draw, Limb([(lx, base - 9.0), (lx, base - 2.0 - lift)], [2.8, 2.6]), col, sh=(0.6, 0.0))
        cel(draw, Ell(lx + d * 0.8, base - 1.2 - lift, 3.0, 1.6), GOR_FACE if (not d or s == 1) else shade(GOR_FACE, 0.6), sh=None)
    # body: a huge chest and shoulders
    bw = 12.0 if not d else 10.4
    body = Ell(cx - d * 1.0, base - 17.0, bw, 10.4)
    cel(draw, body, GOR if not back else GOR_SILVER, sh=(1.6, 1.4))
    if back:
        cel(draw, Ell(cx, base - 12.0, bw * 0.7, 5.0), GOR, sh=None, line=False)
    elif not d:
        for s in (-1, 1):
            cel(draw, Ell(cx + s * 3.6, base - 18.4, 4.2, 3.4), GOR_FACE, sh=(0.6, 0.6), tone=shade(GOR_FACE, 0.8))
    else:
        cel(draw, Poly(arc_pts(cx - d * 1.0, base - 17.0, bw - 0.6, 9.8, 190 if d > 0 else 280, 260 if d > 0 else 350, 10) + [(cx - d * 1.0, base - 17.0)]),
            GOR_SILVER, sh=None, line=False)
    # arms: enormous, knuckles planted
    for s in ((-1, 1) if not d else (d, -d)):
        near = not d or s == d
        col = GOR if near else shade(GOR, 0.6)
        fwd = -ph * s if not d else (-ph if near else ph)
        sx = cx + (s * (bw - 2.0) if not d else s * 2.0)
        kx = sx + (s * 3.6 if not d else d * (fwd * 2.4 + (3.0 if near else -2.0)))
        ky = base - 1.4
        cel(draw, Limb([(sx, base - 24.0), (sx + (s * 3.4 if not d else d * 2.0), base - 14.0), (kx, ky - 3.0)], [4.4, 3.6, 3.0]), col, sh=(1.0, 0.6))
        cel(draw, Ell(kx, ky - 1.6, 3.4, 2.6), GOR_FACE if near else shade(GOR_FACE, 0.6), sh=(0.4, 0.4))
        if near and not back:
            for k in (-1.6, 0.0, 1.6):
                stroke(draw, [(kx + k, ky - 3.4), (kx + k, ky - 1.8)], 0.4, shade(GOR_FACE, 1.6))
    # head: sunk low between the shoulders, crested
    hx, hy = cx + d * 3.4, base - 27.0
    blob(draw, [Ell(hx, hy, 8.4, 7.6), Ell(hx - d * 1.4, hy - 6.4, 4.4, 3.4)], GOR if not back else GOR_SILVER, sh=(1.0, 0.9))
    if back:
        return
    fx = hx + d * 2.0
    cel(draw, Ell(fx, hy + 1.4, 6.2 if not d else 5.2, 5.4), GOR_FACE, sh=(0.8, 0.6))
    # the heavy brow shelf
    cel(draw, RRect(fx - 6.4 if not d else fx - 4.2, hy - 3.6, fx + 6.4 if not d else fx + 4.6, hy - 1.4, 1.0), shade(GOR, 0.4), sh=None)
    e1, e2 = (fx - 2.8, fx + 2.8) if not d else (fx + d * 0.6, fx + d * 3.4)
    _eyes(draw, e1, e2, hy + 0.2, d, (120, 60, 30), mood="sharp", skin=GOR_FACE, w=3.0, h=3.4)
    for s in ((-1, 1) if not d else (d,)):
        Ell(fx + s * 1.2 + d * 1.6, hy + 3.4, 0.7, 0.5).draw(draw, fill=shade(GOR_FACE, 2.4))
    stroke(draw, [(fx - 2.6 + d * 1.6, hy + 5.4), (fx + 2.6 + d * 1.6, hy + 5.4)], 0.6, shade(GOR_FACE, 2.4))


# ===================================================================
# CHAMELEON (86) -- turret eyes, a curled tail, colours that shift
# ===================================================================

CHAM = ((110, 200, 90), (90, 190, 150), (170, 200, 80), (100, 190, 120))
CHAM_SPOT = ((250, 200, 70), (255, 150, 90), (240, 230, 90), (255, 180, 120))


def draw_chameleon(draw, ox, oy, direction, frame):
    d, back, base, cx, ph = _setup(ox, oy, direction, frame)
    col = CHAM[frame]
    spot = CHAM_SPOT[frame]
    belly = lit(col, 1.2)
    # the curled tail
    if d:
        tb = (cx - d * 9.0, base - 12.0)
        spiral = [tb] + [(tb[0] - d * (5.4 + math.cos(math.radians(a)) * r), tb[1] + 2.0 + math.sin(math.radians(a)) * r)
                         for (a, r) in ((180, 5.4), (225, 5.4), (270, 5.0), (315, 4.2), (0, 3.4), (45, 2.6), (90, 1.8), (135, 1.2))]
    else:
        tb = (cx + 6.0, base - 6.0)
        spiral = [tb] + [(tb[0] + 7.4 + math.cos(math.radians(a)) * r, tb[1] - 3.0 + math.sin(math.radians(a)) * r)
                         for (a, r) in ((180, 5.4), (230, 5.0), (280, 4.6), (330, 3.8), (20, 3.0), (70, 2.2), (120, 1.4))]
    cel(draw, Limb(spiral, [2.8] + [2.6 - i * 0.26 for i in range(len(spiral) - 1)]), col, sh=(0.5, 0.5))
    if d:
        _quad_legs(draw, cx, base, d, ph, col, 6.0, -6.0, base - 10.0, w=2.2)
        body = Poly(_rot(arc_pts(cx, base - 13.4, 11.4, 7.0, 0, 360)[:-1], cx, base - 13.4, -d * 4))
        cel(draw, body, col, sh=(1.2, 1.2), regions=[(Ell(cx + d * 1.0, base - 9.4, 8.4, 2.6), belly)])
        for (u, v) in ((-5.0, -2.0), (0.0, -3.4), (4.4, -1.0)):
            Ell(cx + d * u, base - 13.4 + v, 1.6, 1.3).draw(draw, fill=spot)
        # the crest along the spine
        for k in range(4):
            x = cx - d * (7.0 - k * 4.0)
            Poly([(x - 1.4, base - 19.4 + k * 0.2), (x + 1.4, base - 19.6 + k * 0.2), (x, base - 22.0)]).draw(draw, fill=shade(col, 0.8))
        # a big head with a casque and one turret eye
        hx, hy = cx + d * 10.0, base - 20.0
        cel(draw, Poly([(hx - d * 6.4, hy + 4.0), (hx - d * 5.4, hy - 8.4), (hx + d * 1.0, hy - 5.4), (hx + d * 9.4, hy + 1.0),
                        (hx + d * 8.4, hy + 5.4), (hx, hy + 7.0)]), col, sh=(0.8, 0.8))
        cel(draw, Ell(hx + d * 1.4, hy - 0.4, 4.6, 4.4), col, sh=(0.5, 0.5))
        ms_eye(draw, hx + d * 1.8, hy - 0.4, 4.2, 4.2, (60, 40, 30), (float(d), 0.0), "bright", skin=col, side=-d)
        stroke(draw, [(hx + d * 5.0, hy + 4.2), (hx + d * 8.6, hy + 3.4)], 0.6, shade(col, 1.8))
        Ell(hx - d * 2.6, hy + 3.0, 1.4, 1.1).draw(draw, fill=spot)
        if frame == 2:
            # the tongue, snapping out
            stroke(draw, [(hx + d * 8.6, hy + 3.6), (hx + d * 16.0, hy + 2.4)], 1.0, (240, 110, 140))
            cel(draw, Ell(hx + d * 16.6, hy + 2.4, 2.0, 2.0), (240, 110, 140), sh=None, lw=0.5)
        return
    # head-on / from behind: a big head on a squat body, legs splayed like a frog's
    for s in (-1, 1):
        fwd = ph * s
        lift = 1.0 if fwd < 0 else 0.0
        for (u, dark) in ((9.4, True), (5.0, False)):
            lx = cx + s * u
            lc = shade(col, 0.6) if dark else col
            cel(draw, Limb([(lx - s * 2.0, base - 10.0), (lx + s * 1.4, base - 6.0), (lx, base - 1.8 - lift)], [2.2, 2.0, 1.6]), lc, sh=None)
            for k in (-1, 1):
                stroke(draw, [(lx, base - 1.8 - lift), (lx + k * 1.8, base - 0.4 - lift)], 1.1, lc)
    cel(draw, Ell(cx, base - 12.0, 10.4, 6.6), col, sh=(1.2, 1.0), regions=[(Ell(cx, base - 9.6, 6.4, 3.4), belly)])
    hx, hy = cx, base - 23.0
    cel(draw, Poly([(hx - 6.0, hy - 5.4), (hx, hy - 14.4), (hx + 6.0, hy - 5.4)]), col, sh=(0.5, 0.5))
    cel(draw, Ell(hx, hy, 11.4, 9.0), col, sh=(1.2, 1.0), hi=(0.7, 0.7))
    if back:
        for (u, v) in ((-4.0, -1.0), (3.6, 1.6), (0.0, -5.0)):
            Ell(hx + u, hy + v, 1.6, 1.3).draw(draw, fill=spot)
        return
    cel(draw, Ell(hx, hy + 4.0, 7.4, 3.6), belly, sh=None, line=False)
    for (u, v) in ((-8.0, 3.0), (8.4, 2.0)):
        Ell(hx + u, hy + v, 1.4, 1.1).draw(draw, fill=spot)
    # turret eyes on the sides of the head, each looking a different way
    looks = ((-0.8, 0.0), (0.8, -0.4)) if frame % 2 == 0 else ((0.6, 0.4), (-0.6, 0.0))
    for s, lk in zip((-1, 1), looks):
        ex = hx + s * 7.4
        cel(draw, Ell(ex, hy - 2.0, 4.8, 4.6), col, sh=(0.5, 0.5))
        ms_eye(draw, ex, hy - 2.0, 4.4, 4.4, (60, 40, 30), lk, "bright", skin=col, side=s)
    stroke(draw, [(hx - 4.4, hy + 4.4), (hx, hy + 5.4), (hx + 4.4, hy + 4.4)], 0.7, shade(col, 1.8))


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
