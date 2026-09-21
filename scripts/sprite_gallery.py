#!/usr/bin/env python3
"""Contact-sheet gallery for the character sprite sheets.

The review loop for character art, in the same spirit as scripts/sound_gallery.py
and the projectile gallery: 112 characters cannot be judged one at a time, and the
faults that matter are the ones you only see when they sit side by side.

    python3 scripts/sprite_gallery.py /tmp/spritegallery              # contact sheets
    python3 scripts/sprite_gallery.py /tmp/spritegallery --chars wolf,bear
    python3 scripts/sprite_gallery.py /tmp/spritegallery --detail     # per-character 4x4

A sprite is displayed in game at PLAYER_DISPLAY_SIZE_PX (48) x CAMERA_ZOOM (1.6)
~= 77px. Every cell is drawn at that size and again at 2x, over the three terrain
bands a character actually has to read against (dark stone, grass, sand) - judging
a sprite at its 128px sheet resolution flatters it, and judging it on one ground
hides the contrast faults.
"""

import argparse
import os
import sys

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SPRITES = os.path.join(ROOT, "sprites")

FRAME = 128
DOWN, UP, LEFT, RIGHT = 0, 1, 2, 3
DISPLAY = 77          # PLAYER_DISPLAY_SIZE_PX * CAMERA_ZOOM
BIG = DISPLAY * 2

# Terrain bands from Tile.scala, the three grounds a sprite must read against.
GROUNDS = [(58, 58, 64), (74, 112, 62), (198, 178, 128)]

# name -> sprite file, in CharacterDef order.
ROSTER = [
    ("Spaceman", "character"), ("Gladiator", "gladiator"), ("Wraith", "wraith"),
    ("Wizard", "wizard"), ("Tidecaller", "tidecaller"), ("Soldier", "soldier"),
    ("Raptor", "raptor"), ("Assassin", "assassin"), ("Warden", "warden"),
    ("Samurai", "samurai"), ("PlagueDoctor", "plaguedoctor"), ("Vampire", "vampire"),
    ("Pyromancer", "pyromancer"), ("Cryomancer", "cryomancer"), ("Stormcaller", "stormcaller"),
    ("Earthshaker", "earthshaker"), ("Windwalker", "windwalker"), ("MagmaKnight", "magmaknight"),
    ("Frostbite", "frostbite"), ("Sandstorm", "sandstorm"), ("Thornweaver", "thornweaver"),
    ("Cloudrunner", "cloudrunner"), ("Inferno", "inferno"), ("Glacier", "glacier"),
    ("Mudslinger", "mudslinger"), ("Ember", "ember"), ("Avalanche", "avalanche"),
    ("Necromancer", "necromancer"), ("SkeletonKing", "skeletonking"), ("Banshee", "banshee"),
    ("Lich", "lich"), ("Ghoul", "ghoul"), ("Reaper", "reaper"), ("Shade", "shade"),
    ("Revenant", "revenant"), ("Gravedigger", "gravedigger"), ("Dullahan", "dullahan"),
    ("Phantom", "phantom"), ("Mummy", "mummy"), ("Deathknight", "deathknight"),
    ("Shadowfiend", "shadowfiend"), ("Poltergeist", "poltergeist"),
    ("Paladin", "paladin"), ("Ranger", "ranger"), ("Berserker", "berserker"),
    ("Crusader", "crusader"), ("Druid", "druid"), ("Bard", "bard"), ("Monk", "monk"),
    ("Cleric", "cleric"), ("Rogue", "rogue"), ("Barbarian", "barbarian"),
    ("Enchantress", "enchantress"), ("Jester", "jester"), ("Valkyrie", "valkyrie"),
    ("Warlock", "warlock"), ("Inquisitor", "inquisitor"),
    ("Cyborg", "cyborg"), ("Hacker", "hacker"), ("MechPilot", "mechpilot"),
    ("Android", "android"), ("Chronomancer", "chronomancer"), ("Graviton", "graviton"),
    ("Tesla", "tesla"), ("Nanoswarm", "nanoswarm"), ("Voidwalker", "voidwalker"),
    ("Photon", "photon"), ("Railgunner", "railgunner"), ("Bombardier", "bombardier"),
    ("Sentinel", "sentinel"), ("Pilot", "pilot"), ("Glitcher", "glitcher"),
    ("Wolf", "wolf"), ("Serpent", "serpent"), ("Spider", "spider"), ("Bear", "bear"),
    ("Scorpion", "scorpion"), ("Hawk", "hawk"), ("Shark", "shark"), ("Beetle", "beetle"),
    ("Treant", "treant"), ("Phoenix", "phoenix"), ("Hydra", "hydra"), ("Mantis", "mantis"),
    ("Jellyfish", "jellyfish"), ("Gorilla", "gorilla"), ("Chameleon", "chameleon"),
    ("Minotaur", "minotaur"), ("Medusa", "medusa"), ("Cerberus", "cerberus"),
    ("Centaur", "centaur"), ("Kraken", "kraken"), ("Sphinx", "sphinx"),
    ("Cyclops", "cyclops"), ("Harpy", "harpy"), ("Griffin", "griffin"),
    ("Anubis", "anubis"), ("Yokai", "yokai"), ("Golem", "golem"), ("Djinn", "djinn"),
    ("Fenrir", "fenrir"), ("Chimera", "chimera"),
    ("Alchemist", "alchemist"), ("Puppeteer", "puppeteer"), ("Gambler", "gambler"),
    ("Blacksmith", "blacksmith"), ("Pirate", "pirate"), ("Chef", "chef"),
    ("Musician", "musician"), ("Astronomer", "astronomer"), ("Runesmith", "runesmith"),
    ("Shapeshifter", "shapeshifter"),
]


def _font(size):
    for path in ("/System/Library/Fonts/Supplemental/Arial Bold.ttf",
                 "/System/Library/Fonts/Helvetica.ttc",
                 "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"):
        if os.path.exists(path):
            try:
                return ImageFont.truetype(path, size)
            except OSError:
                pass
    return ImageFont.load_default()


def load_sheet(slug):
    path = os.path.join(SPRITES, slug + ".png")
    return Image.open(path).convert("RGBA")


def frame(sheet, direction, idx):
    return sheet.crop((idx * FRAME, direction * FRAME, (idx + 1) * FRAME, (direction + 1) * FRAME))


def on_ground(img, size, ground):
    """Scale a frame to `size` and composite it over a flat ground colour."""
    cell = Image.new("RGBA", (size, size), ground + (255,))
    cell.alpha_composite(img.resize((size, size), Image.LANCZOS))
    return cell


CELL_W = BIG + 8
CELL_H = BIG + DISPLAY + 30


def cell(name, sheet):
    """One character: big front view, then front / side / back at true size.

    The small row used to be three front frames on three grounds, which hid
    every fault that only shows in profile — arms crossing the body, a head
    that only works head-on. One of each facing catches those.
    """
    img = Image.new("RGBA", (CELL_W, CELL_H), (24, 24, 28, 255))
    d = ImageDraw.Draw(img)
    big = on_ground(frame(sheet, DOWN, 0), BIG, GROUNDS[1])
    img.alpha_composite(big, (4, 4))
    y = BIG + 6
    for i, (dir_, fr) in enumerate(((DOWN, 2), (RIGHT, 1), (UP, 0))):
        img.alpha_composite(on_ground(frame(sheet, dir_, fr), DISPLAY, GROUNDS[i]),
                            (4 + i * (DISPLAY + 2), y))
    d.text((5, CELL_H - 20), name, font=_font(15), fill=(235, 235, 240))
    return img


def sheet_pages(entries, out_dir, cols=5, rows=4, prefix="chars"):
    per = cols * rows
    pages = []
    for p in range(0, len(entries), per):
        chunk = entries[p:p + per]
        page = Image.new("RGBA", (cols * CELL_W + 8, rows * CELL_H + 8), (16, 16, 18, 255))
        for i, (name, slug) in enumerate(chunk):
            c = cell(name, load_sheet(slug))
            page.paste(c, (4 + (i % cols) * CELL_W, 4 + (i // cols) * CELL_H))
        path = os.path.join(out_dir, "%s_%02d.png" % (prefix, p // per))
        page.convert("RGB").save(path)
        pages.append(path)
    return pages


def detail_page(name, slug, out_dir):
    """All 4 directions x 4 frames for one character, big, over grass."""
    size = BIG
    pad, top = 6, 26
    page = Image.new("RGBA", (4 * (size + pad) + pad, 4 * (size + pad) + pad + top), (16, 16, 18, 255))
    sheet = load_sheet(slug)
    for r in range(4):
        for c in range(4):
            g = GROUNDS[r % len(GROUNDS)]
            page.alpha_composite(on_ground(frame(sheet, r, c), size, g),
                                 (pad + c * (size + pad), top + pad + r * (size + pad)))
    ImageDraw.Draw(page).text((pad, 5), "%s  (rows: down / up / left / right)" % name,
                              font=_font(16), fill=(235, 235, 240))
    path = os.path.join(out_dir, "detail_%s.png" % slug)
    page.convert("RGB").save(path)
    return path


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("out_dir")
    ap.add_argument("--chars", default=None, help="comma-separated slugs")
    ap.add_argument("--detail", action="store_true", help="one 4x4 page per character")
    ap.add_argument("--cols", type=int, default=5)
    ap.add_argument("--rows", type=int, default=4)
    args = ap.parse_args()

    os.makedirs(args.out_dir, exist_ok=True)
    entries = ROSTER
    if args.chars:
        want = {s.strip().lower() for s in args.chars.split(",")}
        entries = [e for e in ROSTER if e[1] in want or e[0].lower() in want]
        if not entries:
            sys.exit("no characters matched %s" % args.chars)

    if args.detail:
        for name, slug in entries:
            print(detail_page(name, slug, args.out_dir))
    else:
        for p in sheet_pages(entries, args.out_dir, args.cols, args.rows):
            print(p)


if __name__ == "__main__":
    main()
