#!/usr/bin/env python3
"""Generate the application icon: sprites/icon_wizard_{1024,256,128}.png, .ico and .icns.

Reuses the wizard character's front-facing draw routine from
generate_wizard.py, rendered as a single large frame instead of a
spritesheet, so the icon always matches the in-game wizard sprite.
generate_wizard.py runs this after writing his sheet, so redrawing the
wizard redraws the icon.

The .ico (Windows) is written directly via Pillow, cross-platform. The
.icns (macOS) needs Apple's iconutil, so it is only remade on a Mac.
"""

import os
import subprocess
import sys
import tempfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from PIL import Image, ImageDraw
from generate_wizard import draw_wizard, DOWN
from sprite_base import FRAME_SIZE, ScaledDraw, finish_frame

CANVAS = 128
MARGIN = 10
# The wizard is authored in a 64px space; drawing it through ScaledDraw at this
# factor renders the icon at native resolution instead of blowing a 64px sprite
# up 16x, which is what the icon used to be.
RENDER_SCALE = 16
ICON_SIZES = [1024, 256, 128]
ICO_SIZES = [(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)]
# An .iconset holds each of these at 1x and at 2x
ICNS_SIZES = [16, 32, 128, 256, 512]


def write_icns(master, out_path):
    """The macOS .icns, from the 1024px master, through iconutil."""
    with tempfile.TemporaryDirectory() as tmp:
        iconset = os.path.join(tmp, "icon.iconset")
        os.mkdir(iconset)
        for size in ICNS_SIZES:
            for k, suffix in ((1, ""), (2, "@2x")):
                master.resize((size * k, size * k), Image.LANCZOS).save(
                    os.path.join(iconset, f"icon_{size}x{size}{suffix}.png"))
        subprocess.run(["iconutil", "-c", "icns", iconset, "-o", out_path], check=True)


def main():
    canvas = Image.new("RGBA", (CANVAS * RENDER_SCALE,) * 2, (0, 0, 0, 0))
    draw = ScaledDraw(ImageDraw.Draw(canvas), RENDER_SCALE)
    # Offset so the hat tip and staff (which extend above/right of the
    # character's own 64x64 draw box) land safely inside the canvas.
    # Frame 0 is his standing pose (the idle frame); the others are his walk.
    draw_wizard(draw, 16, 24, DOWN, frame=0)

    bbox = canvas.getbbox()
    cropped = canvas.crop(bbox)
    side = max(cropped.width, cropped.height) + MARGIN * RENDER_SCALE * 2
    square = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    square.paste(cropped, ((side - cropped.width) // 2, (side - cropped.height) // 2))

    master = None
    for size in ICON_SIZES:
        # Same shading the in-game sprites get, applied at each icon size so the
        # bevel and contour stay proportional rather than scaling with the art.
        icon = finish_frame(square.resize((size, size), Image.LANCZOS),
                            scale=max(1, size // FRAME_SIZE))
        if size == max(ICON_SIZES):
            master = icon
        out_path = f"sprites/icon_wizard_{size}.png"
        icon.save(out_path)
        print(f"Generated {out_path} ({size}x{size})")

    ico_path = "sprites/icon_wizard.ico"
    master.save(ico_path, sizes=ICO_SIZES)
    print(f"Generated {ico_path} ({len(ICO_SIZES)} sizes)")

    icns_path = "sprites/icon_wizard.icns"
    if sys.platform == "darwin":
        write_icns(master, icns_path)
        print(f"Generated {icns_path}")
    else:
        print(f"Skipped {icns_path}: it needs macOS's iconutil")


if __name__ == "__main__":
    main()
