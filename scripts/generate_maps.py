#!/usr/bin/env python3
"""Generate the maps: every map in worlds/ but the four space arenas (which were drawn by hand).

    python3 scripts/generate_maps.py            # writes the maps
    python3 scripts/generate_maps.py --check    # only checks them, writes nothing
    python3 scripts/generate_maps.py --preview /tmp/maps   # also a top-down picture of each

No dependencies; --preview needs Pillow.

Every map in the game is the same shape, because it plays well: a round arena in the middle
of a square world, closed off by terrain that nobody can cross, with cover scattered through it
in rings, arcs and blocks. These keep that shape and change its size and what it is made of.
A theme has a small, a medium and a large map; the first three themes a medium and a large one:

  bright      The Meadow (grass and flowers, paths round a pond, fairy rings, a forest all round)
              and The Wildwood, the same wood at the Colony's size, a river round it.
  the sea     The Lagoon (an island: a beach round a grassy middle, boardwalks, palms; sea all
              round) and The Archipelago, seventeen islands joined by boardwalks.
  winter      The Snowglobe (snow, a frozen pond, snowmen, ice blocks, pines all round) and
              The Tundra, with a frozen river, hot springs and crags.
  the volcano The Crucible, The Caldera, The Cinderlands: scorched rock and ash round pools,
              lakes and rivers of lava, basalt, burnt trees; a moat of lava all round.
  the grave   The Crypt, The Graveyard, The Necropolis: plots of graves in rows, mausoleums,
              cypresses, lanterns, cobbled paths; the cemetery's wall all round.
  the desert  The Tomb, The Oasis, The Dunes: deep sand round a pyramid or a pool, obelisks,
              ruins, cacti, bones; a plateau of red rock all round.

Each map is symmetric under all eight of the square's symmetries: every decision about a cell
is made from its offset to the centre with the signs dropped and the axes sorted. That is what
makes a Teams match fair (TeamDivider splits the world down its middle column, which is the
centre column here) and a free-for-all fair from every spawn.

Checked before writing, the same way WorldMapsTest checks every registered map: spawn points on
open ground, none on the divider's column, all open ground one region, and room to walk and to
fight everywhere (scripts/map_audit.py, which has the rules and why): no gap or path under three
cells wide, and nothing of any size reached only by a way under five.

How a map is kept to that:
  * What is built (rings, courts, bridges, causeways) is built to it: a gate, a bridge and a
    causeway are five cells wide or more (`on_axis(a, 2)` and up; `on_diagonal(a, b, 4)`, a
    diagonal being walked along its middle), a ring of trees is a horseshoe with one wide mouth.
  * Loose cover (the odd rock or tree) goes on after the rest, by `Arena.scatter`, which leaves
    out any that would stand within three cells of other cover.
  * Woods, crags and mesas are solid clumps, not trees one cell apart to thread between.
  * `Arena.open_up` then fills whatever tight ground is left with the cover beside it (two clumps
    a cell apart become one, a strip between a rock and the arena's edge closes).
"""

import argparse
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import map_audit  # noqa: E402
from map_audit import WALKABLE, POOLS  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# Tile ids (Tile.scala)
GRASS, WATER, SAND, STONE, WALL, TREE, PATH, DEEP, SNOW, ICE = 0, 1, 2, 3, 4, 5, 6, 7, 8, 9
FLOWERS, DIRT, COBBLE, CLIFF = 20, 21, 22, 29
CORAL, RUINS, MOSS = 25, 26, 27
BUSH, ROCK, MUSHROOM, PALM, PINE, SNOWMAN, PLANKS, ICE_BLOCK = 34, 35, 36, 37, 38, 39, 40, 41
LAVA, MOUNTAIN, OBSIDIAN, ASH, BASALT = 10, 11, 28, 30, 32
CINDER, CHARRED_TREE, MAGMA_ROCK = 42, 43, 44
DEAD_GRASS, TOMBSTONE, DEAD_TREE, CRYPT, LANTERN = 45, 46, 47, 48, 49
DUNE, CACTUS, OBELISK, MESA, BONES = 50, 51, 52, 53, 54
CYPRESS = 55

# Loose cover keeps this many open cells between itself and any other cover (Arena.scatter)
SCATTER_GAP = 3


def h01(a, b, salt):
    """A stable pseudo-random number in [0, 1) for a canonical offset."""
    n = (a * 374761393 + b * 668265263 + salt * 1274126177) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1103515245) & 0xFFFFFFFF
    n = (n ^ (n >> 16)) & 0xFFFFFFFF
    return n / 4294967296.0


def canon(dx, dy):
    """An offset with its signs dropped and its axes sorted: the same for all eight of a
    cell's mirror images, so anything decided from it is decided the same for all eight."""
    a, b = abs(dx), abs(dy)
    return (a, b) if a <= b else (b, a)


def noise(a, b, scale, salt):
    """Smooth value noise in [0, 1) over a canonical offset: bilinear between lattice points
    `scale` cells apart, so patches come out as rounded blobs rather than squares."""
    fx, fy = a / scale, b / scale
    ix, iy = int(math.floor(fx)), int(math.floor(fy))
    tx, ty = fx - ix, fy - iy
    tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
    v00, v10 = h01(ix, iy, salt), h01(ix + 1, iy, salt)
    v01, v11 = h01(ix, iy + 1, salt), h01(ix + 1, iy + 1, salt)
    return (v00 * (1 - tx) + v10 * tx) * (1 - ty) + (v01 * (1 - tx) + v11 * tx) * ty


def near_any(dx, dy, centres, r):
    return any(math.hypot(dx - x, dy - y) <= r for (x, y) in centres)


def sym8(pts):
    """Every mirror image of the given offsets (the centres of a feature, from one of them)."""
    out = []
    for (x, y) in pts:
        for (a, b) in ((x, y), (-x, y), (x, -y), (-x, -y), (y, x), (-y, x), (y, -x), (-y, -x)):
            if (a, b) not in out:
                out.append((a, b))
    return out


def ring_cell(dx, dy, centres, r, thick=0.75, gaps=(), gap_w=26.0):
    """Is (dx, dy) on a broken ring of radius r round one of `centres`? Gaps are angles (deg)
    measured from the direction pointing away from the map's centre, so every copy of the
    ring has its gaps in the same place relative to the arena — and, as long as the list is
    symmetric about 0, mirror copies match."""
    for (cx, cy) in centres:
        d = math.hypot(dx - cx, dy - cy)
        if abs(d - r) > thick:
            continue
        out = math.atan2(cy, cx) if (cx, cy) != (0, 0) else -math.pi / 2
        a = math.degrees(math.atan2(dy - cy, dx - cx) - out)
        a = (a + 180.0) % 360.0 - 180.0
        if all(abs(((a - g) + 180.0) % 360.0 - 180.0) > gap_w / 2 for g in gaps):
            return True
    return False


class Arena:
    """A square world of `size` cells with a round arena of radius `radius` in the middle."""

    def __init__(self, name, filename, size, radius, background):
        self.name, self.filename, self.size, self.radius, self.background = name, filename, size, radius, background
        self.c = size // 2
        self.grid = [[GRASS] * size for _ in range(size)]
        self.spawns = []
        self.faults = []

    def paint(self, fn):
        """Set every cell from fn(dx, dy, d) -> tile, where (dx, dy) is the offset to the centre
        and d its length. fn must decide from canon(dx, dy) and d only."""
        for y in range(self.size):
            for x in range(self.size):
                dx, dy = x - self.c, y - self.c
                self.grid[y][x] = fn(dx, dy, math.hypot(dx, dy))

    def place_spawns(self, slots):
        """Spawn points from slots (radius, angle in degrees, 0 < angle < 45): each is moved
        to the nearest cell in the same eighth of the arena that is open ground with open
        ground two cells all round it (the middle of a lane: nobody spawns in a corner), and
        then mirrored into all eight eighths. None ends up on
        an axis or a diagonal, so the eight copies are distinct and the divider's column —
        the vertical axis — has none."""
        pts = []
        for (r, ang) in slots:
            a0 = r * math.sin(math.radians(ang))
            b0 = r * math.cos(math.radians(ang))
            best = None
            for b in range(1, self.radius + 1):
                for a in range(1, b):
                    d = math.hypot(a - a0, b - b0)
                    if d > 6 or (best and d >= best[0]):
                        continue
                    if self._open_around(self.c + a, self.c + b):
                        best = (d, (a, b))
            if best is None:
                self.faults.append(f"no open cell near spawn slot ({r}, {ang})")
                continue
            a, b = best[1]
            for (x, y) in sym8([(a, b)]):
                p = (self.c + x, self.c + y)
                if p not in pts:
                    pts.append(p)
        self.spawns = pts

    def fill_pockets(self, tile, most=None):
        """Open ground nobody could walk to from the middle of the arena (a cell ringed by
        trees, the inside of a clump of rock) becomes `tile`. The pockets are mirror images of
        one another, as everything is, so the map stays symmetric. Returns how many cells it
        filled; with more than `most` of them it fills none and returns minus the count."""
        g, n, c = self.grid, self.size, self.c
        start = next((c + k, c) for k in range(self.radius) if g[c][c + k] in WALKABLE and g[c][c + k + 1] in WALKABLE)
        seen = {start}
        stack = [start]
        while stack:
            x, y = stack.pop()
            for (nx, ny) in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
                if 0 <= nx < n and 0 <= ny < n and g[ny][nx] in WALKABLE and (nx, ny) not in seen:
                    seen.add((nx, ny))
                    stack.append((nx, ny))
        pockets = [(x, y) for y in range(n) for x in range(n) if g[y][x] in WALKABLE and (x, y) not in seen]
        if most is not None and len(pockets) > most:
            return -len(pockets)
        for (x, y) in pockets:
            g[y][x] = tile
        return len(pockets)

    def _open_around(self, x, y):
        k = map_audit.LANE // 2
        return all(self.grid[y + oy][x + ox] in WALKABLE for oy in range(-k, k + 1) for ox in range(-k, k + 1))

    def scatter(self, on, fn, gap=SCATTER_GAP):
        """Loose cover, put on after everything else, on the grounds in `on` (not on a road or
        a deck): fn(a, b, d) -> a prop, or None, for the canonical offset (a, b) at distance d
        from the middle. A prop is left out if any cover
        already stands within `gap` cells of it, or another prop would and has the better of
        the draw, so the ground between any two pieces of cover is `gap` cells wide or more:
        a rock beside a wall a cell away made a gap one cell wide, which a walk slides past.
        Mirror images draw alike, so the map stays symmetric."""
        g, n, c = self.grid, self.size, self.c
        wanted = {}
        for y in range(gap, n - gap):
            for x in range(gap, n - gap):
                if g[y][x] not in on:
                    continue
                dx, dy = x - c, y - c
                a, b = canon(dx, dy)
                t = fn(a, b, math.hypot(dx, dy))
                if t is not None:
                    wanted[(x, y)] = (t, h01(a, b, 977))
        placed = {}
        for (x, y), (t, draw) in wanted.items():
            ok = True
            for oy in range(-gap, gap + 1):
                for ox in range(-gap, gap + 1):
                    if (ox, oy) == (0, 0):
                        continue
                    if g[y + oy][x + ox] not in WALKABLE:
                        ok = False
                    other = wanted.get((x + ox, y + oy))
                    if other is not None and other[1] >= draw:
                        ok = False
            if ok:
                placed[(x, y)] = t
        for (x, y), t in placed.items():
            g[y][x] = t
        return len(placed)

    def open_up(self, pocket):
        """The last pass over a map: whatever ground is still too narrow to walk becomes the
        cover beside it (map_audit.fill_tight, and fill_side_rooms for a corner of ground joined
        on only by a squeeze between two corners), and ground that leaves nobody able to reach
        becomes `pocket` (fill_pockets), until none of them finds anything. A pocket of any size is
        a mistake in the map (a region walled in, a bridge too narrow that was filled): this
        closes small ones and refuses big ones."""
        total = 0
        while True:
            n = map_audit.fill_tight(self.grid) + map_audit.fill_side_rooms(self.grid, 60)
            cut = self.fill_pockets(pocket, most=60)
            if cut < 0:
                self.faults.append(f"{-cut} cells of ground are walled in once the tight ground is filled: widen the way to them")
                return total
            total += n + cut
            if n + cut == 0:
                return total

    # -- checks ----------------------------------------------------------------

    def check(self):
        g, n, c = self.grid, self.size, self.c
        problems = list(self.faults)
        for y in range(n):
            for x in range(n):
                t = g[y][x]
                for (mx, my) in ((2 * c - x, y), (x, 2 * c - y), (y, x)):
                    if 0 <= mx < n and 0 <= my < n and g[my][mx] != t:
                        problems.append(f"not symmetric at ({x},{y})")
                        break
                if problems:
                    break
            if problems:
                break
        if not self.spawns:
            problems.append("no spawn points")
        for (x, y) in self.spawns:
            if g[y][x] not in WALKABLE:
                problems.append(f"spawn ({x},{y}) is not open ground")
            if x == c:
                problems.append(f"spawn ({x},{y}) is on the divider's column")
        left = sum(1 for (x, _) in self.spawns if x < c)
        right = sum(1 for (x, _) in self.spawns if x > c)
        if left != right:
            problems.append(f"spawns split {left}/{right} between the halves")
        # all open ground one region (4-connected, as WorldMapsTest walks it)
        open_cells = {(x, y) for y in range(n) for x in range(n) if g[y][x] in WALKABLE}
        start = self.spawns[0] if self.spawns else next(iter(open_cells))
        seen = {start}
        stack = [start]
        while stack:
            x, y = stack.pop()
            for (nx, ny) in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
                if (nx, ny) in open_cells and (nx, ny) not in seen:
                    seen.add((nx, ny))
                    stack.append((nx, ny))
        if len(seen) != len(open_cells):
            stray = sorted(open_cells - seen)[:6]
            problems.append(f"{len(open_cells) - len(seen)} open cells cut off from the rest, e.g. {stray}")
        # nothing open outside the arena: the edge of the circle is the edge of the map
        for (x, y) in open_cells:
            if math.hypot(x - c, y - c) > self.radius + 0.01:
                problems.append(f"open ground outside the arena at ({x},{y})")
                break
        # room to walk and to fight everywhere
        problems += map_audit.Audit(self.grid, self.spawns).problems()
        return problems

    def stats(self):
        g, c, r = self.grid, self.c, self.radius
        inside = [(x, y) for y in range(self.size) for x in range(self.size) if math.hypot(x - c, y - c) <= r]
        cover = sum(1 for (x, y) in inside if g[y][x] not in WALKABLE and g[y][x] not in POOLS)
        pools = sum(1 for (x, y) in inside if g[y][x] in POOLS)
        return f"{self.size}x{self.size}, arena r={r}, {len(inside)} cells inside, " \
               f"{100.0 * cover / len(inside):.1f}% cover, {100.0 * pools / len(inside):.1f}% water or lava, " \
               f"{len(self.spawns)} spawns"

    # -- output ----------------------------------------------------------------

    def to_json(self):
        return {
            "name": self.name,
            "background": self.background,
            "width": self.size,
            "height": self.size,
            "layers": [{"type": "grid", "data": [" ".join(str(t) for t in row) for row in self.grid]}],
            "spawnPoints": [{"x": x, "y": y} for (x, y) in self.spawns],
        }


# ---------------------------------------------------------------------------
# Shapes the maps are made of
# ---------------------------------------------------------------------------

def on_axis(a, w=1):
    """On one of the two axes through the middle, `w` cells either side of it: a spoke. A road
    over open ground can be any width; a bridge or a causeway is `w` 2 or more (five cells)."""
    return a <= w


def on_diagonal(a, b, w=1):
    """On one of the two diagonals through the middle: a staircase 2w + 1 cells wide, which
    can be walked along (its cells touch side to side). Only its middle is walked straight (a
    diagonal step wants the cells either side of it open), so a bridge is `w` 4."""
    return b - a <= w


def knot(dx, dy, centres, missing_inner=True):
    """A three-by-three block round one of `centres`, with the corner nearest the middle of the
    map left open: 1 for the block, 2 for that corner, 0 for neither."""
    for (ox, oy) in centres:
        if abs(dx - ox) <= 1 and abs(dy - oy) <= 1:
            inner = abs(dx) < abs(ox) and abs(dy) < abs(oy)
            if missing_inner and inner and abs(dx - ox) == 1 and abs(dy - oy) == 1:
                return 2
            return 1
    return 0


def ell(dx, dy, centres):
    """An L of three cells with a fourth beside it, round one of `centres` on a diagonal."""
    for (bx, by) in centres:
        ex, ey = dx - bx, dy - by
        sx = 1 if bx > 0 else -1
        sy = 1 if by > 0 else -1
        if (ex, ey) in ((0, 0), (sx, 0), (0, sy), (-sx, -sy)):
            return True
    return False


def bar(dx, dy, centres, half=1.6, thick=0.5):
    """A short wall square to the middle of the map, through one of `centres`."""
    for (fx, fy) in centres:
        e = math.hypot(fx, fy)
        along = abs(((dx - fx) * -fy + (dy - fy) * fx) / e)
        across = abs(((dx - fx) * fx + (dy - fy) * fy) / e)
        if along <= half and across <= thick:
            return True
    return False


def square_ring(dx, dy, centres, half, gate=2):
    """The wall of a square court round one of `centres`, `half` cells from its middle to its
    wall, with a gate `gate` cells either side of the middle of each side (2: five cells, the
    least a gate is): 1 on the wall, 2 inside it, 0 outside."""
    for (ox, oy) in centres:
        ex, ey = abs(dx - ox), abs(dy - oy)
        if ex <= half and ey <= half:
            if max(ex, ey) == half:
                return 0 if min(ex, ey) <= gate else 1
            return 2
    return 0


def horseshoe(dx, dy, centres, r, mouth=150.0, thick=0.72):
    """A ring of radius r round one of `centres` with one mouth, `mouth` degrees of it, facing
    the middle of the map: cover to stand behind with a way in wide enough to walk without
    looking. (The rings used to have three or four gaps of two or three cells each.)"""
    return ring_cell(dx, dy, centres, r, thick, gaps=(180,), gap_w=mouth)


def backs(centres, r):
    """The cell at the back of each horseshoe: the far side of it from the middle of the map."""
    out = []
    for (cx, cy) in centres:
        e = math.hypot(cx, cy)
        out.append((round(cx + cx / e * r), round(cy + cy / e * r)))
    return out


def to_edge(dx, dy, centres):
    """How far (dx, dy) is from the nearest of `centres`."""
    return min(math.hypot(dx - x, dy - y) for (x, y) in centres)


def off_the_ways(a, b, d, R, axis=8, diagonal=11, rim=12):
    """Is a canonical offset clear of the ways across a big map: the axes and the diagonals,
    where its roads and its crossings are, and the ground along its edge? A clump of wood or
    rock (noise, which no one lays out) stands only where this is true, and clear of whatever
    is built, so that it is something to go round and never the side of a way too narrow."""
    return a > axis and b - a > diagonal and d < R - rim


def bearing(a, b):
    """The angle of a canonical offset from the nearest axis: 0 on an axis, 45 on a diagonal."""
    return math.degrees(math.atan2(a, b))


# ---------------------------------------------------------------------------
# The Meadow
# ---------------------------------------------------------------------------

def meadow():
    m = Arena("The Meadow", "the_meadow.json", 120, 46, "sky")
    R = m.radius
    groves = sym8([(15, 15)])                 # fairy rings of trees, between the spokes
    caps = backs(groves, 5.0)                 # a giant mushroom at the back of each
    shrooms = sym8([(5, 36)])                 # giant mushrooms beside each spoke, past the ring road
    outcrops = sym8([(27, 27)])               # grass-topped earth: hard cover on the diagonals
    hedges = sym8([(12, 30)])                 # short hedges off the ring road

    def fn(dx, dy, d):
        a, b = canon(dx, dy)
        if d > R + 1.2:
            return TREE
        if d > R:
            # the forest's edge: a hedge of bushes, a few trees stepping out of it
            return TREE if h01(a, b, 11) < 0.3 else BUSH
        # the pond in the middle, and the flowers round it
        if d <= 4.2:
            return WATER
        if d <= 6.2:
            return FLOWERS
        # the paths: a ring round the pond, four spokes, and a ring road
        if 7.2 <= d <= 8.8 or (a <= 1 and d <= R - 1) or 29.0 <= d <= 30.6:
            return PATH
        # fairy rings of trees, open toward the pond
        if near_any(dx, dy, caps, 1.0):
            return MUSHROOM
        if horseshoe(dx, dy, groves, 5.0, 130):
            return TREE if h01(a, b, 21) < 0.6 else BUSH
        if near_any(dx, dy, shrooms, 0.9):
            return MUSHROOM
        # outcrops of cliff, three by three with one corner broken off
        k = knot(dx, dy, outcrops)
        if k:
            return CLIFF if k == 1 else GRASS
        # hedges: three bushes in a row, square to the ring road
        if bar(dx, dy, hedges, 1.6, 0.5):
            return BUSH
        # flower patches
        if noise(a, b, 4.0, 41) > 0.78:
            return FLOWERS
        return GRASS

    def loose(a, b, d):
        r = h01(a, b, 31)
        if 10 < d < R - 4:
            return ROCK if r < 0.008 else BUSH if r < 0.016 else TREE if r < 0.020 else None
        return None

    m.paint(fn)
    m.scatter({GRASS, FLOWERS}, loose)
    m.open_up(TREE)
    m.place_spawns([(12, 22.5), (22, 30), (35, 12), (42, 30)])
    return m


# ---------------------------------------------------------------------------
# The Lagoon
# ---------------------------------------------------------------------------

def lagoon():
    m = Arena("The Lagoon", "the_lagoon.json", 130, 50, "sea")
    R = m.radius
    beach = R - 7
    palms = sym8([(20, 20)])
    rockpools = sym8([(9, 33)])
    ruins = [(round(13.5 * math.cos(math.radians(22.5 + 45 * k))), round(13.5 * math.sin(math.radians(22.5 + 45 * k))))
             for k in range(8)]

    def fn(dx, dy, d):
        a, b = canon(dx, dy)
        if d > R + 5:
            return DEEP
        if d > R:
            return WATER
        # the pool in the middle and the deck round it, five planks wide (the pool was bridged
        # by one plank each way)
        if d <= 3.0:
            return WATER
        if d <= 8.2:
            return PLANKS
        # boardwalks out to the beach
        if a <= 1 and d <= beach + 3:
            return PLANKS
        # broken columns round the deck
        if (dx, dy) in ruins:
            return RUINS
        # rock pools: coral growing at the water's edge on two sides, sand round the rest
        e = to_edge(dx, dy, rockpools)
        if e <= 2.7:
            return WATER
        if ring_cell(dx, dy, rockpools, 3.3, 0.6, gaps=(0, 180), gap_w=84):
            return CORAL
        if e <= 4.9:
            return SAND
        # palm groves: a horseshoe of palms open toward the pool
        if horseshoe(dx, dy, palms, 5.0, 130):
            return PALM if h01(a, b, 7) < 0.8 else ROCK
        if d > beach:
            # the beach: sand, with the odd rock and coral at the water's edge
            r = h01(a, b, 13)
            if d > R - 1 and r < 0.06:
                return CORAL if r < 0.025 else ROCK
            return SAND
        # sandy patches through the grass
        if noise(a, b, 5.0, 17) > 0.77:
            return SAND
        return GRASS

    def loose(a, b, d):
        r = h01(a, b, 13)
        if d > beach:
            return PALM if r < 0.010 and d < R - 4 else None
        if 16 < d:
            return ROCK if r < 0.008 else PALM if r < 0.015 else BUSH if r < 0.019 else None
        return None

    m.paint(fn)
    m.scatter({GRASS, SAND}, loose)
    m.open_up(ROCK)
    m.place_spawns([(13, 22.5), (24, 12), (36, 32), (46, 18)])
    return m


# ---------------------------------------------------------------------------
# The Snowglobe
# ---------------------------------------------------------------------------

def snowglobe():
    m = Arena("The Snowglobe", "the_snowglobe.json", 100, 38, "snow")
    R = m.radius
    blocks = sym8([(17, 17)])
    groves = sym8([(0, 24)])
    forts = sym8([(11, 27)])   # snow forts: short walls of ice, facing the middle
    snowmen = [(round(10.5 * math.cos(math.radians(22.5 + 45 * k))), round(10.5 * math.sin(math.radians(22.5 + 45 * k))))
               for k in range(8)]

    def fn(dx, dy, d):
        a, b = canon(dx, dy)
        if d > R:
            return PINE
        # the frozen pond, with a fir standing in the middle of it
        if a == 0 and b == 0:
            return PINE
        if d <= 7.5:
            return ICE
        # snowmen round the pond
        if (dx, dy) in snowmen:
            return SNOWMAN
        # blocks of ice on the diagonals: an L of three with a fourth beside it
        if ell(dx, dy, blocks):
            return ICE_BLOCK
        # pine groves: a horseshoe each, open toward the pond
        if horseshoe(dx, dy, groves, 4.5, 150):
            return PINE
        # snow forts: three blocks of ice square to the middle
        if bar(dx, dy, forts, 1.6, 0.5):
            return ICE_BLOCK
        # icy patches
        if noise(a, b, 3.5, 29) > 0.82:
            return ICE
        return SNOW

    def loose(a, b, d):
        r = h01(a, b, 23)
        if 13 < d < R - 3:
            return ROCK if r < 0.009 else PINE if r < 0.017 else SNOWMAN if r < 0.021 else None
        return None

    m.paint(fn)
    m.scatter({SNOW, ICE}, loose)
    m.open_up(PINE)
    m.place_spawns([(13, 30), (20, 12), (28, 34), (34, 15)])
    return m


# ---------------------------------------------------------------------------
# The volcano: the Crucible, the Caldera, the Cinderlands
# ---------------------------------------------------------------------------

def volcano_rim(d, R):
    """Past the arena: a moat of lava, then the crater's wall of basalt."""
    return BASALT if d > R + 4.5 else LAVA


def volcano_ground(a, b, salt, ash_scale=4.0):
    """Scorched rock, ash drifted over it in patches."""
    return ASH if noise(a, b, ash_scale, salt + 1) > 0.84 else CINDER


def volcano_loose(a, b, salt):
    r = h01(a, b, salt)
    return MAGMA_ROCK if r < 0.008 else CHARRED_TREE if r < 0.015 else OBSIDIAN if r < 0.018 else None


def crucible():
    m = Arena("The Crucible", "the_crucible.json", 90, 34, "volcano")
    R = m.radius
    pillars = sym8([(11, 11)])    # basalt columns on the diagonals
    pools = sym8([(0, 16)])       # pools of lava on the axes
    groves = sym8([(9, 23)])      # burnt trees, a horseshoe open to the middle
    rocks = sym8([(20, 20)])

    def fn(dx, dy, d):
        a, b = canon(dx, dy)
        if d > R:
            return volcano_rim(d, R)
        # the crucible itself: a pool of lava in the middle, ash round it
        if d <= 3.4:
            return LAVA
        if d <= 5.6:
            return ASH
        e = to_edge(dx, dy, pools)
        if e <= 2.4:
            return LAVA
        if e <= 3.8:
            return ASH
        if ell(dx, dy, pillars):
            return BASALT
        if horseshoe(dx, dy, groves, 4.0, 150):
            return CHARRED_TREE
        if near_any(dx, dy, rocks, 1.2):
            return MAGMA_ROCK
        if 8 < d < R - 2:
            return volcano_ground(a, b, 51)
        return CINDER

    m.paint(fn)
    m.scatter({CINDER, ASH}, lambda a, b, d: volcano_loose(a, b, 51) if 8 < d < R - 3 else None)
    m.open_up(MAGMA_ROCK)
    m.place_spawns([(9, 30), (20, 12), (24, 38), (30, 22)])
    return m


def caldera():
    m = Arena("The Caldera", "the_caldera.json", 140, 54, "volcano")
    R = m.radius
    pillars = sym8([(15, 15)])
    knots = sym8([(21, 43)])          # basalt outcrops beyond the river
    groves = sym8([(11, 29)])
    pools = sym8([(8, 48)])           # lava pools either side of each road out, past the river
    walls = sym8([(8, 19)])

    def fn(dx, dy, d):
        a, b = canon(dx, dy)
        if d > R:
            return volcano_rim(d, R)
        # the lake of lava in the middle, an island in it, four causeways out to it five cells
        # wide (they were three, to an island five across)
        if (a, b) == (0, 0):
            return OBSIDIAN
        if d <= 5.6:
            return ASH if d <= 2.6 else CINDER
        if d <= 11.5:
            return CINDER if on_axis(a, 2) else LAVA
        if d <= 13.6:
            return ASH
        # a river of lava round the arena, crossed on the axes and the diagonals
        if 38.0 <= d <= 41.0:
            return CINDER if (on_axis(a, 3) or on_diagonal(a, b, 4)) else LAVA
        e = to_edge(dx, dy, pools)
        if e <= 2.6:
            return LAVA
        if e <= 3.8:
            return ASH
        if ell(dx, dy, pillars):
            return BASALT
        k = knot(dx, dy, knots)
        if k:
            return BASALT if k == 1 else CINDER
        if bar(dx, dy, walls, 1.6, 0.5):
            return BASALT
        if horseshoe(dx, dy, groves, 4.2, 150):
            return CHARRED_TREE
        # the ring road, of ash
        if 24.0 <= d <= 25.8:
            return ASH
        if 15 < d < R - 2 and not (36.5 <= d <= 42.5):
            return volcano_ground(a, b, 61)
        return CINDER

    def loose(a, b, d):
        if 16 < d < R - 3 and not (34.5 <= d <= 44.5) and not (23 <= d <= 27):
            return volcano_loose(a, b, 61)
        return None

    m.paint(fn)
    m.scatter({CINDER, ASH}, loose)
    m.open_up(MAGMA_ROCK)
    m.place_spawns([(18, 30), (22, 12), (32, 22.5), (46, 22.5), (48, 36)])
    return m


def cinderlands():
    m = Arena("The Cinderlands", "the_cinderlands.json", 240, 100, "volcano")
    R = m.radius
    craters = sym8([(20, 48)])                 # eight lesser craters round the great one, off the roads
    pillars = sym8([(22, 22), (14, 36)])
    knots = sym8([(34, 56), (12, 84), (58, 70)])
    groves = sym8([(8, 38), (30, 82), (50, 64), (16, 62)])
    walls = sym8([(9, 22), (42, 84)])
    built = craters + pillars + knots + groves + walls

    def fn(dx, dy, d):
        a, b = canon(dx, dy)
        if d > R:
            return volcano_rim(d, R)
        # the great crater: a lake of lava, an island of rock in it five cells wide all the way
        # round a heart of lava, four causeways seven wide (they were three)
        if d <= 2.5:
            return LAVA
        if d <= 7.6:
            return ASH if d <= 4.0 else CINDER
        if d <= 15.0:
            return CINDER if on_axis(a, 3) else LAVA
        if d <= 17.5:
            return ASH
        # the lesser craters, each a pool with ash round it
        e = to_edge(dx, dy, craters)
        if e <= 5.0:
            return LAVA
        if e <= 6.8:
            return ASH
        # two rivers of lava, the inner crossed on the axes and diagonals, the outer between them too
        if 27.0 <= d <= 29.6:
            return CINDER if (on_axis(a, 3) or on_diagonal(a, b, 4)) else LAVA
        if 70.0 <= d <= 73.4:
            return CINDER if (on_axis(a, 3) or on_diagonal(a, b, 4) or abs(bearing(a, b) - 22.5) <= 3.6) else LAVA
        if ell(dx, dy, pillars):
            return BASALT
        k = knot(dx, dy, knots)
        if k:
            return BASALT if k == 1 else CINDER
        if bar(dx, dy, walls, 2.6, 0.5):
            return BASALT
        if horseshoe(dx, dy, groves, 4.6, 150):
            return CHARRED_TREE
        # roads of ash: one round the great crater, one far out, spokes between the rivers
        if 44.0 <= d <= 45.8 or 86.0 <= d <= 87.6:
            return ASH
        if on_axis(a) and 29.6 < d < 70.0:
            return ASH
        if 19 < d < R - 3 and not (24.5 <= d <= 32) and not (67.5 <= d <= 76):
            # burnt woods: clumps of dead trees with hot boulders among them, solid, to go round
            if noise(a, b, 7.0, 77) > 0.76 and off_the_ways(a, b, d, R) and not (21 <= d <= 36) \
                    and not (63.5 <= d <= 80) and to_edge(dx, dy, built) > 12:
                return CHARRED_TREE if h01(a, b, 78) < 0.75 else MAGMA_ROCK
            return volcano_ground(a, b, 71, 5.0)
        return CINDER

    def loose(a, b, d):
        if 19 < d < R - 4 and not (23.5 <= d <= 33) and not (66.5 <= d <= 77):
            return volcano_loose(a, b, 71)
        return None

    m.paint(fn)
    m.scatter({CINDER, ASH}, loose)
    m.open_up(MAGMA_ROCK)
    m.place_spawns([(21, 22.5), (36, 30), (48, 12), (60, 22.5), (80, 10), (82, 35), (93, 22.5)])
    return m


# ---------------------------------------------------------------------------
# The graveyard: the Crypt, the Graveyard, the Necropolis
# ---------------------------------------------------------------------------

def graveyard_rim(a, b, d, R):
    """Past the arena: the cemetery's wall, and a dark wood beyond it, cypresses with bare
    trees among them. (Bare trees alone, thin as they are, stood like the bars of a cage.)"""
    if d <= R + 1.7:
        return CRYPT
    return DEAD_TREE if h01(a, b, 5) < 0.22 else CYPRESS


def grave_at(a, b, salt, plots=0.5):
    """Graves stand in rows facing the middle of the map, a plot of them here and there (the
    less of the map the higher `plots`, and all of it at 0): three
    stones side by side, five cells to the next three and five to the next row, so there is a
    way between any two rows and through any row. (They stood one to every fourth cell both
    ways: three cells between any two, and a walk through a plot caught on every one.) Left out
    near a diagonal, where the rows of two eighths of the map would meet at a corner. The rows
    are 3, 9, 15... cells out and begin 5 cells off an axis: a lamp at (4, row) heads one."""
    return b % 6 == 3 and a % 8 in (5, 6, 7) and b - a >= 3 and noise(a, b, 9.0, salt) >= plots


def crypt():
    m = Arena("The Crypt", "the_crypt.json", 90, 34, "night")
    R = m.radius
    tombs = sym8([(13, 13)])        # the walls of fallen mausoleums, on the diagonals
    copses = sym8([(9, 23)])
    lamps = sym8([(4, 9), (4, 21)])     # at the head of a row of graves, beside the path

    def fn(dx, dy, d):
        a, b = canon(dx, dy)
        if d > R:
            return graveyard_rim(a, b, d, R)
        # the crypt in the middle, its yard paved, four paths out to the wall
        if abs(dx) <= 1 and abs(dy) <= 1:
            return CRYPT
        if d <= 5.6:
            return COBBLE
        if on_axis(a):
            return COBBLE
        if (dx, dy) in lamps:
            return LANTERN
        if ell(dx, dy, tombs):
            return CRYPT
        if horseshoe(dx, dy, copses, 4.0, 150):
            return CYPRESS if h01(a, b, 85) < 0.6 else DEAD_TREE
        if 8 < d < R - 3 and to_edge(dx, dy, tombs) > 5 and to_edge(dx, dy, copses) > 7.5 and grave_at(a, b, 81, 0):
            return TOMBSTONE
        return DEAD_GRASS

    def loose(a, b, d):
        r = h01(a, b, 83)
        if 8 < d < R - 3:
            return DEAD_TREE if r < 0.008 else ROCK if r < 0.014 else None
        return None

    m.paint(fn)
    m.scatter({DEAD_GRASS}, loose)
    m.open_up(DEAD_TREE)
    m.place_spawns([(11, 24), (19, 10), (25, 34), (30, 16)])
    return m


def graveyard():
    m = Arena("The Graveyard", "the_graveyard.json", 140, 54, "night")
    R = m.radius
    vaults = sym8([(29, 29)])                 # mausoleums: a walled court with a gate each side
    tombs = sym8([(13, 13)])
    copses = sym8([(14, 36)])
    lamps = sym8([(4, 15), (4, 21), (4, 33), (4, 45), (17, 17)])    # at the heads of rows of graves

    def fn(dx, dy, d):
        a, b = canon(dx, dy)
        if d > R:
            return graveyard_rim(a, b, d, R)
        # the chapel's ruin in the middle: a ring of wall with four doors five cells wide, a
        # lamp inside (it was nine cells across inside, its doors three)
        if (a, b) == (0, 0):
            return LANTERN
        if d <= 6.2:
            return COBBLE
        if d <= 7.6:
            return COBBLE if on_axis(a, 2) else CRYPT
        if d <= 10.6:
            return COBBLE
        if on_axis(a) or 26.0 <= d <= 27.8:
            return COBBLE
        if (dx, dy) in lamps:
            return LANTERN
        # a vault: a court eleven cells across, a gate five wide in each side (seven across, and
        # gates of one cell)
        q = square_ring(dx, dy, vaults, 5)
        if q == 1:
            return CRYPT
        if q == 2:
            return TOMBSTONE if (dx, dy) in vaults else COBBLE
        if ell(dx, dy, tombs):
            return CRYPT
        if horseshoe(dx, dy, copses, 4.0, 150):
            return CYPRESS if h01(a, b, 97) < 0.6 else DEAD_TREE
        if 12 < d < R - 4 and not (23.5 <= d <= 30.3) and to_edge(dx, dy, vaults) > 11 \
                and to_edge(dx, dy, tombs) > 5 and to_edge(dx, dy, copses) > 7.5 and grave_at(a, b, 91, 0):
            return TOMBSTONE
        return DEAD_GRASS

    def loose(a, b, d):
        r = h01(a, b, 93)
        if 12 < d < R - 3 and not (24.5 <= d <= 29.3):
            return DEAD_TREE if r < 0.008 else ROCK if r < 0.014 else BUSH if r < 0.018 else None
        return None

    m.paint(fn)
    m.scatter({DEAD_GRASS}, loose)
    m.open_up(DEAD_TREE)
    m.place_spawns([(16, 30), (20, 12), (34, 20), (41, 30), (47, 12)])
    return m


def necropolis():
    m = Arena("The Necropolis", "the_necropolis.json", 240, 100, "night")
    R = m.radius
    BLOCK = 26      # avenues every 26 cells, both ways: a city of the dead
    HALF = BLOCK // 2
    # what each block holds, by which block it is counted from the middle: 1 a mausoleum, 2 a
    # dead wood, 3 a plot inside broken walls. Any other, and any the cemetery's wall cuts
    # through, is rows of graves.
    KINDS = {(0, 0): 1, (0, 1): 2, (1, 1): 3, (1, 2): 1, (0, 3): 3}

    def fn(dx, dy, d):
        a, b = canon(dx, dy)
        if d > R:
            return graveyard_rim(a, b, d, R)
        # the great tomb in the middle of its square, a lamp at each corner of it
        if abs(dx) <= 2 and abs(dy) <= 2:
            return CRYPT
        if d <= 9.5:
            return LANTERN if (a, b) == (5, 5) else COBBLE
        # the avenues
        lx, ly = abs(dx) % BLOCK, abs(dy) % BLOCK
        cu, cv = lx - HALF, ly - HALF             # from the middle of the block: -13..12
        if abs(cu) >= HALF - 1 or abs(cv) >= HALF - 1:
            return COBBLE
        ca, cb = (abs(cu), abs(cv)) if abs(cu) <= abs(cv) else (abs(cv), abs(cu))
        boulevard = COBBLE if 62.0 <= d <= 64.0 else DEAD_GRASS
        if d > R - 3.5:
            return boulevard
        # a lamp at each corner of a block: four round every crossing, five cells between two
        # across an avenue (they stood two cells off the avenue's middle, three between them)
        if (ca, cb) == (10, 10):
            return LANTERN
        ci, cj = abs(dx) // BLOCK, abs(dy) // BLOCK
        ci, cj = (ci, cj) if ci <= cj else (cj, ci)
        whole = math.hypot(ci * BLOCK + HALF + 10, cj * BLOCK + HALF + 10) < R - 3.5
        kind = KINDS.get((ci, cj), 0) if whole else 0
        if kind == 1:
            # a mausoleum: a court fifteen cells across, a gate five wide in each side, the tomb
            # in the middle with five cells all round it, graves along the outside of its walls
            if cb == 7:
                return boulevard if ca <= 2 else CRYPT
            if cb < 7:
                return CRYPT if cb <= 1 else COBBLE
            if cb == 8 and 4 <= ca <= 6:
                return TOMBSTONE
        elif kind == 2:
            # a dead wood round a clearing thirteen cells across, a way in on each side, a lamp
            # in the middle
            e = math.hypot(cu, cv)
            if e <= 0.5:
                return LANTERN
            if 6.0 < e <= 9.6 and ca > 2:
                return DEAD_TREE if h01(a, b, 104) < 0.4 else CYPRESS
        elif kind == 3:
            # a plot inside broken walls: a corner of wall at each corner of it, graves in four
            # short rows out from the middle, five cells clear between a row and a corner
            if cb == 9 and ca >= 6:
                return CRYPT
            if ca == 0 and 3 <= cb <= 5:
                return TOMBSTONE
        else:
            # rows of graves, five cells between any two
            if ca % 6 == 3 and cb % 6 == 3 and d < R - 7:
                return TOMBSTONE if h01(a, b, 105) < 0.85 else DEAD_TREE
        return boulevard

    def loose(a, b, d):
        r = h01(a, b, 107)
        if 12 < d < R - 4:
            return ROCK if r < 0.004 else BUSH if r < 0.008 else None
        return None

    m.paint(fn)
    m.scatter({DEAD_GRASS}, loose)
    m.open_up(DEAD_TREE)
    m.place_spawns([(19, 22.5), (36, 12), (50, 33), (58, 10), (76, 22.5), (84, 38), (92, 10)])
    return m


# ---------------------------------------------------------------------------
# The desert: the Tomb, the Oasis, the Dunes
# ---------------------------------------------------------------------------

def desert_ground(a, b, salt):
    """Deep sand, pale sand blown into drifts across it."""
    return SAND if noise(a, b, 5.0, salt + 1) > 0.74 else DUNE


def desert_loose(a, b, salt):
    r = h01(a, b, salt)
    return CACTUS if r < 0.008 else ROCK if r < 0.013 else BONES if r < 0.017 else None


def pyramid(dx, dy, half):
    """The tomb in the middle: a square of wall `half` cells from its middle to its side (every
    block is one height, so it is a mastaba, not a pyramid with steps)."""
    return abs(dx) <= half and abs(dy) <= half


def tomb():
    m = Arena("The Tomb", "the_tomb.json", 90, 34, "desert")
    R = m.radius
    ruins = sym8([(13, 13)])
    walls = sym8([(9, 24)])
    obelisks = sym8([(3, 11), (3, 19), (3, 27)])
    bones = sym8([(20, 20)])

    def fn(dx, dy, d):
        a, b = canon(dx, dy)
        if d > R:
            return MESA
        # the tomb in the middle of a paved court, four ways lined with obelisks
        if pyramid(dx, dy, 2):
            return WALL
        if d <= 6.6:
            return STONE
        if on_axis(a):
            return STONE
        if (dx, dy) in obelisks:
            return OBELISK
        if ell(dx, dy, ruins):
            return WALL
        if bar(dx, dy, walls, 1.6, 0.5):
            return WALL
        if near_any(dx, dy, bones, 0.5):
            return BONES
        if 8 < d < R - 2 and a > 3:
            return desert_ground(a, b, 111)
        return DUNE

    m.paint(fn)
    m.scatter({DUNE, SAND}, lambda a, b, d: desert_loose(a, b, 111) if 8 < d < R - 3 and a > 3 else None)
    m.open_up(ROCK)
    m.place_spawns([(11, 28), (19, 12), (26, 36), (30, 18)])
    return m


def oasis():
    m = Arena("The Oasis", "the_oasis.json", 140, 54, "desert")
    R = m.radius
    wells = sym8([(29, 29)])              # lesser pools on the diagonals
    courts = sym8([(0, 34)])              # ruined courts on the axes
    outcrops = sym8([(17, 43)])
    palms = sym8([(13, 21)])
    obelisks = sym8([(3, 17), (3, 24), (3, 45)])

    def fn(dx, dy, d):
        a, b = canon(dx, dy)
        if d > R:
            return MESA
        # the oasis: a pool, palms standing at the water on the diagonals, grass round it, a
        # beach of pale sand. (The palms stood a cell back from the water all the way round.)
        if d <= 5.6:
            return WATER
        if d <= 7.0 and bearing(a, b) > 28:
            return PALM
        if d <= 10.4:
            return GRASS
        if d <= 13.0:
            return SAND
        # a ruined court on each axis: a paved floor eleven cells across, a gate five wide in
        # each wall (nine across, gates of three), an obelisk in the middle
        q = square_ring(dx, dy, courts, 5)
        if q == 1:
            return WALL
        if q == 2:
            return OBELISK if (dx, dy) in courts else STONE
        if on_axis(a):
            return STONE
        if (dx, dy) in obelisks:
            return OBELISK
        e = to_edge(dx, dy, wells)
        if e <= 2.6:
            return WATER
        if ring_cell(dx, dy, wells, 3.2, 0.6, gaps=(0, 180), gap_w=96):
            return PALM
        if e <= 5.0:
            return GRASS
        if e <= 6.6:
            return SAND
        k = knot(dx, dy, outcrops)
        if k:
            return MESA if k == 1 else DUNE
        if horseshoe(dx, dy, palms, 4.0, 150):
            return PALM if h01(a, b, 123) < 0.7 else CACTUS
        if 14 < d < R - 2 and a > 3:
            return desert_ground(a, b, 125)
        return DUNE

    m.paint(fn)
    m.scatter({DUNE, SAND}, lambda a, b, d: desert_loose(a, b, 125) if 15 < d < R - 3 and a > 3 else None)
    m.open_up(PALM)
    m.place_spawns([(18, 36), (22, 10), (37, 20), (44, 36), (48, 10)])
    return m


def dunes():
    m = Arena("The Dunes", "the_dunes.json", 240, 100, "desert")
    R = m.radius
    wells = sym8([(44, 44), (0, 80)])
    courts = sym8([(0, 46), (24, 62)])
    ruins = sym8([(18, 18), (12, 30), (50, 76)])
    palms = sym8([(16, 46), (34, 84)])
    obelisks = sym8([(3, 20), (3, 30), (3, 58), (3, 68), (3, 92), (20, 26)])

    def fn(dx, dy, d):
        a, b = canon(dx, dy)
        if d > R:
            return MESA
        # the great tomb: a square of wall nine cells across, in the middle of a paved square, four
        # long ways out
        if pyramid(dx, dy, 4):
            return WALL
        if d <= 12.5:
            return STONE
        if d <= 14.5:
            return SAND
        q = square_ring(dx, dy, courts, 5)
        if q == 1:
            return WALL
        if q == 2:
            return OBELISK if (dx, dy) in courts else STONE
        if on_axis(a):
            return STONE
        if (dx, dy) in obelisks:
            return OBELISK
        # an oasis: a pool, palms at the water on two sides of it, grass, a beach
        e = to_edge(dx, dy, wells)
        if e <= 4.0:
            return WATER
        if ring_cell(dx, dy, wells, 4.7, 0.65, gaps=(0, 180), gap_w=96):
            return PALM
        if e <= 6.6:
            return GRASS
        if e <= 8.6:
            return SAND
        if ell(dx, dy, ruins):
            return WALL
        if horseshoe(dx, dy, palms, 4.0, 150):
            return PALM if h01(a, b, 133) < 0.6 else CACTUS
        # a dry river bed winding round the desert, and the mesas: red rock standing in
        # clumps, far enough from everything built that nothing is walled in
        if 16 < d < R - 3 and a > 4:
            built = min(to_edge(dx, dy, courts), to_edge(dx, dy, wells))
            if built > 16 and 22 < d < R - 8 and noise(a, b, 8.0, 135) > 0.8:
                return MESA
            if abs(d - (60 + 5 * math.sin(math.atan2(a, b) * 8))) <= 1.6:
                return SAND
            return desert_ground(a, b, 137)
        return DUNE

    m.paint(fn)
    m.scatter({DUNE, SAND}, lambda a, b, d: desert_loose(a, b, 137) if 16 < d < R - 4 and a > 4 else None)
    m.open_up(MESA)
    m.place_spawns([(20, 14), (34, 30), (50, 14), (62, 36), (74, 22.5), (86, 10), (90, 33)])
    return m


# ---------------------------------------------------------------------------
# The bright maps at the Colony's size: the Wildwood, the Archipelago, the Tundra
# ---------------------------------------------------------------------------

def wildwood():
    m = Arena("The Wildwood", "the_wildwood.json", 240, 100, "sky")
    R = m.radius
    rings = sym8([(16, 16), (22, 56), (40, 74), (20, 86)])      # fairy rings
    caps = backs(rings, 5.0)
    shrooms = sym8([(5, 40), (25, 31), (6, 71)])
    outcrops = sym8([(26, 26), (14, 34), (34, 64), (60, 66)])
    hedges = sym8([(11, 24), (32, 46), (10, 91), (52, 78)])
    henges = sym8([(0, 62), (50, 50)])                           # rings of old stones

    def fn(dx, dy, d):
        a, b = canon(dx, dy)
        if d > R + 1.2:
            return TREE
        if d > R:
            return TREE if h01(a, b, 11) < 0.3 else BUSH
        # the glade in the middle: a pond, flowers round it, a ring of path
        if d <= 5.4:
            return WATER
        if d <= 7.6:
            return FLOWERS
        if 8.6 <= d <= 10.2:
            return PATH
        # a river round the wood, bridged on the axes and the diagonals: seven planks wide (the
        # bridges were three, and on the diagonals a staircase only its middle could be walked)
        if 43.0 <= d <= 46.4:
            return PLANKS if (on_axis(a, 3) or on_diagonal(a, b, 4)) else WATER
        # the roads: four spokes, a ring inside the river and one far outside it
        if (on_axis(a) and d <= R - 1) or 30.0 <= d <= 31.6 or 76.0 <= d <= 77.6:
            return PATH
        # rings of old stones on a floor of cobbles: stones in twos, a gap of four cells between
        # two and a way in seven wide on the side of the middle and the side away from it
        e = to_edge(dx, dy, henges)
        if e <= 9.6:
            if ring_cell(dx, dy, henges, 8.0, 0.6, gaps=(0, 180), gap_w=52) and \
                    ring_cell(dx, dy, henges, 8.0, 0.6, gaps=(60, 120, -60, -120), gap_w=30):
                return RUINS
            return COBBLE if e <= 5.4 else GRASS
        if near_any(dx, dy, caps, 1.0):
            return MUSHROOM
        if horseshoe(dx, dy, rings, 5.0, 130):
            return TREE if h01(a, b, 21) < 0.6 else BUSH
        if near_any(dx, dy, shrooms, 0.9):
            return MUSHROOM
        k = knot(dx, dy, outcrops)
        if k:
            return CLIFF if k == 1 else GRASS
        if bar(dx, dy, hedges, 1.6, 0.5):
            return BUSH
        if 13 < d < R - 3 and a > 4 and not (39.5 <= d <= 50) and b - a > 6:
            # thickets: the wood closing in, in solid clumps to go round (they were trees a cell
            # or two apart, to thread between)
            if noise(a, b, 7.0, 141) > 0.82:
                return TREE if h01(a, b, 145) < 0.7 else BUSH
        if noise(a, b, 4.0, 41) > 0.78:
            return FLOWERS
        return GRASS

    def loose(a, b, d):
        r = h01(a, b, 31)
        if 12 < d < R - 4 and not (40 <= d <= 49.4):
            return ROCK if r < 0.008 else BUSH if r < 0.015 else TREE if r < 0.019 else None
        return None

    m.paint(fn)
    m.scatter({GRASS, FLOWERS}, loose)
    m.open_up(TREE)
    m.place_spawns([(20, 30), (36, 14), (52, 30), (60, 12), (68, 36), (86, 22.5), (92, 8)])
    return m


def archipelago():
    m = Arena("The Archipelago", "the_archipelago.json", 240, 100, "sea")
    R = m.radius
    inner = sym8([(0, 55)])           # islands on the axes, r 21
    diag = sym8([(39, 39)])           # and on the diagonals, r 19
    outer = sym8([(34, 80)])          # eight far islands, r 14
    palms = sym8([(10, 15), (11, 64), (46, 46), (33, 83)])       # none on an axis: the walks run there
    pools = sym8([(9, 50)])
    columns = [(round(11.5 * math.cos(math.radians(22.5 + 45 * k))), round(11.5 * math.sin(math.radians(22.5 + 45 * k))))
               for k in range(8)]

    def shore(dx, dy, d, a, b):
        """How far inside the nearest island's shore a cell is (negative: in the sea). The
        shores wander a little, so no island is a circle."""
        wob = (noise(a, b, 4.0, 151) - 0.5) * 3.0
        return max(26.0 - d, 21.0 - to_edge(dx, dy, inner), 19.0 - to_edge(dx, dy, diag),
                   14.0 - to_edge(dx, dy, outer)) + wob

    def fn(dx, dy, d):
        a, b = canon(dx, dy)
        if d > R + 5:
            return DEEP
        if d > R:
            return WATER
        s = shore(dx, dy, d, a, b)
        # the middle island's pool, with a deck five planks wide round it
        if d <= 2.6:
            return WATER
        if d <= 7.8:
            return PLANKS
        # boardwalks: out along the axes and the diagonals, and round the two rings of islands.
        # Seven planks wide, with room to step aside from a shot (they were three, and two round
        # the rings, where the curve left one cell to walk on)
        walk = (on_axis(a, 3) and d <= 91) or (on_diagonal(a, b, 4) and d <= 58) \
            or abs(d - 55.0) <= 3.4 or abs(d - 87.0) <= 3.4
        if s <= 0:
            return PLANKS if walk else WATER
        if walk and s <= 5:
            return PLANKS
        if (dx, dy) in columns:
            return RUINS
        e = to_edge(dx, dy, pools)
        if e <= 2.4:
            return WATER
        if e <= 4.0:
            return SAND
        if horseshoe(dx, dy, palms, 4.0, 150, 0.75):
            return PALM if h01(a, b, 7) < 0.8 else ROCK
        if s <= 4.0:
            # the beach, coral and rocks at the water's edge
            r = h01(a, b, 13)
            if s <= 1.0 and r < 0.07:
                return CORAL if r < 0.035 else ROCK
            return SAND
        if noise(a, b, 5.0, 17) > 0.77:
            return SAND
        return GRASS

    def loose(a, b, d):
        r = h01(a, b, 13)
        return ROCK if r < 0.008 else PALM if r < 0.016 else BUSH if r < 0.020 else None

    m.paint(fn)
    m.scatter({GRASS, SAND}, loose)
    m.open_up(ROCK)
    m.place_spawns([(14, 30), (46, 12), (54, 10), (52, 40), (60, 38), (86, 20), (88, 26)])
    return m


def tundra():
    m = Arena("The Tundra", "the_tundra.json", 240, 100, "snow")
    R = m.radius
    blocks = sym8([(20, 20), (30, 54), (58, 66)])
    groves = sym8([(0, 27), (22, 44), (0, 86), (44, 78), (16, 72)])
    forts = sym8([(12, 32), (26, 68), (10, 50), (40, 90)])
    springs = sym8([(36, 36)])
    snowmen = [(round(15.5 * math.cos(math.radians(22.5 + 45 * k))), round(15.5 * math.sin(math.radians(22.5 + 45 * k))))
               for k in range(8)]
    built = blocks + groves + forts + springs

    def fn(dx, dy, d):
        a, b = canon(dx, dy)
        if d > R:
            return PINE
        # the frozen lake, a fir on an island of snow in the middle of it
        if (a, b) == (0, 0):
            return PINE
        if d <= 2.4:
            return SNOW
        if d <= 12.5:
            return ICE
        if (dx, dy) in snowmen:
            return SNOWMAN
        # a frozen river round the tundra: ice all the way, anyone can cross it anywhere
        river = abs(d - (60 + 3.0 * math.sin(math.atan2(a, b) * 8))) <= 2.0
        # hot springs on the diagonals: open water, snow melted back to the earth round them
        e = to_edge(dx, dy, springs)
        if e <= 3.0:
            return WATER
        if e <= 4.4:
            return DIRT
        if ell(dx, dy, blocks):
            return ICE_BLOCK
        if horseshoe(dx, dy, groves, 4.5, 150, 0.75):
            return PINE
        if bar(dx, dy, forts, 1.6, 0.5):
            return ICE_BLOCK
        if river:
            return ICE
        if d > 24 and off_the_ways(a, b, d, R) and abs(d - 60) > 9 and to_edge(dx, dy, built) > 10:
            # crags: rock standing out of the snow in clumps, well clear of the river
            if noise(a, b, 8.0, 161) > 0.74:
                return MOUNTAIN
            # pine woods, in solid clumps (they were pines a cell or two apart)
            if noise(a, b, 7.0, 163) > 0.76:
                return PINE
        if noise(a, b, 3.5, 29) > 0.82:
            return ICE
        return SNOW

    def loose(a, b, d):
        r = h01(a, b, 23)
        if 17 < d < R - 4:
            return ROCK if r < 0.008 else PINE if r < 0.015 else SNOWMAN if r < 0.019 else None
        return None

    m.paint(fn)
    m.scatter({SNOW, ICE}, loose)
    m.open_up(PINE)
    m.place_spawns([(20, 14), (34, 30), (46, 10), (52, 22.5), (72, 36), (80, 12), (92, 24)])
    return m


MAPS = [meadow, lagoon, snowglobe, crucible, caldera, cinderlands, crypt, graveyard, necropolis,
        tomb, oasis, dunes, wildwood, archipelago, tundra]


def preview(m, out):
    from PIL import Image
    colours = {GRASS: (128, 206, 80), WATER: (74, 186, 240), SAND: (250, 231, 176), DEEP: (46, 128, 214),
               SNOW: (242, 247, 255), ICE: (192, 234, 252), PATH: (230, 198, 140), FLOWERS: (250, 170, 200),
               TREE: (60, 140, 60), BUSH: (90, 170, 80), ROCK: (140, 136, 130), MUSHROOM: (255, 146, 52),
               PALM: (40, 150, 70), PINE: (40, 110, 80), SNOWMAN: (255, 90, 90), PLANKS: (200, 150, 96),
               CLIFF: (150, 100, 60), CORAL: (255, 110, 140), RUINS: (225, 220, 205), ICE_BLOCK: (120, 190, 240),
               LAVA: (255, 128, 40), ASH: (140, 134, 134), BASALT: (60, 56, 80), OBSIDIAN: (120, 90, 190),
               CINDER: (150, 112, 108), CHARRED_TREE: (40, 30, 36), MAGMA_ROCK: (255, 200, 90), MOUNTAIN: (130, 124, 130),
               DEAD_GRASS: (104, 134, 122), TOMBSTONE: (220, 220, 240), DEAD_TREE: (70, 60, 84), CRYPT: (60, 58, 80),
               LANTERN: (255, 214, 110), COBBLE: (182, 180, 192), DIRT: (192, 142, 94), STONE: (214, 208, 196),
               WALL: (150, 130, 100), DUNE: (238, 196, 120), CACTUS: (60, 150, 70), OBELISK: (170, 120, 60),
               MESA: (190, 100, 60), BONES: (255, 255, 255), CYPRESS: (40, 84, 80)}
    img = Image.new("RGB", (m.size, m.size))
    px = img.load()
    for y in range(m.size):
        for x in range(m.size):
            px[x, y] = colours.get(m.grid[y][x], (255, 0, 255))
    for (x, y) in m.spawns:
        px[x, y] = (0, 0, 0)
    img = img.resize((m.size * 5, m.size * 5), Image.NEAREST)
    img.save(os.path.join(out, m.filename.replace(".json", ".png")))


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--check", action="store_true", help="check the maps, write nothing")
    ap.add_argument("--preview", help="also write a top-down picture of each map here")
    args = ap.parse_args()
    failed = False
    for make in MAPS:
        m = make()
        problems = m.check()
        print(f"{m.filename}: {m.stats()}")
        for p in problems:
            print(f"  PROBLEM: {p}")
        failed |= bool(problems)
        if args.preview:
            os.makedirs(args.preview, exist_ok=True)
            preview(m, args.preview)
            # and where it is tight, if anywhere: scripts/map_audit.py's picture of it
            map_audit.Audit(m.grid, m.spawns).picture(os.path.join(args.preview, m.filename.replace(".json", "_audit.png")))
        if not args.check and not problems:
            path = os.path.join(ROOT, "worlds", m.filename)
            with open(path, "w") as f:
                json.dump(m.to_json(), f, indent=2)
                f.write("\n")
    if failed:
        sys.exit(1)


if __name__ == "__main__":
    main()
