#!/usr/bin/env python3
"""Contact sheets and little scenes for the terrain tileset.

The review loop for tile art, in the same spirit as sprite_gallery.py and the
projectile gallery: a tile is never seen alone. Ground is seen as a field of
itself, where any mark near an edge becomes a grid; a tree is seen in a forest
of itself; and everything is seen with a character standing in it for scale.

    python3 scripts/tile_gallery.py /tmp/tilegallery           # draws the atlas from generate_tiles.py
    python3 scripts/tile_gallery.py /tmp/tilegallery --atlas   # uses the committed sprites/tiles.png
    python3 scripts/tile_gallery.py /tmp/tilegallery --map worlds/the_meadow.json --at 60,60

Writes:
  sheet.png    every tile, its four rows side by side, labelled
  fields.png   each tile laid out as a patch in a field of grass
  scenes.png   the bright maps' biomes and the space maps' arena, composed
  map_*.png    with --map: a window of a real map around --at (default: its centre)

Scenes are composed the way GLGameRenderer draws the world -- ground, pools and
the ground under props first, then blocks and props back to front -- at atlas
resolution, which is 1.25x what a 1x screen shows and 0.6x a HiDPI one.
"""

import argparse
import json
import os
import sys

from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import generate_tiles as gt  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CW, CH = gt.CELL_W, gt.CELL_H
FORMS = {tid: form for (tid, name, form, fn) in gt.TILES}
NAMES = {tid: name for (tid, name, form, fn) in gt.TILES}
# A prop's own ground (Tile.ground in Tile.scala); grass unless listed
PROP_GROUND = {24: 3, 25: 2, 31: 21, 37: 2, 38: 8, 39: 8}


def _font(size):
    for path in ("/System/Library/Fonts/Supplemental/Arial Bold.ttf",
                 "/System/Library/Fonts/Helvetica.ttc",
                 "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"):
        if os.path.exists(path):
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def cell(atlas, tid, frame):
    return atlas.crop((tid * CW, frame * CH, tid * CW + CW, frame * CH + CH))


def ground_under(grid, x, y):
    """Tile.groundUnder: the prop's own ground if it is next to some, else the first
    walkable neighbour (south, east, west, north), else its own."""
    own = PROP_GROUND.get(grid[y][x], 0)
    first = None
    for (nx, ny) in ((x, y + 1), (x + 1, y), (x - 1, y), (x, y - 1)):
        if 0 <= ny < len(grid) and 0 <= nx < len(grid[0]):
            t = grid[ny][nx]
            if FORMS.get(t) == gt.GROUND:
                if t == own:
                    return own
                if first is None:
                    first = t
    return first if first is not None else own


def compose(atlas, grid, tick=0, sprites=()):
    """Draw a grid of tile ids ([row][col], row = world y) the way the game does."""
    h, w = len(grid), len(grid[0])
    width = (w + h) * 40 + CW
    height = (w + h) * 20 + CH + 20
    ox, oy = h * 40 + 20, CH - 10
    img = Image.new("RGBA", (width, height), (150, 200, 240, 255))

    def at(x, y):
        return ox + (x - y) * 40, oy + (x + y) * 20

    def put(tid, frame, x, y):
        sx, sy = at(x, y)
        c = cell(atlas, tid, frame)
        img.alpha_composite(c, (int(sx - 40), int(sy - (CH - 20))))

    for y in range(h):
        for x in range(w):
            t = grid[y][x]
            form = FORMS[t]
            variant = ((x * 7 + y * 13) & 0x7FFFFFFF) % 4
            if form == gt.GROUND:
                put(t, variant, x, y)
            elif form == gt.POOL:
                put(t, (tick + x * 7 + y * 13) % 4, x, y)
            elif form == gt.PROP:
                put(ground_under(grid, x, y), variant, x, y)
    for y in range(h):
        for x in range(w):
            t = grid[y][x]
            form = FORMS[t]
            if form == gt.BLOCK:
                put(t, (tick + x * 7 + y * 13) % 4, x, y)
            elif form == gt.PROP:
                put(t, ((x * 7 + y * 13) & 0x7FFFFFFF) % 4, x, y)
            for (sxw, syw, spr) in sprites:
                if (sxw, syw) == (x, y):
                    sx, sy = at(x, y)
                    img.alpha_composite(spr, (int(sx - spr.width / 2), int(sy - spr.height + 6)))
    return img


def character(name="wizard"):
    """One idle frame, scaled so it stands in the scene as it does in game (48 display
    px against a 40-px-wide cell, so 96 atlas px)."""
    sheet = Image.open(os.path.join(ROOT, "sprites", name + ".png")).convert("RGBA")
    return sheet.crop((0, 0, 128, 128)).resize((96, 96), Image.LANCZOS)


def sheet(atlas, out):
    tiles = sorted(FORMS)
    cols = 6
    font = _font(13)
    pad = 8
    tile_w = CW * 4 + pad * 5
    tile_h = CH + 26
    rows = (len(tiles) + cols - 1) // cols
    img = Image.new("RGBA", (tile_w * cols, tile_h * rows), (92, 96, 110, 255))
    d = ImageDraw.Draw(img)
    for i, tid in enumerate(tiles):
        x0, y0 = (i % cols) * tile_w, (i // cols) * tile_h
        for f in range(4):
            img.alpha_composite(cell(atlas, tid, f), (x0 + pad + f * (CW + pad), y0))
        d.text((x0 + pad, y0 + CH + 4), f"{tid} {NAMES[tid]} ({FORMS[tid]})", font=font, fill=(255, 255, 255))
    img.save(os.path.join(out, "sheet.png"))


def fields(atlas, out):
    """Each tile as a 5x5 patch (props and blocks a cluster, pools a pond) in grass."""
    tiles = sorted(FORMS)
    font = _font(14)
    patches = []
    for tid in tiles:
        g = [[0] * 7 for _ in range(7)]
        form = FORMS[tid]
        for y in range(1, 6):
            for x in range(1, 6):
                if form in (gt.GROUND, gt.POOL):
                    g[y][x] = tid
                elif (x, y) in ((2, 2), (3, 2), (2, 3), (4, 4), (3, 4), (4, 3)):
                    g[y][x] = tid
        if form == gt.PROP and PROP_GROUND.get(tid, 0) != 0:
            own = PROP_GROUND[tid]
            for y in range(7):
                for x in range(7):
                    if g[y][x] == 0:
                        g[y][x] = own
        p = compose(atlas, g)
        ImageDraw.Draw(p).text((10, 8), f"{tid} {NAMES[tid]}", font=font, fill=(20, 30, 40))
        patches.append(p.resize((p.width // 2, p.height // 2), Image.LANCZOS))
    cols = 6
    pw, ph = patches[0].size
    img = Image.new("RGBA", (pw * cols, ph * ((len(patches) + cols - 1) // cols)), (150, 200, 240, 255))
    for i, p in enumerate(patches):
        img.alpha_composite(p, ((i % cols) * pw, (i // cols) * ph))
    img.save(os.path.join(out, "fields.png"))


# Scene legend: one character per tile
LEGEND = {
    ".": 0, "f": 20, "p": 6, "d": 21, "s": 2, "n": 8, "i": 9, "k": 40, "c": 22, "o": 3,
    "w": 1, "W": 7, "L": 10, "T": 5, "B": 34, "R": 35, "M": 36, "P": 37, "N": 38, "S": 39,
    "C": 29, "V": 17, "O": 28, "X": 18, "#": 16, "I": 41, "F": 12, "K": 25, "Y": 24, "U": 26,
    "H": 31, "A": 11, "Z": 4, "E": 15, "G": 32, "m": 27, "g": 33,
}

SCENES = {
    "meadow": [
        "TTTTTTTTTTTT",
        "TTTBBTTTTBTT",
        "TB....fff.BT",
        "T..M..ppp..T",
        "T.fB..p.R..T",
        "T....pp..www",
        "TR..pp..wwww",
        "T..pp.C.wwww",
        "T.pp..C..www",
        "TppffMB....T",
        "Tp....f..MTT",
        "TTTTTTTTTTTT",
    ],
    "lagoon": [
        "WWWWWWWWWWWW",
        "WwwwwwwwwwwW",
        "WwssssssssWW",
        "WssP..sKsssw",
        "Wss..kkkk.sw",
        "Wws.Pk..k.sw",
        "Wwss.kwwkRsw",
        "Wws..kwwk.sw",
        "WwsR.kkkkPsw",
        "WwssP..ssssw",
        "WwwssssssswW",
        "WWWWwwwwwwWW",
    ],
    "snowglobe": [
        "NNNNNNNNNNNN",
        "NNNnnNNNnnNN",
        "Nn..nnnn..nN",
        "Nn.S.iii.RnN",
        "NnnniiiiinnN",
        "NnniiiIiiinN",
        "NnnniiiiinnN",
        "NnR.niiinS.N",
        "Nn..Nnnnn.nN",
        "NnnnnnnIInnN",
        "NNnnnnnnnnNN",
        "NNNNNNNNNNNN",
    ],
    "space": [
        "OOOOOOOOOOOO",
        "OVVVVVVVVVVO",
        "OVV######VVO",
        "OV###XX###VO",
        "OV##X####XVO",
        "OV##X#OO#XVO",
        "OV#####OO#VO",
        "OV##X####XVO",
        "OV###XX###VO",
        "OVV######VVO",
        "OVVVVVVVVVVO",
        "OOOOOOOOOOOO",
    ],
}


def scenes(atlas, out):
    wizard = character("wizard")
    druid = character("druid")
    imgs = []
    for name, rows in SCENES.items():
        grid = [[LEGEND[ch] for ch in row] for row in rows]
        spr = [(5, 5, wizard), (7, 4, druid)]
        imgs.append(compose(atlas, grid, sprites=spr))
    w = sum(i.width for i in imgs[:2])
    h = max(i.height for i in imgs) * 2
    img = Image.new("RGBA", (w, h), (150, 200, 240, 255))
    for k, im in enumerate(imgs):
        img.alpha_composite(im, ((k % 2) * imgs[0].width, (k // 2) * im.height))
    img.save(os.path.join(out, "scenes.png"))


def map_window(atlas, out, path, at, radius=14):
    data = json.load(open(path))
    rows = [list(map(int, r.split())) for r in data["layers"][0]["data"]]
    cx, cy = at if at else (data["width"] // 2, data["height"] // 2)
    x0, y0 = max(0, cx - radius), max(0, cy - radius)
    x1, y1 = min(data["width"], cx + radius), min(data["height"], cy + radius)
    grid = [row[x0:x1] for row in rows[y0:y1]]
    img = compose(atlas, grid, sprites=[(cx - x0, cy - y0, character("wizard"))])
    name = os.path.splitext(os.path.basename(path))[0]
    img.save(os.path.join(out, f"map_{name}_{cx}_{cy}.png"))


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("out")
    ap.add_argument("--atlas", action="store_true", help="use the committed sprites/tiles.png")
    ap.add_argument("--map", help="also compose a window of this world file")
    ap.add_argument("--at", help="x,y the map window is centred on")
    args = ap.parse_args()
    os.makedirs(args.out, exist_ok=True)
    if args.atlas:
        atlas = Image.open(os.path.join(ROOT, "sprites", "tiles.png")).convert("RGBA")
    else:
        atlas = gt.build_atlas()
    sheet(atlas, args.out)
    fields(atlas, args.out)
    scenes(atlas, args.out)
    if args.map:
        at = tuple(int(v) for v in args.at.split(",")) if args.at else None
        map_window(atlas, args.out, args.map, at)
    print(f"Wrote {args.out}/sheet.png, fields.png, scenes.png")


if __name__ == "__main__":
    main()
