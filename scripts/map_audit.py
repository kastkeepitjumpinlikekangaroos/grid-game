#!/usr/bin/env python3
"""Is there room to walk and to fight on every map?

    python3 scripts/map_audit.py                      # every map in worlds/, a line each
    python3 scripts/map_audit.py worlds/the_hive.json # only these
    python3 scripts/map_audit.py --out /tmp/audit     # also a picture of each, the tight ground marked
    python3 scripts/map_audit.py --strict             # exit 1 if any map breaks the rules below

No dependencies; --out needs Pillow.

A player stands on one cell, and one key walks a diagonal of the grid (W is up the screen, which
is (-1, -1) in the world); a diagonal step goes between two open cells only (WorldData.canStep),
and a blocked one slides along whichever side is open (LocalMovement). So a gap one cell wide in
a wall is slid straight past unless the player stops in front of it and holds the two keys that
walk an axis, and a path one cell wide is walked a step at a time. That is what this measures:

  the spine     the cells with open ground all round them (a 3x3 block: ROOM), joined to their
                neighbours on all eight sides. A step between two of them never meets a corner.
  roomy ground  everything within a step of the spine that can be walked to from the middle of
                the map: where a player walks without thinking about the walls.
  tight ground  the rest of the open ground: gaps and paths one or two cells wide, and whatever
                lies behind them. Counted apart: the cells with no 2x2 of open ground round them
                (one wide).
  lanes         the same with a 5x5 block (LANE): what can be reached without going through
                anything narrower than five cells, which is a bridge or a gate wide enough to
                step aside on. The audit lists what is left: a region reached only by something
                narrower is behind a choke. A lone prop (LONE cells of cover or fewer, touching
                no other) is not counted here: a walk goes round either side of a rock, and two
                rocks four cells apart are not a gate.

The rules (`problems`), which generate_maps.py checks before it writes a map and WorldMapsTest
checks for every registered map: no tight ground at all, and no ground off the lanes in a piece
bigger than an alcove (ALCOVE cells: a spawn pod with a door three wide, the corner behind a
rock), so that a long way, a bridge or a room of any size is five wide or has a way in that is.

    python3 scripts/map_audit.py --repair worlds/the_hive.json   # rewrite a map drawn by hand

--repair is for the four space arenas, which no script writes: a door one cell wide into a pod
becomes three, and five into a pod too big to be an alcove (`widen_necks`), and whatever is still
tight becomes the cover beside it (`fill_tight`: a dent in a wall is filled in, a strip between a
pod and the deck's edge goes to the edge). The generated maps are made to the rules by
generate_maps.py, which ends on the same `fill_tight`.
"""

import argparse
import glob
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# Tile.scala: the tiles a player can stand on
WALKABLE = {0, 2, 3, 6, 8, 9, 13, 14, 16, 20, 21, 22, 23, 27, 30, 33, 40, 42, 45, 50}
# Flat and uncrossable, but a shot flies over it (Tile.stopsProjectiles)
POOLS = {1, 7, 10}

ROOM = 3    # open ground this many cells across is roomy: a gap narrower is tight
LANE = 5    # a way between two parts of a map is this wide, or it is a choke
LONE = 2    # cover in a piece this small is a prop to walk round, not the side of a way
ALCOVE = 48 # ground off the lanes in a piece no bigger than this is an alcove, not somewhere cut off


def load(path):
    with open(path) as f:
        w = json.load(f)
    rows = None
    for layer in w["layers"]:
        if layer["type"] == "grid":
            rows = [[int(t) for t in row.split()] for row in layer["data"]]
    spawns = [(p["x"], p["y"]) for p in w.get("spawnPoints", [])]
    return rows, spawns


def save(path, rows):
    """Write a map's tiles back, everything else in the file as it was."""
    with open(path) as f:
        w = json.load(f)
    for layer in w["layers"]:
        if layer["type"] == "grid":
            layer["data"] = [" ".join(str(t) for t in row) for row in rows]
    with open(path, "w") as f:
        json.dump(w, f, indent=2)
        f.write("\n")


def open_blocks(walk, n):
    """The cells whose n x n block (n odd, the cell in its middle) is all open ground."""
    h, w = len(walk), len(walk[0])
    k = n // 2
    # a summed-area table of the blocked cells
    sat = [[0] * (w + 1) for _ in range(h + 1)]
    for y in range(h):
        row, above, run = sat[y + 1], sat[y], 0
        wy = walk[y]
        for x in range(w):
            run += 0 if wy[x] else 1
            row[x + 1] = above[x + 1] + run
    out = [[False] * w for _ in range(h)]
    for y in range(k, h - k):
        for x in range(k, w - k):
            if walk[y][x]:
                blocked = sat[y + k + 1][x + k + 1] - sat[y - k][x + k + 1] - sat[y + k + 1][x - k] + sat[y - k][x - k]
                out[y][x] = blocked == 0
    return out


def components(cells, diagonal=True):
    """The connected pieces of a set of cells, biggest first."""
    steps = ((1, 0), (-1, 0), (0, 1), (0, -1)) + (((1, 1), (1, -1), (-1, 1), (-1, -1)) if diagonal else ())
    left = set(cells)
    out = []
    while left:
        start = left.pop()
        piece, stack = [start], [start]
        while stack:
            x, y = stack.pop()
            for (ox, oy) in steps:
                p = (x + ox, y + oy)
                if p in left:
                    left.remove(p)
                    piece.append(p)
                    stack.append(p)
        out.append(piece)
    out.sort(key=len, reverse=True)
    return out


def reach(walk, n):
    """What a player keeping n cells of open ground round them can reach: the biggest joined-up
    piece of the n-spine, and every cell within its blocks. Returns (ground, spine pieces)."""
    h, w = len(walk), len(walk[0])
    spine = open_blocks(walk, n)
    pieces = components([(x, y) for y in range(h) for x in range(w) if spine[y][x]])
    ground = set()
    k = n // 2
    if pieces:
        for (x, y) in pieces[0]:
            for oy in range(-k, k + 1):
                for ox in range(-k, k + 1):
                    ground.add((x + ox, y + oy))
    return ground, pieces


def walkable(rows):
    return [[t in WALKABLE for t in row] for row in rows]


def narrow_cells(rows):
    """The open cells with no ROOM x ROOM block of open ground round them, anywhere on the map."""
    walk = walkable(rows)
    h, w = len(rows), len(rows[0])
    spine = open_blocks(walk, ROOM)
    k = ROOM // 2
    near = [[False] * w for _ in range(h)]
    for y in range(k, h - k):
        row = spine[y]
        for x in range(k, w - k):
            if row[x]:
                for yy in range(y - k, y + k + 1):
                    for xx in range(x - k, x + k + 1):
                        near[yy][xx] = True
    return [(x, y) for y in range(h) for x in range(w) if walk[y][x] and not near[y][x]]


def fill_tight(rows):
    """Open ground too narrow to walk becomes the cover beside it: the gap between two rocks one
    cell apart closes, a dent in a wall fills in, a strip of deck between a pod and the edge goes
    to the edge. Each cell takes the tile most of the blocked cells round it have (the lowest id
    of them on a tie), which is the same choice for a cell and its mirror image, so a symmetric
    map stays one. Repeated until there is none: filling a gap can leave the ground beside it
    narrow. Returns how many cells it filled. It does not join anything up: ground that was
    reached only through a gap is left cut off, for the map's checks to refuse."""
    filled = 0
    while True:
        narrow = narrow_cells(rows)
        if not narrow:
            return filled
        changes = {}
        for (x, y) in narrow:
            count = {}
            for oy in (-1, 0, 1):
                for ox in (-1, 0, 1):
                    yy, xx = y + oy, x + ox
                    if 0 <= yy < len(rows) and 0 <= xx < len(rows[0]) and rows[yy][xx] not in WALKABLE:
                        count[rows[yy][xx]] = count.get(rows[yy][xx], 0) + 1
            if count:
                changes[(x, y)] = min(count, key=lambda t: (-count[t], t))
        if not changes:
            return filled
        for (x, y), t in changes.items():
            rows[y][x] = t
        filled += len(changes)


def spine_pieces(rows, n):
    """The n-spine in joined-up pieces, biggest first, and what each reaches: every cell of its
    cells' n x n blocks."""
    walk = walkable(rows)
    h, w = len(rows), len(rows[0])
    spine = open_blocks(walk, n)
    pieces = components([(x, y) for y in range(h) for x in range(w) if spine[y][x]])
    k = n // 2
    reaches = []
    for piece in pieces:
        r = set()
        for (x, y) in piece:
            for oy in range(-k, k + 1):
                for ox in range(-k, k + 1):
                    r.add((x + ox, y + oy))
        reaches.append(r)
    return pieces, reaches


def widen_necks(rows, n, least):
    """Where two parts of a map with room in them (n x n of open ground) are joined only by
    something narrower, the cover either side of that neck is opened, a cell each side, until
    they are joined by n: a spawn pod's door one cell wide becomes three, then a big pod's three
    five. Only a part that reaches `least` cells or more is worth a door; a smaller one is left,
    to be filled in or to stay an alcove. Returns how many cells of cover it opened."""
    h, w = len(rows), len(rows[0])
    opened = 0
    for _ in range(8):
        pieces, reaches = spine_pieces(rows, n)
        if len(pieces) < 2:
            break
        owner = {}
        for i, r in enumerate(reaches):
            if i == 0 or len(r) >= least:
                for c in r:
                    owner.setdefault(c, i)
        loose = [(x, y) for y in range(h) for x in range(w)
                 if rows[y][x] in WALKABLE and not any((x, y) in r for r in reaches)]
        # only the walls of the part being joined on are opened: a block that stands in front of
        # a pod's door is not its wall
        walls = {}
        for piece in components([(x, y) for y in range(h) for x in range(w) if rows[y][x] not in WALKABLE]):
            near = set()
            for (x, y) in piece:
                for oy in (-1, 0, 1):
                    for ox in (-1, 0, 1):
                        i = owner.get((x + ox, y + oy))
                        if i:
                            near.add(i)
            for c in piece:
                walls[c] = near
        change = {}
        for group in components(loose):
            touching = set()
            for (x, y) in group:
                for oy in (-1, 0, 1):
                    for ox in (-1, 0, 1):
                        if (x + ox, y + oy) in owner:
                            touching.add(owner[(x + ox, y + oy)])
            if 0 not in touching or len(touching) < 2:
                continue
            tiles = [rows[y][x] for (x, y) in group]
            ground = max(set(tiles), key=lambda t: (tiles.count(t), -t))
            for (x, y) in group:
                for oy in (-1, 0, 1):
                    for ox in (-1, 0, 1):
                        yy, xx = y + oy, x + ox
                        if 0 < yy < h - 1 and 0 < xx < w - 1 and walls.get((xx, yy), set()) & touching:
                            change[(xx, yy)] = ground
        # two parts that touch with nothing loose between them and are still not joined: a
        # squeeze past two corners (a pod's mouth on a diagonal). The corners go.
        for i in range(1, len(reaches)):
            if len(reaches[i]) < least:
                continue
            for (x, y) in reaches[i]:
                if any(p in reaches[0] for p in ((x, y), (x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1))):
                    for oy in (-1, 0, 1):
                        for ox in (-1, 0, 1):
                            yy, xx = y + oy, x + ox
                            if 0 < yy < h - 1 and 0 < xx < w - 1 and i in walls.get((xx, yy), ()):
                                change[(xx, yy)] = rows[y][x]
        if not change:
            break
        for (x, y), t in change.items():
            rows[y][x] = t
        opened += len(change)
    return opened


def fill_stranded(rows, most):
    """Open ground a walk can't reach from the biggest piece of it, in pieces of `most` cells or
    fewer, becomes the cover round it. Returns how many cells it filled."""
    h, w = len(rows), len(rows[0])
    pieces = components([(x, y) for y in range(h) for x in range(w) if rows[y][x] in WALKABLE], diagonal=False)
    filled = 0
    for piece in pieces[1:]:
        if len(piece) > most:
            continue
        count = {}
        for (x, y) in piece:
            for (ox, oy) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                t = rows[y + oy][x + ox]
                if t not in WALKABLE:
                    count[t] = count.get(t, 0) + 1
        t = min(count, key=lambda t: (-count[t], t))
        for (x, y) in piece:
            rows[y][x] = t
        filled += len(piece)
    return filled


def fill_side_rooms(rows, most):
    """Ground with room in it that is still not roomy: joined to the rest only past two corners
    (a squeeze on a diagonal, which leaves no cell narrow), or not at all. In pieces of `most`
    cells or fewer it becomes the cover round it. Returns how many cells it filled."""
    walk = walkable(rows)
    h, w = len(rows), len(rows[0])
    roomy, _ = reach(walk, ROOM)
    apart = [(x, y) for y in range(h) for x in range(w) if walk[y][x] and (x, y) not in roomy]
    filled = 0
    for piece in components(apart, diagonal=False):
        if len(piece) > most:
            continue
        count = {}
        for (x, y) in piece:
            for oy in (-1, 0, 1):
                for ox in (-1, 0, 1):
                    t = rows[y + oy][x + ox]
                    if t not in WALKABLE:
                        count[t] = count.get(t, 0) + 1
        if not count:
            continue
        t = min(count, key=lambda t: (-count[t], t))
        for (x, y) in piece:
            rows[y][x] = t
        filled += len(piece)
    return filled


def repair(rows):
    """Bring a map drawn by hand to the rules: doors widened, then what is still tight filled.
    Returns (cells of cover opened, cells of ground filled)."""
    opened = widen_necks(rows, ROOM, 0)
    filled = fill_tight(rows) + fill_stranded(rows, 16)
    while True:
        more = fill_side_rooms(rows, 16)
        if not more:
            break
        filled += more + fill_tight(rows)
    # a room too big to be an alcove wants a way in five wide
    for _ in range(4):
        a = Audit(rows, [])
        if not a.cut_off:
            break
        more = widen_necks(rows, LANE, ALCOVE // 2)
        if not more:
            break
        opened += more
        filled += fill_tight(rows)
    return opened, filled


class Audit:
    def __init__(self, rows, spawns):
        self.rows, self.spawns = rows, spawns
        h, w = len(rows), len(rows[0])
        self.walk = walk = walkable(rows)
        self.open = {(x, y) for y in range(h) for x in range(w) if walk[y][x]}
        self.roomy, _ = reach(walk, ROOM)
        # the lanes are measured with the lone props taken away
        without = [list(row) for row in walk]
        for piece in components([(x, y) for y in range(h) for x in range(w) if not walk[y][x]]):
            if len(piece) <= LONE:
                for (x, y) in piece:
                    without[y][x] = True
        self.lanes, _ = reach(without, LANE)
        self.tight = self.open - self.roomy
        # one wide: no 2x2 of open ground holds the cell
        def in_square(x, y):
            for (ox, oy) in ((0, 0), (-1, 0), (0, -1), (-1, -1)):
                if all((x + ox + i, y + oy + j) in self.open for i in (0, 1) for j in (0, 1)):
                    return True
            return False
        self.one_wide = {p for p in self.tight if not in_square(*p)}
        # ground off the lanes, in pieces: a big piece is somewhere only a choke leads to
        self.off_lanes = components(self.open - self.lanes, diagonal=False)
        self.cut_off = [p for p in self.off_lanes if len(p) > ALCOVE]
        # open ground a walk can't reach from the roomy ground at all
        seen, stack = set(), []
        if self.roomy & self.open:
            start = min(self.roomy & self.open)
            seen.add(start)
            stack.append(start)
        while stack:
            x, y = stack.pop()
            for p in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
                if p in self.open and p not in seen:
                    seen.add(p)
                    stack.append(p)
        self.stranded = self.open - seen

    def problems(self):
        out = []
        if self.tight:
            eg = sorted(self.tight)[:4]
            out.append(f"{len(self.tight)} cells of tight ground (a gap or a path under {ROOM} cells wide), "
                       f"{len(self.one_wide)} of them one cell wide, e.g. {eg}")
        if self.stranded:
            out.append(f"{len(self.stranded)} open cells can't be walked to from the rest, e.g. {sorted(self.stranded)[:4]}")
        if self.cut_off:
            n = sum(len(p) for p in self.cut_off)
            eg = [min(p) for p in self.cut_off[:4]]
            out.append(f"{n} cells in {len(self.cut_off)} places are reached only by a way under {LANE} cells wide, e.g. {eg}")
        return out

    def line(self):
        n = len(self.open)
        off = sum(len(p) for p in self.off_lanes)
        return (f"{n} open, tight {len(self.tight)} ({100.0 * len(self.tight) / n:.2f}%, one wide {len(self.one_wide)}), "
                f"off the {LANE}-lanes {off} ({100.0 * off / n:.1f}%) of which behind a choke "
                f"{sum(len(p) for p in self.cut_off)} in {len(self.cut_off)} places")

    def picture(self, path, scale=5):
        from PIL import Image
        h, w = len(self.rows), len(self.rows[0])
        img = Image.new("RGB", (w, h))
        px = img.load()
        cut = {c for p in self.cut_off for c in p}
        for y in range(h):
            for x in range(w):
                t = self.rows[y][x]
                p = (x, y)
                if p in self.one_wide:
                    c = (255, 0, 0)
                elif p in self.tight:
                    c = (255, 150, 0)
                elif p in cut:
                    c = (255, 235, 60)
                elif p in self.open and p not in self.lanes:
                    c = (205, 225, 150)
                elif p in self.open:
                    c = (236, 236, 228)
                elif t in POOLS:
                    c = (70, 150, 220)
                else:
                    c = (52, 56, 66)
                px[x, y] = c
        for (x, y) in self.spawns:
            px[x, y] = (0, 160, 60)
        img = img.resize((w * scale, h * scale), Image.NEAREST)
        img.save(path)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("maps", nargs="*", help="map files (default: every map in worlds/)")
    ap.add_argument("--out", help="write a picture of each map here: red one wide, orange tight, "
                                  "yellow cut off from the lanes, pale green off the lanes in nooks")
    ap.add_argument("--strict", action="store_true", help="exit 1 if a map breaks the rules")
    ap.add_argument("--repair", action="store_true",
                    help="rewrite the maps named: doors one wide become three, tight ground becomes cover")
    args = ap.parse_args()
    if args.repair and not args.maps:
        ap.error("--repair rewrites the maps it is given: name them")
    paths = args.maps or sorted(glob.glob(os.path.join(ROOT, "worlds", "*.json")))
    failed = False
    for path in paths:
        rows, spawns = load(path)
        if args.repair:
            opened, filled = repair(rows)
            save(path, rows)
            print(f"{os.path.basename(path)}: opened {opened} cells of cover beside doors, filled {filled} cells of tight ground")
        a = Audit(rows, spawns)
        print(f"{os.path.basename(path)}: {a.line()}")
        for p in a.problems():
            print(f"  PROBLEM: {p}")
            failed = True
        if args.out:
            os.makedirs(args.out, exist_ok=True)
            a.picture(os.path.join(args.out, os.path.basename(path).replace(".json", ".png")))
    if failed and args.strict:
        sys.exit(1)


if __name__ == "__main__":
    main()
