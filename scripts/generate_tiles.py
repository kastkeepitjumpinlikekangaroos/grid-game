#!/usr/bin/env python3
"""Generate sprites/tiles.png -- the isometric tileset, drawn in MapleStory's style.

    python3 scripts/generate_tiles.py        # needs Pillow
    python3 scripts/tile_gallery.py /tmp/tg  # then look at it (see that script)

Layout: one 80x112 cell per tile id (2x the 40x56 display cell) across, four
rows down. The diamond a tile stands on is centred at (40, 92), 80 wide and 40
tall; a block of height h has its top diamond h pixels higher and two side
faces below it.

What the four rows are depends on the tile's form (TileForm in Tile.scala,
mirrored in TILES below):

- GROUND  walkable floor. Four *variants*; the renderer picks one by position,
          so a field of grass isn't one tuft stamped over and over.
- POOL    water, lava: level with the ground, can't be walked on. Four
          *animation frames*, cycled with a per-cell offset.
- BLOCK   walls, cliffs, the void: a solid box that fills its cell. Four
          animation frames (identical when it doesn't move).
- PROP    trees, rocks, bushes: something standing on the ground, drawn on a
          transparent cell. The renderer lays the ground it stands in under it
          (Tile.groundUnder), so one tree serves a meadow and a beach alike.
          Four *variants*, picked by position.

Style -- the same rules as the characters (sprite_base.py), because that is
what makes the world look like it belongs to them:

- Flat cel shading. Every surface is one flat colour with one hard-edged
  shadow tone on the side away from the light (upper left): the left face of a
  block is lit, the right face is in shade. No gradients, no noise.
- Outlines in a dark version of the part's own hue (`ink`), never black.
- Bright, saturated, friendly: round trees, fat mushrooms, a grass lip hanging
  over brown soil on a cliff.

Three rules specific to terrain, all learned from how the renderer tiles it:

- Ground and pool tiles are seamless. They are cut to a hard-edged diamond
  (the same mask the old tileset used, so neighbours meet exactly) and nothing
  on them is outlined or shaded toward an edge: any mark that touches the edge
  of one tile shows up as a grid across a whole field of them. Details sit in
  the middle.
- A block only ever inks its bottom edge. Its top diamond meets its
  neighbours' and its side faces are covered by the block in front of it, so a
  line anywhere else draws a seam between every pair of blocks in a wall.
  Anything on a side face only shows on the outside of a mass of blocks --
  which is exactly where a cliff's grass lip belongs.
- Walkable ground stays quiet. It covers most of the screen, and players and
  projectiles have to read on top of it: low-contrast details, far apart.
"""

import math
import os
import random
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sprite_base import (CLEAR, Ell, Limb, Poly, RRect, ScaledDraw, arc_pts, blob, cel,  # noqa: E402
                         ink, leaf, lit, mix, shade, sparkle, stroke, xform)

CELL_W, CELL_H = 80, 112
HW, HH = 40, 20
CX, BASE_Y = 40, 92
FRAMES = 4
SS = 4  # supersampling: every cell is drawn at 4x and resampled down

GROUND, POOL, BLOCK, PROP = "ground", "pool", "block", "prop"


# ---------------------------------------------------------------------------
# Palette
# ---------------------------------------------------------------------------

GRASS = (128, 206, 80)
FLOWER_PINK = (255, 150, 188)
FLOWER_YELLOW = (255, 220, 84)
FLOWER_WHITE = (252, 252, 255)
FLOWER_BLUE = (138, 188, 255)
FLOWER_LILAC = (204, 162, 255)
FLOWER_ORANGE = (255, 172, 84)
PATH = (230, 198, 140)
DIRT = (192, 142, 94)
SOIL = (182, 124, 76)
SAND = (250, 231, 176)
SNOW = (242, 247, 255)
ICE = (192, 234, 252)
STONE = (214, 208, 196)
COBBLE = (182, 180, 192)
PLANK = (216, 164, 102)
MOSS = (104, 172, 80)
MARSH = (130, 150, 88)
ASH = (140, 134, 134)
GRAVEL = (206, 196, 178)
METAL = (178, 192, 208)
GLASS = (170, 228, 244)
CIRCUIT = (36, 114, 110)
WATER = (74, 186, 240)
DEEP = (46, 128, 214)
LAVA = (255, 128, 40)
BARK = (150, 98, 58)
LEAF = (100, 196, 76)
PINE = (64, 150, 106)
ROCK = (172, 166, 158)
WOOD = (214, 160, 98)
SHADOW = (24, 52, 44)


# ---------------------------------------------------------------------------
# Geometry
# ---------------------------------------------------------------------------

def iso(u, v, h=0.0):
    """A point on a tile's own square (u along world x, v along world y, both
    0..1) at height h, in cell pixels."""
    return (CX + (u - v) * HW, BASE_Y - h + (u + v - 1.0) * HH)


def top(h=0.0, grow=0.0):
    """The diamond at height h, grown outward by `grow` pixels."""
    return Poly([(CX, BASE_Y - h - HH - grow), (CX + HW + 2 * grow, BASE_Y - h),
                 (CX, BASE_Y - h + HH + grow), (CX - HW - 2 * grow, BASE_Y - h)])


def left_face(h, grow=0.6):
    return Poly([(-grow, BASE_Y - h), (CX, BASE_Y + HH - h), (CX + grow, BASE_Y + HH + grow),
                 (-grow, BASE_Y + grow)])


def right_face(h, grow=0.6):
    return Poly([(CX - grow, BASE_Y + HH - h), (CELL_W + grow, BASE_Y - h),
                 (CELL_W + grow, BASE_Y + grow), (CX - grow, BASE_Y + HH + grow)])


def lface(s, t, h):
    """A point on a block's left face: s from its left edge to the front corner,
    t from the top of the face (0) to the ground (1)."""
    return (s * CX, BASE_Y - h + s * HH + t * h)


def rface(s, t, h):
    """A point on a block's right face: s from the front corner to its right edge."""
    return (CX + s * HW, BASE_Y + HH - h - s * HH + t * h)


def face_quad(fn, s0, s1, t0, t1, h):
    return Poly([fn(s0, t0, h), fn(s1, t0, h), fn(s1, t1, h), fn(s0, t1, h)])


class PropDraw(ScaledDraw):
    """A ScaledDraw that also scales everything about a prop's foot (the middle of its
    diamond) by k, so a prop can be drawn bigger without restating it. Line widths are
    left alone, so every prop is inked at the same weight whatever its size."""

    def __init__(self, draw, scale, image, k):
        ScaledDraw.__init__(self, draw, scale, image)
        self._k = k

    def _m(self, x, y):
        return (CX + (x - CX) * self._k, BASE_Y + (y - BASE_Y) * self._k)

    def _pts(self, xy):
        s = self._s
        if len(xy) and isinstance(xy[0], (tuple, list)):
            return [tuple(v * s for v in self._m(x, y)) for (x, y) in xy]
        out = []
        for i in range(0, len(xy), 2):
            x, y = self._m(xy[i], xy[i + 1])
            out += [x * s, y * s]
        return out

    def _box(self, xy):
        if len(xy) and isinstance(xy[0], (tuple, list)):
            (x0, y0), (x1, y1) = xy[0], xy[1]
        else:
            x0, y0, x1, y1 = xy
        (x0, y0), (x1, y1) = self._m(x0, y0), self._m(x1, y1)
        return ScaledDraw._box(self, (x0, y0, x1, y1))

    def sub(self):
        img = Image.new("RGBA", self._img.size, CLEAR)
        return PropDraw(ImageDraw.Draw(img), self._s, img, self._k)


def classify(px, py, h):
    """Whether pixel (px, py) belongs to a tile of height h: its top diamond, or
    one of its two side faces. The same test as the tileset has always used, so
    a field of tiles still meets without a gap or a doubled line."""
    top_cy = BASE_Y - h
    if abs(px - CX) / HW + abs(py - top_cy) / HH <= 1.0:
        return True
    if h <= 0:
        return False
    if px <= CX:
        yt = BASE_Y - h + px * 0.5
        yb = BASE_Y + px * 0.5
        if yt <= py <= yb:
            return True
    if px >= CX:
        rpx = px - CX
        yt = BASE_Y + HH - h - rpx * 0.5
        yb = BASE_Y + HH - rpx * 0.5
        if yt <= py <= yb:
            return True
    return False


_masks = {}


def hard_mask(h):
    if h not in _masks:
        m = Image.new("L", (CELL_W, CELL_H), 0)
        px = m.load()
        for y in range(CELL_H):
            for x in range(CELL_W):
                if classify(x, y, h):
                    px[x, y] = 255
        _masks[h] = m
    return _masks[h]


def scatter(r, n, lo=0.2, hi=0.8, gap=0.24, tries=400):
    """Up to n points on a tile's square, kept `gap` apart and off its edges."""
    pts = []
    for _ in range(tries):
        if len(pts) >= n:
            break
        u, v = r.uniform(lo, hi), r.uniform(lo, hi)
        if all((u - a) ** 2 + (v - b) ** 2 >= gap * gap for a, b in pts):
            pts.append((u, v))
    return pts


# ---------------------------------------------------------------------------
# Small motifs
# ---------------------------------------------------------------------------

def shadow(d, x, y, rx, ry, a=78):
    """A prop's contact shadow: one hard-edged ellipse, translucent, laid on
    whatever ground the renderer puts under it."""
    Ell(x, y, rx, ry).draw(d, fill=SHADOW + (a,))


def tuft(d, x, y, col, k=1.0):
    """Three blades of grass standing up out of the ground."""
    Poly([(x - 3.2 * k, y), (x - 2.5 * k, y - 3.0 * k), (x - 1.2 * k, y - 0.9 * k),
          (x - 0.2 * k, y - 4.4 * k), (x + 0.9 * k, y - 0.9 * k), (x + 2.4 * k, y - 3.4 * k),
          (x + 3.2 * k, y)]).draw(d, fill=col)


def flower(d, x, y, col, k=1.0, center=(255, 206, 80)):
    """A five-petal flower seen from above and a little to the side."""
    for i in range(5):
        a = math.radians(-90 + i * 72)
        Ell(x + math.cos(a) * 1.7 * k, y + math.sin(a) * 1.25 * k, 1.35 * k, 1.1 * k).draw(
            d, fill=shade(col, 0.5) if i in (1, 2) else col)
    Ell(x, y, 0.9 * k, 0.75 * k).draw(d, fill=center)


def pebble(d, x, y, rx, ry, col, line=True):
    cel(d, Ell(x, y, rx, ry), col, sh=(rx * 0.3, ry * 0.45), line=line, lw=0.7)


def twinkle(d, x, y, rad, col=(255, 255, 255)):
    if rad > 0.3:
        sparkle(d, x, y, rad, col)


def wave(d, x, y, w, col, lw=1.2, amp=0.9):
    """A short ~ stroke lying on the surface of water."""
    pts = [(x - w * 0.5 + w * i / 8.0, y + math.sin(i / 8.0 * math.pi * 2) * amp) for i in range(9)]
    stroke(d, pts, lw, col)


# ---------------------------------------------------------------------------
# Walkable ground: flat diamond, quiet details in the middle
# ---------------------------------------------------------------------------

def ground_fill(d, col):
    top(0, grow=3.0).draw(d, fill=col)


def _grass(d, f, r, base=GRASS):
    ground_fill(d, base)
    dark = shade(base, 0.55)
    light = lit(base, 0.6)
    n = (2, 2, 3, 2)[f]
    for (u, v) in scatter(r, n, gap=0.34):
        x, y = iso(u, v)
        tuft(d, x, y, dark, r.uniform(0.9, 1.15))
    if f in (0, 2):
        x, y = iso(*scatter(r, 1)[0])
        Ell(x, y, 1.6, 0.9).draw(d, fill=light)
    if f == 1:
        x, y = iso(*r.choice(((0.62, 0.36), (0.38, 0.6))))
        flower(d, x, y, FLOWER_WHITE, 0.75, center=(255, 232, 140))


def _grass_tile(d, f, r):
    _grass(d, f, r)


def _flowers(d, f, r):
    ground_fill(d, GRASS)
    dark = shade(GRASS, 0.55)
    palettes = ((FLOWER_PINK, FLOWER_YELLOW, FLOWER_WHITE), (FLOWER_YELLOW, FLOWER_ORANGE, FLOWER_WHITE),
                (FLOWER_BLUE, FLOWER_WHITE, FLOWER_LILAC), (FLOWER_PINK, FLOWER_LILAC, FLOWER_YELLOW))[f]
    for (u, v) in scatter(r, 2, gap=0.3):
        x, y = iso(u, v)
        tuft(d, x, y, dark, 0.9)
    pts = scatter(r, 3, lo=0.22, hi=0.78, gap=0.3)
    for i, (u, v) in enumerate(pts):
        x, y = iso(u, v)
        # a pair of leaves under each flower
        Ell(x - 2.4, y + 1.6, 2.4, 1.1).draw(d, fill=shade(GRASS, 0.9))
        Ell(x + 2.4, y + 1.6, 2.4, 1.1).draw(d, fill=shade(GRASS, 0.9))
        flower(d, x, y - 0.8, palettes[i % 3], r.uniform(1.3, 1.5))


def _path(d, f, r):
    ground_fill(d, PATH)
    for (u, v) in scatter(r, (3, 2, 3, 4)[f], gap=0.26):
        x, y = iso(u, v)
        pebble(d, x, y, r.uniform(1.4, 2.3), r.uniform(0.9, 1.4), mix(PATH, (186, 150, 108), 0.6))
    for (u, v) in scatter(r, 3, gap=0.2):
        x, y = iso(u, v)
        Ell(x, y, 1.1, 0.6).draw(d, fill=shade(PATH, 0.45))
    if f == 3:
        x, y = iso(0.45, 0.5)
        stroke(d, [(x - 5, y + 1), (x - 1, y - 0.5), (x + 4, y + 0.6)], 0.9, shade(PATH, 0.6))


def _dirt(d, f, r):
    ground_fill(d, DIRT)
    for (u, v) in scatter(r, (3, 4, 2, 3)[f], gap=0.26):
        x, y = iso(u, v)
        pebble(d, x, y, r.uniform(1.5, 2.5), r.uniform(1.0, 1.5), mix(DIRT, (150, 140, 132), 0.55))
    for (u, v) in scatter(r, 4, gap=0.18):
        x, y = iso(u, v)
        Ell(x, y, 1.0, 0.55).draw(d, fill=shade(DIRT, 0.5))
    if f == 2:
        x, y = iso(0.5, 0.45)
        stroke(d, [(x - 6, y), (x - 2, y - 1.2), (x + 1, y - 0.2), (x + 5, y - 1.4)], 1.0, shade(DIRT, 0.9))


def _sand(d, f, r):
    ground_fill(d, SAND)
    light = lit(SAND, 0.9)
    for (u, v) in scatter(r, 2, gap=0.34):
        x, y = iso(u, v)
        pts = arc_pts(x, y + 3.5, 6.0, 3.0, 205, 335, 8)
        stroke(d, pts, 0.9, light)
    for (u, v) in scatter(r, 4, gap=0.16):
        x, y = iso(u, v)
        Ell(x, y, 0.8, 0.5).draw(d, fill=shade(SAND, 0.45))
    if f == 1:
        x, y = iso(0.58, 0.42)
        _shell(d, x, y)
    elif f == 3:
        x, y = iso(0.4, 0.58)
        _starfish(d, x, y)


def _shell(d, x, y):
    col = (255, 196, 186)
    fan = Poly(arc_pts(x, y, 3.2, 2.4, 180, 360, 10) + [(x + 1.0, y + 1.4), (x - 1.0, y + 1.4)])
    cel(d, fan, col, sh=(0.5, 0.5), lw=0.6)
    for a in (220, 270, 320):
        stroke(d, [(x, y + 0.8), (x + math.cos(math.radians(a)) * 2.4, y + math.sin(math.radians(a)) * 1.8)],
               0.35, shade(col, 0.9))


def _starfish(d, x, y):
    col = (255, 150, 110)
    pts = []
    for i in range(10):
        a = math.radians(-90 + i * 36)
        rr = 3.2 if i % 2 == 0 else 1.3
        pts.append((x + math.cos(a) * rr, y + math.sin(a) * rr * 0.72))
    cel(d, Poly(pts), col, sh=(0.4, 0.4), lw=0.6)
    Ell(x, y, 0.6, 0.45).draw(d, fill=lit(col, 1.0))


def _snow(d, f, r):
    ground_fill(d, SNOW)
    blue = (214, 228, 250)
    for (u, v) in scatter(r, (2, 3, 2, 2)[f], gap=0.32):
        x, y = iso(u, v)
        # a soft drift: a crescent of shade under a little mound
        Poly(arc_pts(x, y, 6.5, 2.6, 0, 180, 10) + arc_pts(x, y + 0.2, 6.5, 1.4, 180, 0, 10)).draw(d, fill=blue)
    for (u, v) in scatter(r, 3, gap=0.2):
        x, y = iso(u, v)
        Ell(x, y, 0.9, 0.55).draw(d, fill=(222, 234, 252))
    if f in (1, 3):
        x, y = iso(*scatter(r, 1)[0])
        twinkle(d, x, y - 1, 2.2, (255, 255, 255))


def _ice(d, f, r):
    ground_fill(d, ICE)
    shine = (238, 250, 255)
    x, y = iso(0.35 + 0.1 * (f % 2), 0.4)
    # two parallel streaks of shine, lying along the ice
    for off in (0.0, 3.4):
        stroke(d, [(x - 6 + off, y + 3 - off * 0.2), (x + 5 + off, y - 2.5 - off * 0.2)], 1.3 - off * 0.15, shine)
    crack = shade(ICE, 0.8)
    if f in (1, 2):
        cx, cy = iso(0.62, 0.62)
        stroke(d, [(cx - 5, cy - 1), (cx - 1, cy + 0.5), (cx + 2, cy - 1.5), (cx + 6, cy)], 0.55, crack)
        stroke(d, [(cx - 1, cy + 0.5), (cx, cy + 3)], 0.5, crack)
    for (u, v) in scatter(r, 2, gap=0.3):
        x, y = iso(u, v)
        Ell(x, y, 0.9, 0.7).draw(d, fill=shine)


def _stone(d, f, r):
    """Flagstones: four slabs to a tile, the joints running the tile's own axes."""
    mortar = shade(STONE, 1.0)
    ground_fill(d, mortar)
    tones = [STONE, mix(STONE, (226, 216, 200), 0.6), mix(STONE, (196, 196, 204), 0.6)]
    g = 0.035
    cuts = ((0.0, 0.5, 1.0), (0.0, 0.45, 1.0), (0.0, 0.55, 1.0), (0.0, 0.5, 1.0))[f]
    k = 0
    for i in range(2):
        for j in range(2):
            u0, u1 = cuts[i], cuts[i + 1]
            v0, v1 = (0.0, 0.5, 1.0)[j], (0.0, 0.5, 1.0)[j + 1]
            slab = Poly([iso(u0 + g, v0 + g), iso(u1 - g, v0 + g), iso(u1 - g, v1 - g), iso(u0 + g, v1 - g)])
            col = tones[(k + f) % 3]
            slab.draw(d, fill=col)
            # lit rim along the slab's far edges
            stroke(d, [iso(u0 + g + 0.02, v1 - g - 0.02), iso(u0 + g + 0.02, v0 + g + 0.02),
                       iso(u1 - g - 0.02, v0 + g + 0.02)], 0.7, lit(col, 0.5))
            k += 1


def _cobble(d, f, r):
    """Round cobbles set in mortar."""
    mortar = shade(COBBLE, 1.3)
    ground_fill(d, mortar)
    rows = 3
    for j in range(rows):
        for i in range(3):
            u = (i + 0.5 + (0.5 if j % 2 else 0.0)) / 3.0
            v = (j + 0.5) / rows
            if u > 1.0:
                u -= 1.0
            u = min(max(u, 0.17), 0.83)
            x, y = iso(u, v)
            col = mix(COBBLE, (206, 196, 190) if (i + j + f) % 3 == 0 else (168, 172, 190), 0.5)
            cel(d, Ell(x, y, 6.2, 3.0), col, sh=(1.2, 1.1), line=False)
            Ell(x - 1.6, y - 0.9, 2.2, 0.8).draw(d, fill=lit(col, 0.7))


def _planks(d, f, r):
    """A boardwalk: four boards to a tile, running along world x, nailed at the ends."""
    gap = shade(PLANK, 1.6)
    ground_fill(d, gap)
    tones = (PLANK, mix(PLANK, (228, 178, 116), 0.7), mix(PLANK, (196, 142, 88), 0.7))
    for j in range(4):
        v0, v1 = j / 4.0 + 0.02, (j + 1) / 4.0 - 0.02
        split = (None, 0.55, None, 0.4, 0.62, None, 0.3, None)[(j + f * 2) % 8]
        spans = [(0.0, 1.0)] if split is None else [(0.0, split - 0.01), (split + 0.01, 1.0)]
        for n, (u0, u1) in enumerate(spans):
            col = tones[(j + n + f) % 3]
            Poly([iso(u0, v0), iso(u1, v0), iso(u1, v1), iso(u0, v1)]).draw(d, fill=col)
            stroke(d, [iso(u0, v0 + 0.03), iso(u1, v0 + 0.03)], 0.6, lit(col, 0.6))
            # grain
            gx0 = u0 + (u1 - u0) * 0.25
            stroke(d, [iso(gx0, (v0 + v1) / 2 + 0.02), iso(gx0 + (u1 - u0) * 0.35, (v0 + v1) / 2 + 0.02)],
                   0.4, shade(col, 0.5))
            for uu in (u0 + 0.06, u1 - 0.06):
                if 0.03 < uu < 0.97:
                    x, y = iso(uu, (v0 + v1) / 2)
                    Ell(x, y, 0.7, 0.5).draw(d, fill=shade(col, 1.8))


def _moss(d, f, r):
    ground_fill(d, MOSS)
    for (u, v) in scatter(r, (3, 2, 3, 3)[f], gap=0.28):
        x, y = iso(u, v)
        Poly(arc_pts(x, y, 4.5, 2.3, 180, 360, 10)).draw(d, fill=lit(MOSS, 0.5))
        Ell(x, y + 0.4, 4.5, 1.0).draw(d, fill=shade(MOSS, 0.5))
    for (u, v) in scatter(r, 2, gap=0.3):
        x, y = iso(u, v)
        tuft(d, x, y, shade(MOSS, 0.6), 0.8)


def _marsh(d, f, r):
    ground_fill(d, MARSH)
    for (u, v) in scatter(r, (1, 2, 1, 2)[f], gap=0.4, lo=0.3, hi=0.7):
        x, y = iso(u, v)
        Ell(x, y, 6.0, 2.6).draw(d, fill=(116, 158, 150))
        Ell(x - 1.5, y - 0.7, 2.5, 0.8).draw(d, fill=(170, 206, 196))
    for (u, v) in scatter(r, 3, gap=0.25):
        x, y = iso(u, v)
        tuft(d, x, y, shade(MARSH, 0.7), 1.1)


def _ash(d, f, r):
    ground_fill(d, ASH)
    for (u, v) in scatter(r, 4, gap=0.2):
        x, y = iso(u, v)
        Ell(x, y, 1.3, 0.7).draw(d, fill=shade(ASH, 0.5))
    for (u, v) in scatter(r, (1, 2, 1, 0)[f], gap=0.3):
        x, y = iso(u, v)
        Ell(x, y, 1.0, 0.7).draw(d, fill=(255, 150, 70))
        Ell(x, y, 0.45, 0.35).draw(d, fill=(255, 226, 140))


def _gravel(d, f, r):
    ground_fill(d, GRAVEL)
    tones = ((180, 170, 156), (224, 216, 200), (168, 164, 160))
    for i, (u, v) in enumerate(scatter(r, 8, gap=0.14, lo=0.14, hi=0.86)):
        x, y = iso(u, v)
        pebble(d, x, y, r.uniform(1.1, 1.8), r.uniform(0.7, 1.1), tones[(i + f) % 3], line=False)


def _metal(d, f, r):
    seam = shade(METAL, 1.2)
    ground_fill(d, seam)
    g = 0.03
    plate = Poly([iso(g, g), iso(1 - g, g), iso(1 - g, 1 - g), iso(g, 1 - g)])
    plate.draw(d, fill=METAL)
    stroke(d, [iso(g + 0.03, 1 - g - 0.03), iso(g + 0.03, g + 0.03), iso(1 - g - 0.03, g + 0.03)], 0.8, lit(METAL, 0.8))
    for (u, v) in ((0.12, 0.12), (0.88, 0.12), (0.88, 0.88), (0.12, 0.88)):
        x, y = iso(u, v)
        Ell(x, y, 1.2, 0.8).draw(d, fill=shade(METAL, 0.9))
        Ell(x - 0.3, y - 0.25, 0.5, 0.3).draw(d, fill=lit(METAL, 1.0))
    if f in (1, 3):
        stroke(d, [iso(0.3, 0.5), iso(0.7, 0.5)], 0.6, shade(METAL, 0.5))


def _glass(d, f, r):
    ground_fill(d, shade(GLASS, 0.6))
    g = 0.035
    Poly([iso(g, g), iso(1 - g, g), iso(1 - g, 1 - g), iso(g, 1 - g)]).draw(d, fill=GLASS)
    shine = (240, 252, 255)
    x, y = iso(0.3 + 0.08 * f, 0.3)
    stroke(d, [(x - 4, y + 3), (x + 4, y - 1)], 1.3, shine)
    stroke(d, [(x - 1, y + 5), (x + 5, y + 2)], 0.8, shine)


def _circuit(d, f, r):
    """The space maps' floor: a teal panel with bright traces and gold pads."""
    ground_fill(d, CIRCUIT)
    trace = (70, 200, 170)
    pad = (240, 202, 96)
    g = 0.04
    stroke(d, [iso(g, g), iso(1 - g, g)], 0.5, lit(CIRCUIT, 0.4))
    routes = (
        [(0.2, 0.5), (0.45, 0.5), (0.55, 0.35), (0.8, 0.35)],
        [(0.5, 0.18), (0.5, 0.42), (0.68, 0.6), (0.68, 0.82)],
        [(0.2, 0.3), (0.38, 0.3), (0.38, 0.7), (0.8, 0.7)],
        [(0.3, 0.2), (0.3, 0.46), (0.7, 0.46), (0.7, 0.8)],
    )
    if f in (0, 2):
        pts = [iso(u, v) for (u, v) in routes[f]]
        stroke(d, pts, 0.9, mix(trace, CIRCUIT, 0.35))
        for (x, y) in (pts[0], pts[-1]):
            Ell(x, y, 1.6, 1.0).draw(d, fill=pad)
            Ell(x, y, 0.7, 0.45).draw(d, fill=shade(pad, 1.4))
    else:
        x, y = iso(0.5, 0.5)
        Poly([(x - 3, y), (x, y - 1.5), (x + 3, y), (x, y + 1.5)]).draw(d, fill=(28, 70, 72))
        Ell(x + 7, y + 2, 1.1, 0.7).draw(d, fill=pad)


# ---------------------------------------------------------------------------
# Pools: level with the ground, animated
# ---------------------------------------------------------------------------

def _water_pool(d, f, r, base=WATER, crest=(172, 228, 255), n=3):
    ground_fill(d, base)
    for i, (u, v) in enumerate(scatter(r, n, lo=0.25, hi=0.75, gap=0.3)):
        drift = ((f + i) % 4) * 0.05 - 0.075
        x, y = iso(u + drift, v - drift)
        wave(d, x, y, 9.0, crest, 1.1, 0.8)
    x, y = iso(0.62, 0.38)
    twinkle(d, x, y, (0.0, 2.0, 3.0, 1.4)[f])


def _water(d, f, r):
    _water_pool(d, f, r)


def _deep_water(d, f, r):
    _water_pool(d, f, r, base=DEEP, crest=(110, 180, 245), n=2)


def _lava(d, f, r):
    ground_fill(d, LAVA)
    hot = (255, 214, 92)
    crust = (190, 72, 40)
    # crust plates floating on the melt
    for (u, v, k) in ((0.3, 0.35, 1.0), (0.66, 0.62, 0.8)):
        x, y = iso(u, v)
        plate = Poly([(x - 6 * k, y), (x - 2 * k, y - 2.6 * k), (x + 4 * k, y - 2.2 * k),
                      (x + 7 * k, y + 0.4 * k), (x + 2 * k, y + 2.8 * k), (x - 4 * k, y + 2.2 * k)])
        cel(d, plate, crust, sh=(1.0, 1.0), lw=0.8)
    x, y = iso(0.62, 0.3)
    Ell(x, y, 4.0, 1.8).draw(d, fill=hot)
    # a bubble swelling and popping
    bx, by = iso(0.36, 0.66)
    if f < 3:
        rad = (1.4, 2.2, 3.0)[f]
        cel(d, Ell(bx, by - rad * 0.3, rad, rad * 0.75), (255, 170, 60), sh=(0.5, 0.5), line=False)
        Ell(bx - rad * 0.3, by - rad * 0.6, rad * 0.35, rad * 0.25).draw(d, fill=hot)
    else:
        for a in range(0, 360, 60):
            x2 = bx + math.cos(math.radians(a)) * 3.4
            y2 = by + math.sin(math.radians(a)) * 1.6
            Ell(x2, y2, 0.8, 0.6).draw(d, fill=hot)


# ---------------------------------------------------------------------------
# Blocks: a box filling its cell; ink only on the bottom edge
# ---------------------------------------------------------------------------

def box(d, h, top_col, left_col, right_col):
    left_face(h).draw(d, fill=left_col)
    right_face(h).draw(d, fill=right_col)
    top(h, grow=0.6).draw(d, fill=top_col)


def base_line(d, col, lw=1.3):
    stroke(d, [(0, BASE_Y - 0.4), (CX, BASE_Y + HH - 0.4), (CELL_W, BASE_Y - 0.4)], lw, col)


def _wall(d, f, r):
    h = 50
    stone = (206, 188, 156)
    box(d, h, stone, shade(stone, 0.9), shade(stone, 1.9))
    # the top: a walkway of worn slabs
    for (u0, u1) in ((0.0, 0.5), (0.5, 1.0)):
        Poly([iso(u0 + 0.05, 0.06, h), iso(u1 - 0.05, 0.06, h), iso(u1 - 0.05, 0.94, h),
              iso(u0 + 0.05, 0.94, h)]).draw(d, fill=lit(stone, 0.35))
    # rounded bricks on both faces, staggered, continuing across neighbours
    for fn, tone in ((lface, 0.9), (rface, 1.9)):
        face = shade(stone, tone)
        mortar = shade(face, 1.4)
        rows = 5
        for j in range(rows):
            t0, t1 = j / rows + 0.03, (j + 1) / rows - 0.03
            off = 0.0 if j % 2 == 0 else 1.0 / 6
            edges = [0.0] + [((k / 3.0) + off) for k in range(4) if 0.0 < (k / 3.0) + off < 1.0] + [1.0]
            for k in range(len(edges) - 1):
                s0, s1 = edges[k], edges[k + 1]
                g0 = 0.0 if s0 == 0.0 else 0.025
                g1 = 0.0 if s1 == 1.0 else 0.025
                col = mix(face, lit(face, 0.6) if (j + k) % 3 == 0 else shade(face, 0.3), 0.5)
                face_quad(fn, s0 + g0, s1 - g1, t0, t1, h).draw(d, fill=col)
                stroke(d, [fn(s0 + g0, t0 + 0.02, h), fn(s1 - g1, t0 + 0.02, h)], 0.7, lit(col, 0.5))
            stroke(d, [fn(0, t1 + 0.03, h), fn(1, t1 + 0.03, h)], 1.0, mortar)
    base_line(d, ink(shade(stone, 1.9)))


def _grass_lip(d, fn, h, depth, col):
    """The grass on a cliff hanging over its face: a band with a scalloped
    bottom edge, inked along the scallops. Drawn per face; the scallops repeat
    exactly on every block, so a cliff's edge runs unbroken along a row."""
    n = 4
    pts = [fn(0, 0, h), fn(1, 0, h)]
    bottom = []
    for i in range(n * 4 + 1):
        s = i / float(n * 4)
        bump = abs(math.sin(s * n * math.pi))
        t = (depth * (0.55 + 0.45 * bump)) / h
        bottom.append(fn(s, t, h))
    lip = Poly(pts + bottom[::-1])
    lip.draw(d, fill=col)
    stroke(d, bottom, 0.9, ink(col))


def _cliff(d, f, r):
    """MapleStory's platform: grass on top, a lip of it hanging over brown soil
    set with round stones."""
    h = 72
    box(d, h, GRASS, shade(SOIL, 0.2), shade(SOIL, 1.3))
    # grass on top, same tufts as the ground
    for (u, v) in ((0.3, 0.35), (0.65, 0.6), (0.62, 0.25)):
        x, y = iso(u, v, h)
        tuft(d, x, y, shade(GRASS, 0.55), 1.0)
    for fn, tone in ((lface, 0.2), (rface, 1.3)):
        soil = shade(SOIL, tone)
        # strata
        for t in (0.55, 0.8):
            stroke(d, [fn(0, t, h), fn(0.5, t + 0.03, h), fn(1, t, h)], 1.2, shade(soil, 0.35))
        # round stones in the soil
        for (s, t, k) in ((0.25, 0.42, 1.0), (0.7, 0.66, 0.8), (0.45, 0.88, 0.7)):
            x, y = fn(s, t, h)
            pebble(d, x, y, 3.6 * k, 2.8 * k, mix(soil, (176, 168, 156), 0.65))
        _grass_lip(d, fn, h, 11.0, shade(GRASS, 0.25 if fn is lface else 1.0))
    base_line(d, ink(shade(SOIL, 1.3)))


def _mountain(d, f, r):
    """A rocky crag capped with snow."""
    h = 68
    rock = (160, 150, 140)
    box(d, h, SNOW, shade(rock, 0.3), shade(rock, 1.5))
    for fn, tone in ((lface, 0.3), (rface, 1.5)):
        face = shade(rock, tone)
        for (s0, t0, s1, t1) in ((0.1, 0.45, 0.55, 0.52), (0.4, 0.7, 0.9, 0.62), (0.05, 0.85, 0.4, 0.9)):
            stroke(d, [fn(s0, t0, h), fn((s0 + s1) / 2, (t0 + t1) / 2 + 0.03, h), fn(s1, t1, h)], 1.1,
                   shade(face, 0.6))
        for (s, t) in ((0.7, 0.35), (0.25, 0.62)):
            x, y = fn(s, t, h)
            Poly([(x - 3, y + 1.5), (x, y - 2), (x + 3.5, y + 1.8)]).draw(d, fill=lit(face, 0.4))
        # snow spilling over the edge
        _grass_lip(d, fn, h, 9.0, (236, 244, 255) if fn is lface else (206, 222, 246))
    x, y = iso(0.45, 0.45, h)
    twinkle(d, x, y, 1.8)
    base_line(d, ink(shade(rock, 1.5)))


def _basalt(d, f, r):
    h = 58
    b = (86, 82, 108)
    box(d, h, lit(b, 0.3), b, shade(b, 1.2))
    # the tops of the columns
    for (u, v) in ((0.28, 0.28), (0.72, 0.28), (0.28, 0.72), (0.72, 0.72)):
        x, y = iso(u, v, h)
        hexa = Poly([(x + 7.5 * math.cos(math.radians(a)), y + 3.8 * math.sin(math.radians(a)))
                     for a in range(0, 360, 60)])
        hexa.draw(d, fill=lit(b, 0.55 if (u + v) == 1.0 else 0.35))
    for fn, tone in ((lface, 0.0), (rface, 1.2)):
        face = shade(b, tone) if tone else b
        for k in range(1, 4):
            s = k / 4.0
            stroke(d, [fn(s, 0.02, h), fn(s, 0.98, h)], 1.0, shade(face, 0.8))
        for k in range(4):
            s = (k + 0.5) / 4.0
            stroke(d, [fn(s - 0.08, 0.1, h), fn(s - 0.08, 0.5, h)], 0.8, lit(face, 0.3))
    base_line(d, ink(shade(b, 1.2)))


def _obsidian(d, f, r):
    """Volcanic glass: dark, with hard glassy reflections."""
    h = 50
    o = (66, 56, 104)
    box(d, h, (84, 72, 132), o, shade(o, 1.3))
    for (u0, v0, u1, v1) in ((0.2, 0.3, 0.55, 0.15), (0.45, 0.8, 0.8, 0.6)):
        stroke(d, [iso(u0, v0, h), iso(u1, v1, h)], 1.1, (128, 112, 186))
    for fn, tone in ((lface, 0.0), (rface, 1.3)):
        face = o if tone == 0.0 else shade(o, tone)
        glint = lit(face, 1.4)
        a, b = fn(0.18, 0.2, h), fn(0.32, 0.2, h)
        c, e = fn(0.12, 0.85, h), fn(0.02, 0.85, h)
        Poly([a, b, c, e]).draw(d, fill=glint)
        a, b = fn(0.62, 0.15, h), fn(0.68, 0.15, h)
        c, e = fn(0.58, 0.7, h), fn(0.54, 0.7, h)
        Poly([a, b, c, e]).draw(d, fill=glint)
    base_line(d, (24, 18, 44))


def _void(d, f, r):
    """The dark between the stars, as a block: flat indigo, stars that twinkle."""
    h = 32
    v = (54, 42, 112)
    box(d, h, v, shade(v, 0.8), shade(v, 1.6))
    rr = random.Random(1717)
    stars = [(rr.uniform(0.15, 0.85), rr.uniform(0.15, 0.85)) for _ in range(5)]
    for i, (u, w) in enumerate(stars):
        x, y = iso(u, w, h)
        phase = (f + i) % 4
        if i % 2 == 0:
            twinkle(d, x, y, (1.2, 2.2, 3.0, 2.0)[phase], (255, 250, 220) if i % 4 == 0 else (220, 226, 255))
        else:
            Ell(x, y, 0.9, 0.9).draw(d, fill=(200, 206, 255) if phase % 2 else (150, 150, 220))
    for fn, tone in ((lface, 0.8), (rface, 1.6)):
        for (s, t) in ((0.3, 0.4), (0.7, 0.7)):
            x, y = fn(s, t, h)
            Ell(x, y, 0.8, 0.8).draw(d, fill=(168, 170, 236) if (f % 2 == 0) == (s < 0.5) else (110, 104, 190))
    base_line(d, (22, 16, 50))


def _toxic(d, f, r):
    """A block of green slime: glossy, bubbling, dripping over its edges."""
    h = 16
    g = (146, 232, 72)
    box(d, h, g, shade(g, 0.6), shade(g, 1.4))
    # gloss
    x, y = iso(0.32, 0.36, h)
    Ell(x, y, 7.0, 2.4).draw(d, fill=lit(g, 1.2))
    Ell(x + 5, y + 1.2, 1.4, 0.8).draw(d, fill=lit(g, 1.2))
    # a bubble rising and popping
    bx, by = iso(0.62, 0.58, h)
    if f < 3:
        rad = (1.3, 2.0, 2.7)[f]
        Ell(bx, by, rad, rad * 0.8).draw(d, fill=None, outline=(236, 255, 200), width=0.7)
        Ell(bx - rad * 0.35, by - rad * 0.35, rad * 0.3, rad * 0.25).draw(d, fill=(255, 255, 255))
    else:
        for a in range(0, 360, 72):
            Ell(bx + math.cos(math.radians(a)) * 3.2, by + math.sin(math.radians(a)) * 1.5, 0.6, 0.5).draw(
                d, fill=(236, 255, 200))
    # drips over the edges
    for fn, tone in ((lface, 0.6), (rface, 1.4)):
        col = shade(g, tone - 0.35)
        for (s, k) in ((0.25, 0.55), (0.62, 0.8)):
            x0, y0 = fn(s - 0.07, 0.0, h)
            x1, y1 = fn(s + 0.07, 0.0, h)
            xm, ym = fn(s, k, h)
            Poly([(x0, y0), (x1, y1)] + arc_pts(xm, ym - 1.2, 2.6, 2.2, 0, 180, 8)).draw(d, fill=col)
    base_line(d, ink(shade(g, 1.4)))


def _plasma(d, f, r):
    h = 24
    p = (255, 104, 182)
    box(d, h, p, shade(p, 0.7), shade(p, 1.5))
    for i in range(2):
        a0 = f * 90 + i * 180
        x, y = iso(0.5, 0.5, h)
        pts = [(x + math.cos(math.radians(a0 + k * 18)) * (2 + k * 1.1),
                y + math.sin(math.radians(a0 + k * 18)) * (1 + k * 0.55)) for k in range(12)]
        stroke(d, pts, 1.2, (255, 214, 236))
    for fn, tone in ((lface, 0.7), (rface, 1.5)):
        x, y = fn(0.5, 0.5, h)
        wave(d, x, y, 12, lit(shade(p, tone), 0.6), 1.0, 1.4 if f % 2 else -1.4)
    base_line(d, ink(shade(p, 1.5)))


def _energy(d, f, r):
    h = 44
    e = (178, 116, 240)
    pulse = (0.0, 0.35, 0.6, 0.3)[f]
    box(d, h, lit(e, pulse * 0.6), mix(e, (230, 200, 255), pulse * 0.3), shade(e, 1.2))
    hexline = lit(e, 1.2 + pulse)
    for (u, v) in ((0.3, 0.3), (0.7, 0.3), (0.5, 0.62), (0.3, 0.8), (0.7, 0.8)):
        x, y = iso(u, v, h)
        hexa = [(x + 5.2 * math.cos(math.radians(a)), y + 2.6 * math.sin(math.radians(a))) for a in range(0, 361, 60)]
        stroke(d, hexa, 0.7, hexline)
    for fn in (lface, rface):
        for t in (0.3, 0.55, 0.8):
            stroke(d, [fn(0.05, t, h), fn(0.95, t, h)], 0.8, lit(e, 0.8 + pulse))
    base_line(d, ink(shade(e, 1.2)))


def _fence(d, f, r):
    """A wooden barricade: upright boards on both faces, a rail across them, and
    the board ends on top. It is what a Fence item puts down, on every map."""
    h = 30
    box(d, h, lit(WOOD, 0.3), WOOD, shade(WOOD, 1.2))
    # board ends on top, running along the tile
    for k in range(1, 4):
        stroke(d, [iso(k / 4.0, 0.04, h), iso(k / 4.0, 0.96, h)], 0.8, shade(WOOD, 0.8))
    for fn, tone in ((lface, 0.0), (rface, 1.2)):
        face = WOOD if tone == 0.0 else shade(WOOD, tone)
        for k in range(1, 4):
            stroke(d, [fn(k / 4.0, 0.05, h), fn(k / 4.0, 0.97, h)], 0.9, shade(face, 1.0))
        for k in range(4):
            stroke(d, [fn(k / 4.0 + 0.05, 0.12, h), fn(k / 4.0 + 0.05, 0.4, h)], 0.6, lit(face, 0.5))
        rail = shade(face, 0.5)
        face_quad(fn, 0.0, 1.0, 0.5, 0.68, h).draw(d, fill=rail)
        stroke(d, [fn(0, 0.5, h), fn(1, 0.5, h)], 0.6, lit(rail, 0.6))
        for k in range(4):
            x, y = fn(k / 4.0 + 0.125, 0.59, h)
            Ell(x, y, 0.9, 0.9).draw(d, fill=(96, 86, 92))
    base_line(d, ink(shade(WOOD, 1.2)))


def _ice_block(d, f, r):
    """A chunk of glacier ice with snow settled on top."""
    h = 34
    i_top, i_l, i_r = (214, 244, 255), (166, 220, 248), (126, 190, 236)
    box(d, h, i_top, i_l, i_r)
    # snow on top: a soft rounded patch that stays off the edges
    x, y = iso(0.5, 0.5, h)
    blob(d, [Ell(x - 4, y, 11, 5), Ell(x + 5, y + 1, 9, 4.5), Ell(x, y - 2, 8, 4)], SNOW, sh=(0.0, 1.2),
         tone=(222, 234, 252), line=False)
    for fn, col in ((lface, i_l), (rface, i_r)):
        shine = lit(col, 1.2)
        a, b = fn(0.15, 0.1, h), fn(0.3, 0.1, h)
        c, e = fn(0.18, 0.9, h), fn(0.06, 0.9, h)
        Poly([a, b, c, e]).draw(d, fill=shine)
        for (s, t, k) in ((0.6, 0.35, 1.0), (0.75, 0.7, 0.7), (0.45, 0.62, 0.55)):
            x, y = fn(s, t, h)
            Ell(x, y, 1.3 * k, 1.3 * k).draw(d, fill=None, outline=shine, width=0.5)
    base_line(d, ink(i_r))


# ---------------------------------------------------------------------------
# Props: standing on the ground, drawn on a transparent cell
# ---------------------------------------------------------------------------

def _canopy_marks(d, marks, col):
    for (x, y, w) in marks:
        stroke(d, arc_pts(x, y, w, w * 0.6, 20, 160, 6), 0.8, col)


def _tree(d, f, r):
    """A round Henesys tree: a short trunk under a big soft ball of leaves."""
    shadow(d, 40, 94, 22, 8)
    trunk = Poly([(34.5, 96), (36.5, 88), (37.5, 66), (42.5, 66), (43.5, 88), (45.5, 96), (40, 97.5)])
    cel(d, trunk, BARK, sh=(1.8, 0.0), lw=1.1)
    layouts = (
        [(40, 44, 26, 21), (24, 52, 13, 11), (56, 52, 13, 11), (30, 30, 15, 13), (50, 29, 16, 14), (40, 18, 12, 10)],
        [(40, 46, 25, 20), (22, 50, 12, 11), (58, 50, 12, 11), (32, 28, 16, 14), (50, 31, 14, 12), (42, 17, 11, 9)],
        [(40, 45, 26, 21), (25, 54, 12, 10), (55, 54, 13, 10), (28, 34, 14, 13), (52, 31, 16, 14), (38, 19, 13, 10)],
        [(40, 46, 24, 20), (24, 53, 13, 10), (57, 49, 12, 11), (31, 31, 15, 13), (49, 26, 15, 13), (40, 16, 10, 8)],
    )
    parts = [Ell(x, y, rx, ry) for (x, y, rx, ry) in layouts[f]]
    hi = [(Ell(29, 30, 10, 7), lit(LEAF, 0.7)), (Ell(40, 18, 6, 4), lit(LEAF, 0.7)),
          (Ell(21, 48, 5, 4), lit(LEAF, 0.5))]
    blob(d, parts, LEAF, sh=(4.0, 4.0), regions=hi, lw=1.3)
    _canopy_marks(d, [(47, 40, 5), (34, 48, 5), (55, 53, 4), (43, 26, 4)], shade(LEAF, 1.1))
    if f == 1:  # blossom
        for (x, y) in ((28, 38), (47, 28), (55, 45), (36, 55), (42, 40), (24, 51)):
            flower(d, x, y, (255, 206, 222), 0.85, center=(255, 236, 150))
    elif f == 2:  # apples
        for (x, y) in ((30, 46), (50, 38), (41, 58), (57, 55)):
            cel(d, Ell(x, y, 2.6, 2.6), (236, 64, 64), sh=(0.6, 0.6), lw=0.7)
            Ell(x - 0.8, y - 0.9, 0.8, 0.7).draw(d, fill=(255, 220, 220))


def _bush(d, f, r):
    """A round shrub, about knee high, some in flower."""
    shadow(d, 40, 94, 20, 7)
    layouts = (
        [(40, 82, 20, 12), (29, 80, 10, 9), (51, 80, 10, 9), (35, 72, 10, 9), (46, 71, 11, 9)],
        [(40, 83, 19, 11), (28, 81, 10, 8), (52, 82, 9, 8), (38, 72, 12, 10), (50, 75, 8, 7)],
        [(40, 82, 21, 12), (30, 78, 11, 9), (50, 79, 11, 10), (40, 70, 11, 9)],
        [(40, 83, 20, 11), (29, 81, 9, 8), (51, 80, 11, 9), (34, 73, 10, 8), (46, 72, 10, 9)],
    )
    col = mix(LEAF, (80, 176, 84), 0.5)
    parts = [Ell(x, y, rx, ry) for (x, y, rx, ry) in layouts[f]]
    blob(d, parts, col, sh=(3.0, 3.0), regions=[(Ell(33, 70, 7, 4), lit(col, 0.6))], lw=1.2)
    _canopy_marks(d, [(45, 79, 4), (33, 83, 4)], shade(col, 1.0))
    if f == 0:
        for (x, y) in ((31, 75), (45, 70), (52, 81), (38, 84)):
            flower(d, x, y, FLOWER_PINK, 0.8)
    elif f == 1:
        for (x, y) in ((30, 77), (43, 70), (50, 80)):
            flower(d, x, y, FLOWER_YELLOW, 0.8, center=(255, 150, 70))
    elif f == 2:
        for (x, y) in ((32, 76), (36, 83), (47, 73), (51, 82), (43, 79)):
            cel(d, Ell(x, y, 1.6, 1.6), (224, 60, 96), sh=(0.4, 0.4), lw=0.5)


def _boulder(cx, cy, rx, ry, seed):
    rr = random.Random(seed)
    pts = []
    for i in range(14):
        a = math.radians(180 + i * 360.0 / 14)
        k = rr.uniform(0.9, 1.05)
        y = cy + math.sin(a) * ry * k
        pts.append((cx + math.cos(a) * rx * k, min(y, cy + ry * 0.55)))
    return Poly(pts)


def _rock(d, f, r):
    """A few round boulders, grey and friendly."""
    shadow(d, 40, 94, 20, 7)
    sets = (
        [(40, 84, 15, 12, 1)],
        [(35, 86, 13, 10, 2), (51, 88, 8, 6, 3)],
        [(43, 84, 14, 11, 4), (28, 90, 7, 5, 5)],
        [(38, 85, 14, 11, 6), (53, 89, 6, 5, 7), (26, 91, 5, 4, 8)],
    )[f]
    for (x, y, rx, ry, seed) in sets:
        shape = _boulder(x, y, rx, ry, seed + 10 * f)
        cel(d, shape, ROCK, sh=(rx * 0.25, ry * 0.3), hi=(1.0, 1.0), hi_tone=lit(ROCK, 0.6), lw=1.2)
        if f == 1 and rx > 10:  # moss on top
            Poly(arc_pts(x, y - ry * 0.35, rx * 0.7, ry * 0.55, 180, 360, 10)).draw(d, fill=mix(MOSS, LEAF, 0.4))
        stroke(d, [(x - rx * 0.1, y - ry * 0.1), (x + rx * 0.25, y + ry * 0.1)], 0.6, shade(ROCK, 0.8))


def _mushroom(d, f, r):
    """A giant mushroom: a fat cream stem under a round spotted cap."""
    caps = ((255, 146, 52), (236, 72, 72), (96, 160, 255), (104, 196, 88))
    spots = ((255, 222, 160), (255, 250, 246), (228, 240, 255), (232, 250, 196))
    cap = caps[f]
    shadow(d, 40, 94, 18, 7)
    stem_col = (250, 236, 206)
    stem = Poly([(32, 95), (33.5, 72), (46.5, 72), (48, 95), (40, 97)])
    cel(d, stem, stem_col, sh=(2.2, 0.0), lw=1.1)
    # gills: the underside of the cap, seen from a little above
    Ell(40, 70, 22, 6).draw(d, fill=shade(stem_col, 1.2))
    dome = Poly(arc_pts(40, 68, 26, 30, 180, 360, 24) + arc_pts(40, 68, 26, 5.5, 0, 180, 12)[1:-1])
    spot_regions = [(Ell(x, y, rx, ry), spots[f]) for (x, y, rx, ry) in
                    ((30, 55, 5, 4), (46, 48, 6, 4.5), (54, 61, 4, 3.5), (37, 66, 3.5, 2.5), (41, 42, 3, 2.2))]
    cel(d, dome, cap, sh=(4.0, 3.0), hi=(1.6, 1.6), hi_tone=lit(cap, 0.7), regions=spot_regions, lw=1.3)


def _palm(d, f, r):
    """A palm on the beach: a leaning ringed trunk and a crown of fronds."""
    lean = (1, -1, 1, -1)[f]
    shadow(d, 40 + lean * 6, 94, 22, 7)
    top_x = 40 + lean * 9
    trunk_pts = [(40, 96), (40 + lean * 1.5, 78), (40 + lean * 4, 58), (40 + lean * 7, 40), (top_x, 26)]
    trunk = Limb(trunk_pts, [4.2, 3.8, 3.4, 3.0, 2.6], cap=False)
    cel(d, trunk, (190, 138, 86), sh=(1.6, 0.0), lw=1.1)
    for i in range(1, 4):
        x, y = trunk_pts[i]
        stroke(d, arc_pts(x, y, 3.6, 1.4, 20, 160, 6), 0.8, shade((190, 138, 86), 1.0))
    frond = mix(LEAF, (80, 190, 80), 0.4)
    angles = (-160, -125, -58, -20, 18, 162, 140, 40)
    for k, a in enumerate(angles):
        ln = 31 if abs(a) > 100 or abs(a) < 30 else 27
        col = frond if k % 2 == 0 else shade(frond, 0.4)
        droop = 1 if a > -90 else -1
        _frond(d, top_x, 26, a, ln, col, droop)
    for (dx, dy) in ((-3.5, 2.5), (3, 3), (0, 5.5)):
        cel(d, Ell(top_x + dx, 26 + dy, 3.2, 3.2), (140, 90, 50), sh=(0.7, 0.7), lw=0.8)


def _frond(d, x, y, ang, ln, col, droop):
    """One palm frond: a curving rib with a leaf along it, tip hanging down."""
    pts = []
    a = math.radians(ang)
    for i in range(7):
        t = i / 6.0
        px = x + math.cos(a) * ln * t
        py = y + math.sin(a) * ln * t + (t * t) * 9.0
        pts.append((px, py))
    widths = [1.2, 4.2, 5.0, 4.6, 3.6, 2.2, 0.6]
    cel(d, Limb(pts, widths, cap=False), col, sh=None, lw=0.9)
    stroke(d, pts[:-1], 0.5, shade(col, 1.2))


def _pine(d, f, r):
    """A snowy fir: stacked scalloped tiers, each with snow on its shoulders."""
    shadow(d, 40, 94, 18, 7)
    Poly([(37, 97), (37, 86), (43, 86), (43, 97)]).draw(d, fill=BARK, outline=ink(BARK), width=0.9)
    tiers = ((88, 26, 24), (70, 21, 22), (53, 16, 20), (37, 11, 19))
    snow_amount = (0.45, 0.6, 0.35, 0.5)[f]
    for i, (yb, hw, ht) in enumerate(tiers):
        apex = (40, yb - ht - (6 if i == 3 else 0))
        bottom = []
        n = 6
        for k in range(n + 1):
            s = k / float(n)
            x = 40 + hw - 2 * hw * s
            dy = 2.6 * abs(math.sin(s * n * math.pi / 2 + 0.2))
            bottom.append((x, yb + dy))
        tier = Poly([apex] + bottom)
        cel(d, tier, PINE, sh=(3.0, 1.0), lw=1.2)
        # snow on its shoulders: the upper part of the tier, with a wavy edge
        cut = snow_amount
        lx = 40 - hw * cut
        rx = 40 + hw * cut
        yc = apex[1] + (yb - apex[1]) * cut
        edge = []
        for k in range(7):
            s = k / 6.0
            edge.append((rx - (rx - lx) * s, yc + 1.8 * math.sin(s * math.pi * 3)))
        cap = Poly([apex] + edge)
        cel(d, cap, SNOW, sh=(1.5, 0.5), tone=(206, 222, 246), lw=0.9)
    if f == 3:
        sparkle(d, 40, 14, 3.0, (255, 236, 140))


def _snowman(d, f, r):
    """Three snowballs, coal eyes, a carrot nose, a scarf."""
    shadow(d, 40, 94, 17, 6)
    scarf = ((236, 64, 72), (72, 128, 236), (84, 190, 96), (255, 176, 60))[f]
    sn = (246, 250, 255)
    tone = (212, 226, 248)
    cel(d, Ell(40, 84, 15, 12), sn, sh=(3.0, 2.0), tone=tone, lw=1.2)
    cel(d, Ell(40, 66, 11, 9), sn, sh=(2.4, 1.6), tone=tone, lw=1.2)
    cel(d, Ell(40, 50, 9, 8), sn, sh=(2.0, 1.4), tone=tone, lw=1.2)
    # stick arms
    stroke(d, [(30, 64), (22, 58), (19, 54)], 1.1, (128, 86, 52))
    stroke(d, [(22, 58), (19, 60)], 0.9, (128, 86, 52))
    stroke(d, [(50, 64), (58, 57), (61, 52)], 1.1, (128, 86, 52))
    # buttons
    for y in (62, 68, 80):
        Ell(40, y, 1.2, 1.2).draw(d, fill=(64, 60, 72))
    # scarf round the neck, one end hanging
    band = Poly(arc_pts(40, 57, 10, 3.2, 0, 180, 10) + arc_pts(40, 56, 10, 2.0, 180, 0, 10))
    cel(d, band, scarf, sh=(1.0, 0.6), lw=0.9)
    cel(d, Poly([(44, 58), (48, 58), (49, 68), (45, 69)]), scarf, sh=(0.8, 0.0), lw=0.9)
    # face
    Ell(36.5, 48, 1.2, 1.4).draw(d, fill=(56, 52, 64))
    Ell(43.5, 48, 1.2, 1.4).draw(d, fill=(56, 52, 64))
    cel(d, Poly([(39.5, 51), (41.2, 51.6), (47, 53.5), (40, 52.8)]), (255, 140, 50), sh=None, lw=0.6)
    if f == 3:  # a top hat
        cel(d, RRect(33, 40, 47, 42.5, 1.0), (52, 48, 64), sh=None, lw=0.8)
        cel(d, RRect(35.5, 31, 44.5, 41, 1.0), (52, 48, 64), sh=(1.0, 0.0), lw=0.8)
        Poly([(35.5, 38), (44.5, 38), (44.5, 39.6), (35.5, 39.6)]).draw(d, fill=scarf)


def _crystal(d, f, r):
    """An amethyst cluster growing out of a rock."""
    shadow(d, 40, 94, 18, 7)
    base = _boulder(40, 90, 14, 7, 90 + f)
    cel(d, base, (150, 142, 150), sh=(3.0, 2.0), lw=1.1)
    col = (178, 128, 240)
    sets = (
        [(40, 88, -90, 34, 11), (31, 90, -120, 22, 8), (50, 90, -62, 25, 8)],
        [(42, 88, -95, 30, 10), (32, 90, -130, 18, 7), (51, 89, -70, 27, 9), (38, 91, -100, 14, 6)],
        [(38, 89, -85, 36, 11), (50, 90, -55, 20, 8), (28, 91, -115, 18, 7)],
        [(41, 89, -92, 28, 10), (30, 89, -125, 24, 8), (52, 90, -65, 21, 8)],
    )[f]
    for (x, y, ang, ln, w) in sets:
        _prism(d, x, y, ang, ln, w, col)


def _prism(d, x, y, ang, ln, w, col):
    """A hexagonal crystal from (x, y) along `ang`: a lit face, a shaded face."""
    hw = w * 0.5
    local = [(0.0, -hw), (ln * 0.78, -hw), (ln, 0.0), (ln * 0.78, hw), (0.0, hw)]
    body = Poly(xform(local, x, y, ang))
    dark = Poly(xform([(0.0, 0.0), (ln * 0.78, 0.0), (ln, 0.0), (ln * 0.78, hw), (0.0, hw)], x, y, ang))
    cel(d, body, col, sh=None, regions=[(dark, shade(col, 1.1))], lw=1.0)
    stroke(d, xform([(ln * 0.15, -hw * 0.45), (ln * 0.7, -hw * 0.45)], x, y, ang), 0.9, lit(col, 1.2))


def _coral(d, f, r):
    """Branching coral: soft limbs ending in round knobs."""
    shadow(d, 40, 94, 16, 6)
    col = ((255, 120, 150), (255, 150, 90), (200, 130, 240), (255, 104, 120))[f]
    rr = random.Random(250 + f)
    branches = []
    for k in range(5):
        a = math.radians(-90 + (k - 2) * 26 + rr.uniform(-8, 8))
        ln = rr.uniform(20, 32) * (1.0 if k in (1, 2, 3) else 0.75)
        mid = (40 + math.cos(a) * ln * 0.5 + rr.uniform(-3, 3), 92 + math.sin(a) * ln * 0.5)
        tip = (40 + math.cos(a) * ln, 92 + math.sin(a) * ln)
        branches.append([(40 + (k - 2) * 1.5, 93), mid, tip])
    parts = [Limb(b, [3.4, 2.8, 2.3]) for b in branches]
    for b in branches:
        tx, ty = b[-1]
        parts.append(Ell(tx, ty, 3.4, 3.4))
    blob(d, parts, col, sh=(1.6, 1.2), lw=1.1)
    for b in branches:
        tx, ty = b[-1]
        Ell(tx - 1.0, ty - 1.0, 1.1, 1.0).draw(d, fill=lit(col, 0.8))


def _ruins(d, f, r):
    """A broken marble column, ivy climbing it, rubble at its foot."""
    shadow(d, 40, 94, 20, 7)
    marble = (232, 226, 214)
    top_y = (52, 60, 46, 56)[f]
    shaft = Poly([(31, 92), (31, top_y + 4), (35, top_y), (38, top_y + 5), (42, top_y - 2), (46, top_y + 3),
                  (49, top_y + 1), (49, 92), (40, 94)])
    fl = [(Poly([(x - 0.8, top_y + 8), (x + 0.8, top_y + 8), (x + 0.8, 90), (x - 0.8, 90)]), shade(marble, 0.6))
          for x in (35, 40, 45)]
    cel(d, shaft, marble, sh=(3.0, 0.0), regions=fl, lw=1.2)
    cel(d, Poly([(27, 97), (27, 90), (53, 90), (53, 97), (40, 100)]), shade(marble, 0.3), sh=(2.0, 0.0), lw=1.1)
    for (x, y, k) in ((24, 95, 1.0), (56, 93, 0.8), (51, 98, 0.6)):
        pebble(d, x, y, 3.2 * k, 2.2 * k, shade(marble, 0.5))
    ivy = mix(LEAF, (70, 150, 80), 0.5)
    stroke(d, [(47, 90), (45, 80), (47, 72), (44, 64)], 0.8, shade(ivy, 1.0))
    for (x, y, a) in ((45, 82, 200), (47, 74, -20), (44, 67, 200), (46, 88, -30)):
        leaf(d, x, y, a, 5.5, 3.2, ivy, vein=False)


def _thorns(d, f, r):
    """A bramble: a dark tangle, thorny canes sticking out, a few berries."""
    shadow(d, 40, 94, 20, 7)
    col = (82, 132, 70)
    parts = [Ell(40, 80, 19, 12), Ell(29, 78, 10, 9), Ell(51, 78, 10, 9), Ell(40, 70, 11, 9)]
    blob(d, parts, col, sh=(3.0, 3.0), lw=1.2)
    cane = (150, 96, 70)
    rr = random.Random(3100 + f)
    for k in range(4):
        a = math.radians(-160 + k * 45 + rr.uniform(-10, 10))
        x0, y0 = 40 + math.cos(a) * 6, 78 + math.sin(a) * 4
        x1, y1 = 40 + math.cos(a) * 24, 78 + math.sin(a) * 16
        stroke(d, [(x0, y0), ((x0 + x1) / 2, (y0 + y1) / 2 - 2), (x1, y1)], 1.3, cane)
        for t in (0.45, 0.75):
            tx, ty = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t - 1
            Poly([(tx - 1.0, ty), (tx + 1.0, ty), (tx, ty - 2.6)]).draw(d, fill=lit(cane, 0.6))
    for (x, y) in ((33, 74), (46, 70), (50, 82), (36, 84))[: 2 + f % 3]:
        cel(d, Ell(x, y, 1.8, 1.8), (150, 70, 190), sh=(0.4, 0.4), lw=0.5)


# ---------------------------------------------------------------------------
# The tileset
# ---------------------------------------------------------------------------

# (id, name, form, draw function). Ids are atlas columns and must match Tile.scala.
TILES = [
    (0, "grass", GROUND, _grass_tile),
    (1, "water", POOL, _water),
    (2, "sand", GROUND, _sand),
    (3, "stone", GROUND, _stone),
    (4, "wall", BLOCK, _wall),
    (5, "tree", PROP, _tree),
    (6, "path", GROUND, _path),
    (7, "deep_water", POOL, _deep_water),
    (8, "snow", GROUND, _snow),
    (9, "ice", GROUND, _ice),
    (10, "lava", POOL, _lava),
    (11, "mountain", BLOCK, _mountain),
    (12, "fence", BLOCK, _fence),
    (13, "metal", GROUND, _metal),
    (14, "glass", GROUND, _glass),
    (15, "energy_field", BLOCK, _energy),
    (16, "circuit", GROUND, _circuit),
    (17, "void", BLOCK, _void),
    (18, "toxic", BLOCK, _toxic),
    (19, "plasma", BLOCK, _plasma),
    (20, "flowers", GROUND, _flowers),
    (21, "dirt", GROUND, _dirt),
    (22, "cobblestone", GROUND, _cobble),
    (23, "marsh", GROUND, _marsh),
    (24, "crystal", PROP, _crystal),
    (25, "coral", PROP, _coral),
    (26, "ruins", PROP, _ruins),
    (27, "moss", GROUND, _moss),
    (28, "obsidian", BLOCK, _obsidian),
    (29, "cliff", BLOCK, _cliff),
    (30, "ash", GROUND, _ash),
    (31, "thorns", PROP, _thorns),
    (32, "basalt", BLOCK, _basalt),
    (33, "gravel", GROUND, _gravel),
    (34, "bush", PROP, _bush),
    (35, "rock", PROP, _rock),
    (36, "mushroom", PROP, _mushroom),
    (37, "palm", PROP, _palm),
    (38, "pine", PROP, _pine),
    (39, "snowman", PROP, _snowman),
    (40, "planks", GROUND, _planks),
    (41, "ice_block", BLOCK, _ice_block),
]

# Height of each block's top above the ground, in atlas pixels (twice display pixels).
BLOCK_HEIGHTS = {4: 50, 11: 68, 12: 30, 15: 44, 17: 32, 18: 16, 19: 24, 28: 50, 29: 72, 32: 58, 41: 34}

# Blocks whose four frames are an animation; every other block draws one frame four times.
ANIMATED_BLOCKS = {15, 17, 18, 19}


# Props drawn bigger than they are authored, about their foot (PropDraw)
PROP_SCALE = {24: 1.25, 25: 1.3, 26: 1.2, 31: 1.25, 34: 1.3, 35: 1.45, 36: 1.1, 39: 1.2}


def render_cell(tid, form, fn, frame):
    img = Image.new("RGBA", (CELL_W * SS, CELL_H * SS), CLEAR)
    if form == PROP and tid in PROP_SCALE:
        d = PropDraw(ImageDraw.Draw(img), SS, img, PROP_SCALE[tid])
    else:
        d = ScaledDraw(ImageDraw.Draw(img), SS, img)
    animated = form == POOL or (form == BLOCK and tid in ANIMATED_BLOCKS)
    variant = frame if (form in (GROUND, PROP) or animated) else 0
    rng = random.Random(tid * 7919 + (0 if animated else variant))
    fn(d, variant, rng)
    cell = img.resize((CELL_W, CELL_H), Image.LANCZOS)
    if form in (GROUND, POOL):
        cell.putalpha(hard_mask(0))
    elif form == BLOCK:
        cell.putalpha(hard_mask(BLOCK_HEIGHTS[tid]))
    return cell


def build_atlas():
    n = max(t[0] for t in TILES) + 1
    atlas = Image.new("RGBA", (n * CELL_W, CELL_H * FRAMES), CLEAR)
    for (tid, name, form, fn) in TILES:
        for frame in range(FRAMES):
            atlas.paste(render_cell(tid, form, fn, frame), (tid * CELL_W, frame * CELL_H))
    return atlas


def main():
    ids = [t[0] for t in TILES]
    assert ids == list(range(len(ids))), "tile ids must run 0..n-1 without gaps: they are atlas columns"
    atlas = build_atlas()
    out = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "sprites")
    os.makedirs(out, exist_ok=True)
    p = os.path.join(out, "tiles.png")
    atlas.save(p, optimize=True)
    print(f"Generated {p} ({atlas.width}x{atlas.height}, {len(TILES)} tiles)")


if __name__ == "__main__":
    main()
