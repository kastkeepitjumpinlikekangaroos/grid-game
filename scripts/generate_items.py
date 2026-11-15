#!/usr/bin/env python3
"""Generate sprites/items.png -- the pickups, drawn as the characters and the props are.

    python3 scripts/generate_items.py                 # needs Pillow
    python3 scripts/generate_items.py --out /tmp/x    # somewhere else, to look at first
    python3 scripts/item_gallery.py /tmp/itemgallery  # then look at it (see that script)

Layout: a 4x4 grid of 128x128 cells, read left to right and top to bottom
(ItemSprites.scala reads the same numbers):

    0-7   the gem, turning: eight steps of the 45 degrees that bring an
          eight-sided stone back to where it began, so the eight loop
    8     the heart         9   the star
    10    the shield        11  the fence
    12    a twinkle, white, for the renderer to tint, turn and size
    13-15 empty

A cell is 64 authoring units across, as a character's frame is, and is drawn
CELL_UNITS (40) world units across in a match: 3.2 texels a world unit, which
is a texel a pixel at High on a HiDPI screen. An item's art is about 44 units
of its cell's 64 (27 world units, a character's width), centred on (32, 32),
which is the point the renderer bobs, turns and swells it about.

Style -- the characters' own (sprite_base.py), because a pickup lying among
them has to look as if it belongs to them:

- Flat cel shading: one flat colour, one hard-edged shadow tone on the side
  away from the light (upper left), a hard highlight. No gradients.
- Outlines in a dark version of each part's own hue (`ink`), ITEM_INK times
  the kit's weight, and the characters' contour ring round the whole
  (`add_contour`), so an item reads at the weight of the figure beside it and
  holds on snow as well as on the deck.
- Big simple shapes: at Low an item is under 30 pixels across. Each is one
  silhouette nobody else has and two or three areas of colour; nothing on one
  is thinner than about two units.
"""

import argparse
import math
import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sprite_base import (CLEAR, Ell, Poly, ScaledDraw, add_contour, arc_pts, bez, cel, clipped,  # noqa: E402
                         ink, lit, mix, shade, stroke)

CELL = 128          # a cell of the atlas, in texels
UNITS = 64          # the space a cell is authored in
COLS, ROWS = 4, 4
SS = 4              # supersampling: a cell is drawn at 4x and resampled down
# World units a cell is drawn across in a match (ItemPainter.CELL)
CELL_UNITS = 40

GEM_FRAMES = 8
GEM, HEART, STAR, SHIELD, FENCE, TWINKLE = 0, 8, 9, 10, 11, 12

# Lines are this much heavier than the kit draws them: a cell's unit is 0.625 of a world unit
# where a character's is 0.75, and a pickup wants the weight of the props (generate_tiles.PROP_INK)
ITEM_INK = 1.5

CX, CY = 32.0, 32.0

# ---------------------------------------------------------------------------
# Palette: each item's own colour (ItemType.colorRGB), a little lighter, so its
# shadow tone and its ink both have room under it
# ---------------------------------------------------------------------------
GEM_COL = (38, 204, 232)
HEART_COL = (244, 62, 108)
STAR_COL = (255, 216, 62)
SHIELD_COL = (176, 76, 208)
SHIELD_RIM = (232, 232, 244)
WOOD = (222, 168, 104)
GOLD = (252, 204, 84)
NAIL = (108, 96, 104)
WHITE = (255, 255, 255)


class ItemDraw(ScaledDraw):
    """A ScaledDraw that inks every outline ITEM_INK times heavier."""

    def _w(self, width):
        return max(1, int(round((width if width else 1) * self._s * ITEM_INK)))

    def sub(self):
        img = Image.new("RGBA", self._img.size, CLEAR)
        return ItemDraw(ImageDraw.Draw(img), self._s, img)


def rounded(pts, rad, n=5):
    """A polygon grown by `rad` with every corner that sticks out rounded: the points of a
    star, the tip of a heart. Corners that point in are left as the crossing of their two
    edges moved out. `pts` runs clockwise on screen (y down)."""
    m = len(pts)
    out = []
    for i in range(m):
        p0, p1, p2 = pts[i - 1], pts[i], pts[(i + 1) % m]
        ax, ay = p1[0] - p0[0], p1[1] - p0[1]
        bx, by = p2[0] - p1[0], p2[1] - p1[1]
        la, lb = math.hypot(ax, ay) or 1.0, math.hypot(bx, by) or 1.0
        # outward normals of the two edges (clockwise on screen: outside is to the left of travel)
        n0 = (ay / la, -ax / la)
        n1 = (by / lb, -bx / lb)
        cross = ax * by - ay * bx
        if cross > 1e-9:  # sticks out: an arc from one normal round to the other
            a0 = math.atan2(n0[1], n0[0])
            a1 = math.atan2(n1[1], n1[0])
            while a1 < a0:
                a1 += 2 * math.pi
            for k in range(n + 1):
                a = a0 + (a1 - a0) * k / float(n)
                out.append((p1[0] + rad * math.cos(a), p1[1] + rad * math.sin(a)))
        else:  # points in: where the two moved edges cross
            d = 1.0 + n0[0] * n1[0] + n0[1] * n1[1]
            if d < 1e-6:
                out.append((p1[0] + n0[0] * rad, p1[1] + n0[1] * rad))
            else:
                out.append((p1[0] + (n0[0] + n1[0]) * rad / d, p1[1] + (n0[1] + n1[1]) * rad / d))
    return out


def gloss(d, pts, width, col=WHITE):
    """A streak of light along `pts`: a round-ended stroke, never inked."""
    stroke(d, pts, width, col)


# ---------------------------------------------------------------------------
# The gem: a brilliant, eight-sided, seen from a little above, turning
# ---------------------------------------------------------------------------

GEM_TABLE_R = 12.0    # its flat top
GEM_GIRDLE_R = 22.0   # its widest
GEM_CROWN_H = 9.0     # table above girdle
GEM_PAVILION_H = 26.5  # girdle above the point
GEM_PITCH = math.radians(24.0)  # how far down on it we look
# The light: from the upper left and in front
_L = (-0.52, 0.66, 0.54)


def _gem_tone(col, part, k):
    """The flat tone of a facet of the table, the crown or the pavilion catching `k` of the light
    (-1..1). The table is always the palest thing on it; the crown runs from a flash of near white,
    on the one facet that faces the light, down to the stone's own colour and a tone under it; the
    pavilion, which faces the ground, is the stone's colour at its lightest."""
    if part == "table":
        return lit(col, 1.25)
    if part == "crown":
        if k > 0.9:
            return lit(col, 2.4)
        if k > 0.7:
            return lit(col, 0.55)
        if k > 0.42:
            return col
        return shade(col, 0.9)
    if k > 0.22:
        return col
    if k > -0.05:
        return shade(col, 0.9)
    return shade(col, 1.8)


def gem(d, frame):
    """Frame `frame` of GEM_FRAMES: the stone turned that many eighths of 45 degrees."""
    turn = math.radians(45.0 * frame / GEM_FRAMES)
    cp, sp = math.cos(GEM_PITCH), math.sin(GEM_PITCH)
    # where its middle sits in the cell: the girdle a little above the cell's middle, so the
    # whole stone, table to point, is centred
    oy = CY - 5.5

    def ring(r, y, phase=0.0):
        return [(r * math.cos(turn + phase + i * math.pi / 4), y, r * math.sin(turn + phase + i * math.pi / 4))
                for i in range(8)]

    def proj(p):
        x, y, z = p
        return (CX + x, oy - (y * cp - z * sp))

    table = ring(GEM_TABLE_R, GEM_CROWN_H, math.pi / 8)
    girdle = ring(GEM_GIRDLE_R, 0.0)
    culet = (0.0, -GEM_PAVILION_H, 0.0)

    faces = [("table", list(reversed(table)))]
    for i in range(8):
        j = (i + 1) % 8
        # the crown: a triangle down from the table's edge to each of the girdle's corners, and
        # one up from the girdle's edge to each of the table's (which stand between the girdle's)
        faces.append(("crown", [table[i - 1], table[i], girdle[i]]))
        faces.append(("crown", [table[i], girdle[j], girdle[i]]))
        # the pavilion: a triangle from each edge of the girdle down to the point
        faces.append(("pavilion", [girdle[i], girdle[j], culet]))

    def normal(f):
        (ax, ay, az), (bx, by, bz), (cx, cy, cz) = f[0], f[1], f[2]
        ux, uy, uz = bx - ax, by - ay, bz - az
        vx, vy, vz = cx - ax, cy - ay, cz - az
        nx, ny, nz = uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx
        ln = math.sqrt(nx * nx + ny * ny + nz * nz) or 1.0
        nx, ny, nz = nx / ln, ny / ln, nz / ln
        # outward: away from the stone's axis point level with the girdle
        mx = sum(p[0] for p in f) / len(f)
        my = sum(p[1] for p in f) / len(f) - 1.0
        mz = sum(p[2] for p in f) / len(f)
        if nx * mx + ny * my + nz * mz < 0:
            nx, ny, nz = -nx, -ny, -nz
        return nx, ny, nz

    ll = math.sqrt(sum(c * c for c in _L))
    lx, ly, lz = (c / ll for c in _L)
    seen = []
    for part, f in faces:
        nx, ny, nz = normal(f)
        # toward us: we look from in front and above
        if ny * sp + nz * cp <= 1e-6:
            continue
        seen.append((part, [proj(p) for p in f], nx * lx + ny * ly + nz * lz))

    # its outline is the hull of everything we see
    hull = Poly(_hull([p for _, pts, _ in seen for p in pts]))

    def facets(m):
        # every facet a flat tone, with no line of its own
        for part, pts, k in seen:
            Poly(pts).draw(m, fill=_gem_tone(GEM_COL, part, k))
        # the edges between them, light and thin: what makes it cut rather than moulded
        edge = lit(GEM_COL, 2.3)
        for _, pts, _ in seen:
            for a in range(len(pts)):
                stroke(m, [pts[a], pts[(a + 1) % len(pts)]], 0.7, edge)
        # the light it catches, fixed where the light is while the stone turns under it
        _glint(m, CX - 7.0, oy - 5.6, 4.4)
        _glint(m, CX + 8.6, oy + 4.2, 2.3)

    clipped(d, hull, facets)
    hull.draw(d, fill=None, outline=ink(GEM_COL), width=1.0)


def _glint(d, x, y, rad):
    k = rad * 0.26
    Poly([(x, y - rad), (x + k, y - k), (x + rad, y), (x + k, y + k), (x, y + rad),
          (x - k, y + k), (x - rad, y), (x - k, y - k)]).draw(d, fill=WHITE)


def _hull(pts):
    pts = sorted(set((round(x, 4), round(y, 4)) for x, y in pts))
    if len(pts) < 3:
        return pts

    def cross(o, a, b):
        return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0])

    lower, upper = [], []
    for p in pts:
        while len(lower) >= 2 and cross(lower[-2], lower[-1], p) <= 0:
            lower.pop()
        lower.append(p)
    for p in reversed(pts):
        while len(upper) >= 2 and cross(upper[-2], upper[-1], p) <= 0:
            upper.pop()
        upper.append(p)
    return lower[:-1] + upper[:-1]


# ---------------------------------------------------------------------------
# The heart
# ---------------------------------------------------------------------------

def heart_pts(k=1.0, n=72, ox=CX, oy=CY):
    """The heart everyone draws, `k` times its size: 32 units across at k = 1."""
    pts = []
    for i in range(n):
        t = 2 * math.pi * i / n
        x = 16 * math.sin(t) ** 3
        y = -(13 * math.cos(t) - 5 * math.cos(2 * t) - 2 * math.cos(3 * t) - math.cos(4 * t))
        pts.append((ox + x * k, oy + (y + 2.6) * k))
    return pts


def heart(d):
    k = 1.36
    # the curve's own middle is well above the middle of what it draws
    oy = CY - 7.0
    body = Poly(heart_pts(k, oy=oy))
    cel(d, body, HEART_COL, sh=(2.6, 2.8), tone=shade(HEART_COL, 1.25), lw=1.0)
    # the shine on its upper left lobe: a fat streak and a dot under it, hard-edged
    clipped(d, body, lambda m: (
        gloss(m, bez((CX - 15.5, oy - 5.5), (CX - 15.0, oy - 13.5), (CX - 7.5, oy - 14.6), 8), 4.2),
        Ell(CX - 14.8, oy + 1.2, 1.9, 1.9).draw(m, fill=WHITE)))
    body.draw(d, fill=None, outline=ink(HEART_COL), width=1.0)


# ---------------------------------------------------------------------------
# The star
# ---------------------------------------------------------------------------

def star_pts(rad, inner, rot=-90.0, ox=CX, oy=CY):
    pts = []
    for i in range(10):
        a = math.radians(rot + i * 36.0)
        r = rad if i % 2 == 0 else rad * inner
        pts.append((ox + r * math.cos(a), oy + r * math.sin(a)))
    return pts


def star(d):
    # a fat star with round points: a thin one grown by its rounding
    oy = CY + 1.6
    body = Poly(rounded(star_pts(19.6, 0.5, oy=oy), 3.6))
    cel(d, body, STAR_COL, sh=(2.2, 2.4), tone=shade(STAR_COL, 1.35), lw=1.0)
    # a paler star set in its face, toward the light
    clipped(d, body, lambda m: Poly(rounded(star_pts(10.5, 0.52, ox=CX - 1.6, oy=oy - 1.8), 2.2)).draw(
        m, fill=lit(STAR_COL, 1.5)))
    clipped(d, body, lambda m: gloss(m, [(CX - 5.6, oy - 6.4), (CX - 1.6, oy - 13.4)], 2.6))
    body.draw(d, fill=None, outline=ink(STAR_COL), width=1.0)


# ---------------------------------------------------------------------------
# The shield
# ---------------------------------------------------------------------------

def shield_pts(w, top, tip, ox=CX, oy=CY, peak=1.4):
    """A heater shield: a top rising a little to its middle, its corners rounded off, straight
    sides, and two curves in to a point. `w` is half its width, `top` and `tip` how far over and
    under (ox, oy) its corners and its point are."""
    r = min(4.5, w * 0.3)
    right = (bez((ox, oy - top - peak), (ox + w * 0.5, oy - top - peak * 0.4), (ox + w - r, oy - top), 5) +
             bez((ox + w - r, oy - top), (ox + w, oy - top), (ox + w, oy - top + r), 4)[1:] +
             bez((ox + w, oy - 1.0), (ox + w * 0.96, oy + tip * 0.52), (ox, oy + tip), 9))
    left = [(2 * ox - x, y) for (x, y) in reversed(right[1:-1])]
    return right + left


def shield(d):
    oy = CY - 0.5
    outer = Poly(shield_pts(18.6, 19.6, 22.5, oy=oy))
    face = Poly(shield_pts(14.2, 15.4, 17.2, oy=oy, peak=1.1))
    # the rim: pale steel, its lower right in shade
    cel(d, outer, SHIELD_RIM, sh=(2.0, 2.2), lw=1.0)
    # the face: the item's purple, ridged down the middle, the half away from the light darker
    right_half = Poly([(CX, oy - 30), (CX + 30, oy - 30), (CX + 30, oy + 30), (CX, oy + 30)])
    cel(d, face, SHIELD_COL, sh=None, regions=[(right_half, shade(SHIELD_COL, 1.2))], lw=0.75)
    # light across its upper left, as on anything polished
    clipped(d, face, lambda m: (
        gloss(m, [(CX - 12.6, oy - 3.5), (CX - 4.5, oy - 15.8)], 3.4, lit(SHIELD_COL, 1.9)),
        gloss(m, [(CX - 12.2, oy + 3.6), (CX - 9.8, oy - 0.2)], 1.9, lit(SHIELD_COL, 1.9))))
    face.draw(d, fill=None, outline=ink(SHIELD_COL), width=0.75)
    # the boss: a gold stud at its heart
    cel(d, Ell(CX, oy - 1.4, 5.6, 5.6), GOLD, sh=(1.1, 1.2), lw=0.85)
    Ell(CX - 1.7, oy - 3.2, 1.5, 1.5).draw(d, fill=WHITE)


# ---------------------------------------------------------------------------
# The fence
# ---------------------------------------------------------------------------

def fence(d):
    """What the item puts down: boards with pointed tops nailed to two rails."""
    oy = CY + 1.5
    top, shoulder, bottom = oy - 20.5, oy - 14.0, oy + 19.5
    half = 5.6
    rail = shade(WOOD, 1.5)
    rails = (oy - 7.6, oy + 8.6)
    # the rails, behind the boards
    for ry in rails:
        r = Poly([(CX - 22.5, ry - 3.0), (CX + 22.5, ry - 3.0), (CX + 22.5, ry + 3.0), (CX - 22.5, ry + 3.0)])
        cel(d, r, rail, sh=(0.0, 1.8), lw=0.85)
    # three boards, the middle one a little taller
    for i, bx in enumerate((CX - 14.6, CX, CX + 14.6)):
        lift = 2.4 if i == 1 else 0.0
        board = Poly(rounded([(bx, top - lift + 1.0), (bx + half - 0.6, shoulder - lift), (bx + half - 0.6, bottom - 0.6),
                              (bx - half + 0.6, bottom - 0.6), (bx - half + 0.6, shoulder - lift)], 0.6, 3))
        cel(d, board, WOOD, sh=(2.2, 0.0), tone=shade(WOOD, 1.1), hi=(1.3, 0.0), hi_tone=lit(WOOD, 0.9), lw=1.0)
        # a nail where it crosses each rail
        for ry in rails:
            Ell(bx - 0.3, ry, 1.5, 1.5).draw(d, fill=NAIL)
            Ell(bx - 0.75, ry - 0.5, 0.55, 0.55).draw(d, fill=lit(NAIL, 2.2))


# ---------------------------------------------------------------------------
# The twinkle
# ---------------------------------------------------------------------------

def twinkle(d):
    """A four-pointed glint, white, with a thin line round it so that it shows on snow: the
    renderer tints it its item's colour, and turns and sizes it as it comes and goes."""
    rad, k = 21.0, 4.6
    pts = [(CX, CY - rad), (CX + k, CY - k), (CX + rad, CY), (CX + k, CY + k), (CX, CY + rad),
           (CX - k, CY + k), (CX - rad, CY), (CX - k, CY - k)]
    Poly(pts).draw(d, fill=WHITE, outline=(92, 104, 148), width=0.7)


# ---------------------------------------------------------------------------
# The atlas
# ---------------------------------------------------------------------------

def cells():
    """(index, draw function) for every cell that holds something."""
    out = [(GEM + f, (lambda d, f=f: gem(d, f))) for f in range(GEM_FRAMES)]
    out += [(HEART, heart), (STAR, star), (SHIELD, shield), (FENCE, fence), (TWINKLE, twinkle)]
    return out


def render_cell(fn, contour=True):
    img = Image.new("RGBA", (CELL * SS, CELL * SS), CLEAR)
    fn(ItemDraw(ImageDraw.Draw(img), CELL * SS // UNITS, img))
    cell = img.resize((CELL, CELL), Image.LANCZOS)
    # the characters' own finish: a ring a shade darker than the edge it borders
    return add_contour(cell) if contour else cell


MARGIN = 6          # texels of every cell's edge nothing may be drawn in (ItemSpritesTest)


def check(i, cell):
    """What the renderer counts on: an item's art is centred on its cell's middle, which is what it
    is turned and swollen about and what floats over the item's shadow, and stays clear of the
    cell's edge, where the texture's halvings would mix it with its neighbour's."""
    box = cell.getchannel("A").point(lambda v: 255 if v > 16 else 0).getbbox()
    assert box is not None, "cell %d is empty" % i
    mx, my = (box[0] + box[2]) / 2.0, (box[1] + box[3]) / 2.0
    assert abs(mx - CELL / 2.0) <= 2.0 and abs(my - CELL / 2.0) <= 2.0, \
        "cell %d's art is centred on (%.1f, %.1f), not its cell's middle" % (i, mx, my)
    assert box[0] >= MARGIN and box[1] >= MARGIN and box[2] <= CELL - MARGIN and box[3] <= CELL - MARGIN, \
        "cell %d's art %r runs into its cell's margin" % (i, box)


def build_atlas():
    atlas = Image.new("RGBA", (COLS * CELL, ROWS * CELL), CLEAR)
    for (i, fn) in cells():
        cell = render_cell(fn, contour=(i != TWINKLE))
        check(i, cell)
        atlas.paste(cell, ((i % COLS) * CELL, (i // COLS) * CELL))
    return atlas


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--out", help="directory to write items.png to (default: sprites/)")
    args = ap.parse_args()
    out = args.out or os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "sprites")
    os.makedirs(out, exist_ok=True)
    atlas = build_atlas()
    p = os.path.join(out, "items.png")
    atlas.save(p, optimize=True)
    print(f"Generated {p} ({atlas.width}x{atlas.height}, {len(cells())} cells)")


if __name__ == "__main__":
    main()
