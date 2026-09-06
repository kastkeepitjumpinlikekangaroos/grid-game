#!/usr/bin/env python3
"""Generate sprites/icon_wizard.png/.ico — the application window/taskbar icon.

Reuses the wizard character's front-facing draw routine from
generate_wizard.py, rendered as a single large frame instead of a
spritesheet, so the icon always matches the in-game wizard sprite.

The .ico (Windows) is written directly via Pillow, cross-platform. The
.icns (macOS) needs Apple's sips/iconutil — see scripts/generate_icns.sh.
"""

import sys
import os

sys.path.insert(0, os.path.dirname(__file__))

from PIL import Image, ImageDraw
from generate_wizard import draw_wizard, DOWN

CANVAS = 128
MARGIN = 10
ICON_SIZES = [1024, 256, 128]
ICO_SIZES = [(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)]


def main():
    canvas = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
    draw = ImageDraw.Draw(canvas)
    # Offset so the hat tip and staff (which extend above/right of the
    # character's own 64x64 draw box) land safely inside the canvas.
    draw_wizard(draw, 16, 24, DOWN, frame=1)  # frame 1: bright pulsing orb

    bbox = canvas.getbbox()
    cropped = canvas.crop(bbox)
    side = max(cropped.width, cropped.height) + MARGIN * 2
    square = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    square.paste(cropped, ((side - cropped.width) // 2, (side - cropped.height) // 2))

    master = None
    for size in ICON_SIZES:
        icon = square.resize((size, size), Image.LANCZOS)
        if size == max(ICON_SIZES):
            master = icon
        out_path = f"sprites/icon_wizard_{size}.png"
        icon.save(out_path)
        print(f"Generated {out_path} ({size}x{size})")

    ico_path = "sprites/icon_wizard.ico"
    master.save(ico_path, sizes=ICO_SIZES)
    print(f"Generated {ico_path} ({len(ICO_SIZES)} sizes)")


if __name__ == "__main__":
    main()
