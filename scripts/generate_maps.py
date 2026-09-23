#!/usr/bin/env python3
"""Generate the bright maps: worlds/the_meadow.json, the_lagoon.json, the_snowglobe.json.

    python3 scripts/generate_maps.py            # writes the three maps
    python3 scripts/generate_maps.py --check    # only checks them, writes nothing
    python3 scripts/generate_maps.py --preview /tmp/maps   # also a top-down picture of each

No dependencies; --preview needs Pillow.

Every map in the game is the same shape, because it plays well: a round arena in the middle
of a square world, closed off by terrain that nobody can cross, with cover scattered through it
in rings, arcs and blocks. These three keep that shape and change what it is made of:

  The Meadow     grass and flowers, dirt paths like the spokes of a wheel round a pond,
                 fairy rings of trees, giant mushrooms, a forest all round it.
  The Lagoon     an island: a sandy beach round a grassy middle, boardwalks out from a
                 pool with a bridge across it, palm groves, rock pools, ruins; sea all round.
  The Snowglobe  snow, a frozen pond with a fir in the middle and snowmen round it,
                 ice blocks and pine groves, a pine forest all round it.

Each map is symmetric under all eight of the square's symmetries: every decision about a cell
is made from its offset to the centre with the signs dropped and the axes sorted. That is what
makes a Teams match fair (TeamDivider splits the world down its middle column, which is the
centre column here) and a free-for-all fair from every spawn.

Checked before writing, the same way WorldMapsTest checks every registered map: spawn points on
open ground, none on the divider's column, and all open ground one region.
"""

import argparse
import json
import math
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# Tile ids (Tile.scala)
GRASS, WATER, SAND, STONE, WALL, TREE, PATH, DEEP, SNOW, ICE = 0, 1, 2, 3, 4, 5, 6, 7, 8, 9
FLOWERS, DIRT, COBBLE, CLIFF = 20, 21, 22, 29
CORAL, RUINS, MOSS = 25, 26, 27
BUSH, ROCK, MUSHROOM, PALM, PINE, SNOWMAN, PLANKS, ICE_BLOCK = 34, 35, 36, 37, 38, 39, 40, 41

WALKABLE = {0, 2, 3, 6, 8, 9, 13, 14, 16, 20, 21, 22, 23, 27, 30, 33, 40}


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
        ground on all four sides, and then mirrored into all eight eighths. None ends up on
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
                raise ValueError(f"{self.filename}: no open cell near spawn slot ({r}, {ang})")
            a, b = best[1]
            for (x, y) in sym8([(a, b)]):
                p = (self.c + x, self.c + y)
                if p not in pts:
                    pts.append(p)
        self.spawns = pts

    def _open_around(self, x, y):
        return all(self.grid[y + oy][x + ox] in WALKABLE for (ox, oy) in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)))

    # -- checks ----------------------------------------------------------------

    def check(self):
        g, n, c = self.grid, self.size, self.c
        problems = []
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
        return problems

    def stats(self):
        g, c, r = self.grid, self.c, self.radius
        inside = [(x, y) for y in range(self.size) for x in range(self.size) if math.hypot(x - c, y - c) <= r]
        cover = sum(1 for (x, y) in inside if g[y][x] not in WALKABLE)
        return f"{self.size}x{self.size}, arena r={r}, {len(inside)} cells inside, " \
               f"{100.0 * cover / len(inside):.1f}% cover, {len(self.spawns)} spawns"

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
# The Meadow
# ---------------------------------------------------------------------------

def meadow():
    m = Arena("The Meadow", "the_meadow.json", 120, 46, "sky")
    R = m.radius
    groves = sym8([(15, 15)])                 # fairy rings of trees, between the spokes
    shrooms = sym8([(5, 36), (-5, 36)])       # giant mushrooms beside each spoke, past the ring road
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
            return BUSH if (a == b and d > 5.4) else FLOWERS
        # the paths: a ring round the pond, four spokes, and a ring road
        if 7.2 <= d <= 8.8 or (a <= 1 and d <= R - 1) or 29.0 <= d <= 30.6:
            return PATH
        # fairy rings of trees, open toward the pond, the forest and both sides
        if ring_cell(dx, dy, groves, 5.0, 0.72, gaps=(0, 90, 180, -90), gap_w=34):
            return TREE if h01(a, b, 21) < 0.6 else BUSH
        if near_any(dx, dy, groves, 1.2):
            return MUSHROOM
        if near_any(dx, dy, shrooms, 0.9):
            return MUSHROOM
        # outcrops of cliff, three by three with one corner broken off
        for (ox, oy) in outcrops:
            if abs(dx - ox) <= 1 and abs(dy - oy) <= 1:
                inner = (abs(dx) < abs(ox) and abs(dy) < abs(oy))
                return GRASS if inner and abs(dx - ox) == 1 and abs(dy - oy) == 1 else CLIFF
        # hedges: three bushes in a row, square to the ring road
        for (hx, hy) in hedges:
            if math.hypot(dx - hx, dy - hy) <= 1.6 and abs((dx - hx) * hx + (dy - hy) * hy) < 0.8 * math.hypot(hx, hy):
                return BUSH
        # scattered cover, kept off the paths' edges
        r = h01(a, b, 31)
        if 10 < d < R - 2.5:
            if r < 0.006:
                return ROCK
            if r < 0.012:
                return BUSH
            if r < 0.015:
                return TREE
        # flower patches
        if noise(a, b, 4.0, 41) > 0.78:
            return FLOWERS
        return GRASS

    m.paint(fn)
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
    ruins = [(round(10.5 * math.cos(math.radians(22.5 + 45 * k))), round(10.5 * math.sin(math.radians(22.5 + 45 * k))))
             for k in range(8)]

    def fn(dx, dy, d):
        a, b = canon(dx, dy)
        if d > R + 5:
            return DEEP
        if d > R:
            return WATER
        # the pool in the middle, a bridge across it both ways, a deck round it
        if d <= 4.6:
            return PLANKS if a <= 0 else WATER
        if d <= 6.8:
            return PLANKS
        # boardwalks out to the beach
        if a <= 1 and d <= beach + 3:
            return PLANKS
        # broken columns round the deck
        if (dx, dy) in ruins:
            return RUINS
        # rock pools, rimmed with coral
        for (px, py) in rockpools:
            e = math.hypot(dx - px, dy - py)
            if e <= 2.7:
                return WATER
            if e <= 3.9:
                return SAND
            if e <= 4.9:
                # coral round the outside of a sand rim, so none of it can wall sand in
                return CORAL if h01(a, b, 5) < 0.28 else SAND
        # palm groves: a loose ring, open toward the pool and the sea
        if ring_cell(dx, dy, palms, 4.5, 0.75, gaps=(0, 180, 90, -90), gap_w=30):
            return PALM if h01(a, b, 7) < 0.8 else ROCK
        if near_any(dx, dy, palms, 0.6):
            return PALM
        r = h01(a, b, 13)
        if d > beach:
            # the beach: sand, with the odd rock and coral near the water
            if d > R - 2 and r < 0.03:
                return CORAL if r < 0.012 else ROCK
            if r < 0.006:
                return PALM
            return SAND
        if 9 < d < beach - 1:
            if r < 0.006:
                return ROCK
            if r < 0.011:
                return PALM
            if r < 0.014:
                return BUSH
        # sandy patches through the grass
        if noise(a, b, 5.0, 17) > 0.77:
            return SAND
        return GRASS

    m.paint(fn)
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
    forts = sym8([(11, 27)])   # snow forts: short curved walls of ice, facing the middle
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
        for (bx, by) in blocks:
            ex, ey = dx - bx, dy - by
            sx = 1 if bx > 0 else -1
            sy = 1 if by > 0 else -1
            if (ex, ey) in ((0, 0), (sx, 0), (0, sy), (-sx, -sy)):
                return ICE_BLOCK
        # pine groves: arcs opening toward the pond
        if ring_cell(dx, dy, groves, 4.5, 0.75, gaps=(180, 120, -120), gap_w=36):
            return PINE
        # snow forts: three blocks of ice square to the middle
        for (fx, fy) in forts:
            e = math.hypot(fx, fy)
            along = abs(((dx - fx) * -fy + (dy - fy) * fx) / e)
            across = abs(((dx - fx) * fx + (dy - fy) * fy) / e)
            if along <= 1.6 and across <= 0.5:
                return ICE_BLOCK
        r = h01(a, b, 23)
        if 10 < d < R - 2:
            if r < 0.007:
                return ROCK
            if r < 0.013:
                return PINE
            if r < 0.016:
                return SNOWMAN
        # icy patches
        if noise(a, b, 3.5, 29) > 0.82:
            return ICE
        return SNOW

    m.paint(fn)
    m.place_spawns([(13, 30), (20, 12), (28, 34), (34, 15)])
    return m


MAPS = [meadow, lagoon, snowglobe]


def preview(m, out):
    from PIL import Image
    colours = {GRASS: (128, 206, 80), WATER: (74, 186, 240), SAND: (250, 231, 176), DEEP: (46, 128, 214),
               SNOW: (242, 247, 255), ICE: (192, 234, 252), PATH: (230, 198, 140), FLOWERS: (250, 170, 200),
               TREE: (60, 140, 60), BUSH: (90, 170, 80), ROCK: (140, 136, 130), MUSHROOM: (255, 146, 52),
               PALM: (40, 150, 70), PINE: (40, 110, 80), SNOWMAN: (255, 90, 90), PLANKS: (200, 150, 96),
               CLIFF: (150, 100, 60), CORAL: (255, 110, 140), RUINS: (225, 220, 205), ICE_BLOCK: (120, 190, 240)}
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
        if not args.check and not problems:
            path = os.path.join(ROOT, "worlds", m.filename)
            with open(path, "w") as f:
                json.dump(m.to_json(), f, indent=2)
                f.write("\n")
    if failed:
        sys.exit(1)


if __name__ == "__main__":
    main()
