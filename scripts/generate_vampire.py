#!/usr/bin/env python3
"""Generate sprites/vampire.png -- the Vampire.

A cartoon count: blue-black hair slicked back to a sharp widow's peak, long
pointed ears, pale lilac skin, big red eyes and a grin with two fangs; a
plum cape lined in crimson, with a tall two-tone collar standing up behind
his head and a bat-wing hem; a tailcoat over a red waistcoat, a white jabot
pinned with a ruby, and white gloves.
"""

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sprite_base import (
    Ell, Poly, arc_pts, bez, blob, cel, clipped, face_anchor, gem, generate_character, head_face,
    head_skull, ink, lit, rig, rig_arms, rig_legs, rig_torso, shade, stroke,
)

CAPE = (58, 42, 86)
COAT = (70, 52, 100)
LINING = (222, 40, 66)
VEST = (186, 30, 58)
SKIN_V = (240, 234, 250)
HAIR = (46, 40, 78)
IRIS = (228, 36, 62)
WHITE = (248, 246, 252)
GOLD = (246, 200, 84)
MOUTH_IN = (122, 26, 52)


# One wing of the collar, in (out from the head's middle, y): the edge
# against the head rising to the top tip, then the scalloped outer edge back
# down to the shoulder. The lining is the same wing with that edge pulled in.
def _wing(hy, y0):
    inner = [(4.6, y0 + 0.4)] + bez((8.0, y0 - 1.0), (12.6, hy + 3.0), (15.2, hy - 9.8), 6)
    outer = [(16.0, hy - 4.6), (19.6, hy - 3.4), (17.4, hy + 1.4), (19.8, hy + 4.8), (13.4, y0 + 1.4)]
    lining = [(15.4, hy - 3.2), (17.8, hy - 2.4), (15.8, hy + 1.8), (17.6, hy + 4.4), (12.4, y0 + 0.4)]
    return inner, outer, lining


def _collar(r, draw):
    """The tall stand-up collar: two scalloped wings rising behind the head,
    crimson inside and plum outside."""
    hx, hy, d = r.hx, r.head_cy, r.d
    y0 = r.sh_y - 0.6
    inner, outer, lining = _wing(hy, y0)
    if d:
        # the near half, seen edge-on: plum outside, the crimson lining
        # showing along its front edge
        k = -d
        edge = [(-1.6, y0 + 0.6), (2.0, y0 - 2.0)] + bez((4.0, hy + 4.4), (8.0, hy - 3.0), (14.8, hy - 8.6), 5)
        back = bez((16.4, hy - 3.6), (18.4, hy + 3.0), (11.0, y0 - 0.2), 5)
        lining = bez((14.0, hy - 5.0), (7.0, hy + 1.0), (2.6, y0 + 0.4), 5)
        cel(draw, Poly([(hx + k * u, y) for (u, y) in edge + back]), CAPE, sh=None,
            regions=[(Poly([(hx + k * u, y) for (u, y) in edge + lining]), LINING)])
        return
    if r.back:
        # from behind, the plum outside of the same two wings either side of
        # the head, the lining just showing over the edge against it
        for s in (-1, 1):
            peek = inner[1:] + [(u + 1.4, y + 0.6) for (u, y) in inner[:0:-1]]
            cel(draw, Poly([(hx + s * u, y) for (u, y) in inner + outer]), CAPE, sh=(0.0, 0.8),
                regions=[(Poly([(hx + s * u, y) for (u, y) in peek]), LINING)])
        return
    for s in (-1, 1):
        rim = Poly([(hx + s * u, y) for (u, y) in inner + outer])
        cel(draw, rim, CAPE, sh=None,
            regions=[(Poly([(hx + s * u, y) for (u, y) in inner + lining]), LINING)])


def _hem(x1, x0, y, dip, n):
    """A bat-wing hem from x1 to x0: n scallops arching up between points
    that hang down."""
    pts = []
    for i in range(n):
        a = x1 + (x0 - x1) * i / float(n)
        b = x1 + (x0 - x1) * (i + 1) / float(n)
        seg = bez((a, y), ((a + b) / 2.0, y - dip * 2.0), (b, y), 5)
        pts += seg if i == 0 else seg[1:]
    return pts


def _cape(r, draw):
    cx, d = r.cx, r.d
    sway = r.ph * 1.4
    bottom = r.base_y - 0.8
    top = r.sh_y - 2.6
    if d:
        k = -d
        tail = cx + k * (11.4 + abs(sway))
        lining = Poly([(cx + k * 0.6, top), (cx + k * 5.0, top + 1.6), (tail, bottom)] +
                      _hem(tail, cx + k * 1.6, bottom, 1.1, 3)[1:] + [(cx - k * 1.4, r.hip_y)])
        cel(draw, lining, LINING, sh=None, line_color=ink(CAPE))
        cel(draw, Poly([(cx + k * 0.8, top + 0.2), (cx + k * 5.0, top + 1.8), (tail, bottom), (cx + k * 8.0, bottom - 1.6),
                        (cx + k * 4.6, r.waist_y)]), CAPE, sh=None)
        return
    w = r.sh_w
    x0, x1 = cx - w - 6.2 + sway, cx + w + 6.2 + sway
    pts = [(cx - w + 0.2, top), (cx + w - 0.2, top)] + _hem(x1, x0, bottom, 1.2, 4)
    if r.back:
        shape = Poly(pts)
        cel(draw, shape, CAPE, sh=(2.0, 0.0))
        # ribbed like a bat's wing: a fold from the shoulders to each point
        # of the hem
        for i in range(1, 4):
            hx_ = x1 + (x0 - x1) * i / 4.0
            stroke(draw, [(cx + (hx_ - cx) * 0.25, r.sh_y + 1.0), (hx_, bottom - 0.6)], 0.6, lit(CAPE, 0.9))
        return
    # crimson inside, the plum outside wrapping round each edge
    edges = [(Poly([(cx + s * (w - 0.2), top), (cx + s * 40.0, top), (cx + s * 40.0, bottom + 3.0),
                    (cx + s * (w + 4.4) + sway, bottom + 3.0), (cx + s * (w + 0.4), top + 3.0)]), CAPE)
             for s in (-1, 1)]
    cel(draw, Poly(pts), LINING, sh=(1.6, 0.0), regions=edges, line_color=ink(CAPE))


def _streak(a, b, w):
    """A streak of shine from a to b, pointed at both ends and w wide."""
    dx, dy = b[0] - a[0], b[1] - a[1]
    ln = math.hypot(dx, dy) or 1.0
    nx, ny = -dy / ln * w, dx / ln * w
    mx, my = (a[0] + b[0]) / 2.0, (a[1] + b[1]) / 2.0
    return [a, (mx + nx, my + ny), b, (mx - nx, my - ny)]


def _gloss(r, oy):
    """Combed-back streaks of shine across the crown."""
    hx, d = r.hx, r.d
    if d:
        return [_streak((hx + d * u0, oy + y0), (hx + d * u1, oy + y1), 0.8)
                for (u0, y0, u1, y1) in ((5.0, -9.4, -5.4, -7.2), (3.2, -6.4, -7.6, -3.4))]
    if r.back:
        return [_streak((hx + x0, oy + y0), (hx + x1, oy + y1), 0.8)
                for (x0, y0, x1, y1) in ((-2.4, -9.6, -5.0, -3.0), (2.4, -9.6, 5.0, -3.0), (0.0, -10.6, 0.0, -4.0))]
    return [_streak((hx + x0, oy + y0), (hx + x1, oy + y1), 0.8)
            for (x0, y0, x1, y1) in ((-2.6, -10.0, -6.0, -5.4), (2.6, -10.0, 6.0, -5.4))]


def _hair(r, draw):
    """Slicked straight back from a sharp widow's peak, with pointed sideburns
    and flicks at the nape: the count's silhouette."""
    hx, hy, rx, ry, d = r.hx, r.head_cy, r.head_rx, r.head_ry, r.d
    v = 1.3
    oy = hy - 0.5
    shine = lit(HAIR, 2.4)
    if r.back:
        outer = arc_pts(hx, oy, rx + v, ry + v, 140, 400, 44)
        lb, rb = outer[0], outer[-1]
        nape = hy + 11.2
        pts = outer + [(rb[0] - 1.6, nape - 0.6), (hx + 4.6, nape + 0.4), (hx + 3.0, nape + 2.2), (hx + 1.2, nape + 0.6),
                       (hx - 1.2, nape + 0.6), (hx - 3.0, nape + 2.2), (hx - 4.6, nape + 0.4), (lb[0] + 1.6, nape - 0.6)]
    elif d:
        def P(u, y):
            return (hx + d * u, y)
        R, cu = rx + v, -0.6
        outer = [P(cu + R * math.cos(math.radians(a)), oy + (ry + v) * math.sin(math.radians(a)))
                 for a in (-58 - 147 * i / 30.0 for i in range(31))]
        # down the back to two flicks at the nape, round the ear to a sideburn
        # that stops behind the near eye, then up the hairline to the brow
        pts = outer + [P(cu - R - 1.4, hy + 7.6), P(cu - R + 2.2, hy + 6.4), P(cu - R + 1.6, hy + 9.2),
                       P(-5.0, hy + 6.2), P(-3.4, hy + 2.0), P(-2.2, hy + 4.6), P(-1.0, hy - 0.4)]
        pts += [P(u, y) for (u, y) in bez((-0.4, hy - 2.8), (3.0, hy - 5.0), (rx * 0.66, hy - 7.8), 5)]
    else:
        outer = arc_pts(hx, oy, rx + v, ry + v, 172, 368, 40)
        # the hairline, right temple to left, dipping to the peak
        right = [(hx + rx + 0.2, hy + 4.6), (hx + rx - 1.6, hy + 0.4)] + \
            bez((hx + rx - 2.2, hy - 2.6), (hx + rx - 3.6, hy - 6.8), (hx + 5.2, hy - 6.9), 4) + \
            bez((hx + 4.0, hy - 6.6), (hx + 1.6, hy - 6.0), (hx, hy - 2.6), 4)[1:]
        left = [(2 * hx - x, y) for (x, y) in right[::-1]][1:]
        pts = outer + right + left
    shape = Poly(pts)
    if not r.back:
        # the hair's shadow on the forehead
        clipped(draw, Ell(hx, hy, rx, ry), lambda l: shape.shift(0.3, 1.1).draw(l, fill=shade(SKIN_V)))
    cel(draw, shape, HAIR, sh=(1.0, 1.3))
    clipped(draw, shape, lambda l: [Poly(seg).draw(l, fill=shine) for seg in _gloss(r, oy)])


def _ears(r, draw):
    hx, hy, rx, d = r.hx, r.head_cy, r.head_rx, r.d
    if d:
        ex = hx - d * 2.6
        ear = Poly([(ex + d * 1.2, hy + 0.6), (ex - d * 5.2, hy - 2.8), (ex - d * 1.0, hy + 5.0), (ex + d * 1.4, hy + 4.4)])
        cel(draw, ear, SKIN_V, sh=(0.3, 0.3))
        stroke(draw, [(ex, hy + 2.2), (ex - d * 2.6, hy + 0.2)], 0.5, shade(SKIN_V, 1.4))
        return
    for s in (-1, 1):
        ex = hx + s * (rx - 1.2)
        cel(draw, Poly([(ex, hy - 0.2)] + bez((ex + s * 3.0, hy - 1.0), (ex + s * 5.0, hy - 2.0), (ex + s * 6.6, hy - 3.8), 3) +
                       bez((ex + s * 5.0, hy + 1.0), (ex + s * 3.4, hy + 4.4), (ex + s * 0.6, hy + 5.4), 3)), SKIN_V, sh=(0.4, 0.4))
        if not r.back:
            stroke(draw, [(ex + s * 1.0, hy + 2.8), (ex + s * 4.0, hy - 1.4)], 0.55, shade(SKIN_V, 1.5))


def _mouth(r, draw):
    """A grin with two fangs, wider than the kit's: the one feature that says
    vampire at a glance."""
    _, _, _, mx, my = face_anchor(r)
    d = r.d
    w = 2.4 if not d else 1.7
    mx -= d * 0.2
    Poly([(mx - w, my - 0.7), (mx + w, my - 0.7)] + arc_pts(mx, my - 0.7, w, 1.9, 0, 180, 10)[1:-1]).draw(
        draw, fill=MOUTH_IN)
    Ell(mx + d * 0.4, my + 0.7, w * 0.55, 0.6).draw(draw, fill=(232, 102, 120))
    for s in (-1, 1):
        fx = mx + s * w * 0.56
        Poly([(fx - 0.62, my - 0.75), (fx + 0.62, my - 0.75), (fx, my + 1.25)]).draw(draw, fill=WHITE)


def draw_vampire(draw, ox, oy, direction, frame):
    r = rig(ox, oy, direction, frame, build=0.96)
    d = r.d
    cx = r.cx
    if not r.back:
        _cape(r, draw)
        _collar(r, draw)
    if d:
        rig_arms(r, draw, COAT, WHITE, layer="far", cuff=WHITE)
    rig_legs(r, draw, shade(COAT, 0.8), (40, 32, 52))
    rig_torso(r, draw, COAT)
    if not r.back:
        vx = cx + d * 1.8
        vw = 3.4 if not d else 2.4
        # the waistcoat between the coat's lapels, buttoned in gold
        cel(draw, Poly([(vx - vw, r.sh_y - 1.6), (vx + vw, r.sh_y - 1.6), (vx + vw * 0.9, r.waist_y + 2.6),
                        (vx, r.waist_y + 3.8), (vx - vw * 0.9, r.waist_y + 2.6)]), VEST, sh=(0.8, 0.0))
        for y in (r.sh_y + 3.6, r.sh_y + 6.0):
            cel(draw, Ell(vx, y, 0.75, 0.75), GOLD, sh=None, lw=0.5)
        # a frilled jabot, pinned with a ruby
        blob(draw, [Ell(vx, r.sh_y - 0.6, 2.8 if not d else 2.0, 1.8), Ell(vx - 1.0, r.sh_y + 1.6, 1.8, 1.6),
                    Ell(vx + 1.0, r.sh_y + 1.6, 1.8, 1.6), Ell(vx, r.sh_y + 2.8, 1.4, 1.4)], WHITE, sh=(0.6, 0.6))
        gem(draw, vx, r.sh_y + 0.2, 1.3, LINING)
    rig_arms(r, draw, COAT, WHITE, layer="near", cuff=WHITE)
    if r.back:
        _cape(r, draw)
        _collar(r, draw)
    head_skull(r, draw, SKIN_V, ears=False)
    if not r.back:
        head_face(r, draw, SKIN_V, iris=IRIS, mood="sharp", mouth=None, blush=True, brows=True,
                  brow_color=HAIR, lash=(52, 30, 60))
        _mouth(r, draw)
    _hair(r, draw)
    _ears(r, draw)


def main():
    generate_character("vampire", draw_func=draw_vampire)


if __name__ == "__main__":
    main()
