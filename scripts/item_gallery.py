#!/usr/bin/env python3
"""Contact sheets of the pickups, to judge scripts/generate_items.py by.

    python3 scripts/item_gallery.py OUT                 # from the draw functions as they are now
    python3 scripts/item_gallery.py OUT --atlas sprites/items.png   # from an atlas already written

Nothing has to be written to sprites/ to look at an edit. It writes

    items_close.png   every cell enlarged pixel for pixel (the gem's eight steps in a row), on a
                      neutral ground and on snow, with its cell's middle marked
    items_game.png    the five items as the game shows them: floating over their shadow beside a
                      character for scale, on the maps' five grounds, in the engine's colours
                      (sprite_review's ENGINE and GROUNDS), at High on a HiDPI screen (a cell 128
                      pixels across), at High on an ordinary one (64), and at Low on each, where the
                      world is drawn at a third of the display's pixels or so and scaled back up

The close-ups are for faults; the game sheet is the judge, and its Low columns above all: a pickup
is under 30 pixels across there, and whatever is thin on it is gone. The engine's grade still changes
everything, so finish in the game (the golden frames' `*_items` scenes, the render bench).
"""

import argparse
import os
import sys

from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import generate_items as gi  # noqa: E402
from sprite_review import GROUNDS, _font, engine_graded, in_game  # noqa: E402

CELL = gi.CELL
ITEMS = (("gem", gi.GEM), ("heart", gi.HEART), ("star", gi.STAR), ("shield", gi.SHIELD), ("fence", gi.FENCE))
# How far over the ground the middle of an item floats, in world units (ItemPainter.HOVER)
HOVER = 16.0
# World units to a screen pixel at the camera's widest: High on a HiDPI screen, High on an ordinary
# one, and Low on each (RenderQuality.sceneScale: 0.35 of a HiDPI framebuffer, 0.55 of an ordinary one)
VIEWS = (("High, HiDPI", 3.2, 3.2), ("High", 1.6, 1.6), ("Low, HiDPI", 3.2 * 0.35, 3.2), ("Low", 1.6 * 0.55, 1.6))


def cell_of(atlas, i):
    x, y = (i % gi.COLS) * CELL, (i // gi.COLS) * CELL
    return atlas.crop((x, y, x + CELL, y + CELL))


def close_page(atlas, path):
    k = 4
    order = list(range(gi.GEM, gi.GEM + gi.GEM_FRAMES)) + [gi.HEART, gi.STAR, gi.SHIELD, gi.FENCE, gi.TWINKLE]
    cols = 8
    rows = (len(order) + cols - 1) // cols
    page = Image.new("RGBA", (cols * CELL * k, rows * 2 * CELL * k), (20, 20, 24, 255))
    for n, i in enumerate(order):
        big = cell_of(atlas, i).resize((CELL * k, CELL * k), Image.NEAREST)
        for half, bg in enumerate(((112, 132, 112), (216, 215, 212))):
            tile = Image.new("RGBA", big.size, bg + (255,))
            tile.alpha_composite(big)
            d = ImageDraw.Draw(tile)
            m = CELL * k // 2
            d.line([(m - 6, m), (m + 6, m)], fill=(210, 60, 60, 255))
            d.line([(m, m - 6), (m, m + 6)], fill=(210, 60, 60, 255))
            page.paste(tile, ((n % cols) * CELL * k, ((n // cols) * 2 + half) * CELL * k))
    page.convert("RGB").save(path)


def _shadow(w, h, cx, cy, k):
    """The shadow an item floats over (ItemPainter.drawGroundMarks), `k` pixels a world unit."""
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    for rx, ry, a in ((16.5, 6.8, 0.24), (11, 4.6, 0.5)):
        layer = Image.new("L", (w, h), 0)
        ImageDraw.Draw(layer).ellipse([cx - rx * k * 0.5, cy - ry * k * 0.5, cx + rx * k * 0.5, cy + ry * k * 0.5],
                                      fill=int(255 * a * 0.6))
        layer = layer.filter(ImageFilter.GaussianBlur(max(0.5, ry * k * 0.3)))
        img.alpha_composite(Image.merge("RGBA", (Image.new("L", (w, h), 5), Image.new("L", (w, h), 3),
                                                 Image.new("L", (w, h), 12), layer)))
    return img


def floating(cell, ground, scene, shown):
    """An item's cell over `ground`, drawn `scene` pixels a world unit and shown at `shown`: at Low the
    world is drawn small and the display scales it up, which is what softens it."""
    deck = tuple(ground) == dict(GROUNDS)["deck"]
    w = int(round(gi.CELL_UNITS * 1.25 * scene))
    h = int(round(gi.CELL_UNITS * 1.3 * scene))
    tile = Image.new("RGBA", (w, h), ground + (255,))
    gx, gy = w / 2.0, h - 9.0 * scene
    tile.alpha_composite(_shadow(w, h, gx, gy, scene))
    size = max(2, int(round(gi.CELL_UNITS * scene)))
    art = engine_graded(cell, deck).resize((size, size), Image.BILINEAR if size >= CELL else Image.BOX)
    if size < CELL:
        # minified through the texture's halvings, as the GPU does it: a box at each, then linear
        art = engine_graded(cell, deck)
        s = CELL
        while s // 2 >= size and s > 32:
            s //= 2
            art = art.resize((s, s), Image.BOX)
        art = art.resize((size, size), Image.BILINEAR)
    tile.alpha_composite(art, (int(round(gx - size / 2.0)), int(round(gy - HOVER * scene - size / 2.0))))
    if shown != scene:
        tile = tile.resize((int(round(w * shown / scene)), int(round(h * shown / scene))), Image.BILINEAR)
    return tile


def game_page(atlas, path):
    wizard = None
    wpath = os.path.join(os.path.dirname(HERE), "sprites", "wizard.png")
    if os.path.exists(wpath):
        wizard = Image.open(wpath).convert("RGBA").crop((0, 0, 128, 128))
    pad, label_h = 6, 16
    rows = []
    for gname, ground in GROUNDS:
        strips = []
        for vname, scene, shown in VIEWS:
            tiles = []
            if wizard is not None:
                size = int(round(48 * shown))
                tiles.append(in_game(wizard, size, ground))
            for _, idx in ITEMS:
                tiles.append(floating(cell_of(atlas, idx), ground, scene, shown))
            hh = max(t.height for t in tiles)
            strip = Image.new("RGBA", (sum(t.width for t in tiles), hh), ground + (255,))
            x = 0
            for t in tiles:
                strip.paste(t, (x, hh - t.height))
                x += t.width
            strips.append((vname, strip))
        rows.append((gname, strips))
    width = max(sum(s.width + pad for _, s in strips) for _, strips in rows) + pad
    height = sum(max(s.height for _, s in strips) + label_h + pad for _, strips in rows) + pad
    page = Image.new("RGBA", (width, height), (20, 20, 24, 255))
    d = ImageDraw.Draw(page)
    y = pad
    for gname, strips in rows:
        x = pad
        hh = max(s.height for _, s in strips)
        for vname, strip in strips:
            d.text((x + 2, y), "%s: %s" % (gname, vname), font=_font(11), fill=(230, 230, 235))
            page.paste(strip, (x, y + label_h + hh - strip.height))
            x += strip.width + pad
        y += hh + label_h + pad
    page.convert("RGB").save(path)


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("out")
    ap.add_argument("--atlas", help="an items.png to show, rather than drawing one now")
    args = ap.parse_args()
    os.makedirs(args.out, exist_ok=True)
    atlas = Image.open(args.atlas).convert("RGBA") if args.atlas else gi.build_atlas()
    close_page(atlas, os.path.join(args.out, "items_close.png"))
    game_page(atlas, os.path.join(args.out, "items_game.png"))
    print("wrote %s/items_close.png, items_game.png" % args.out)


if __name__ == "__main__":
    main()
