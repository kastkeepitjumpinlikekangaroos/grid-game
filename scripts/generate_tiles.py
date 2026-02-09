#!/usr/bin/env python3
"""Generate sprites/tiles.png — a 20-column x 1-row isometric tileset.

Each cell is 40x56 px. Flat tiles occupy the bottom 20px (diamond only).
Elevated tiles have a top diamond face + left/right side faces.

Colors: flat fills — top=base, left=20% darker, right=35% darker.
"""

from PIL import Image, ImageDraw

CELL_W = 40
CELL_H = 56
HW = CELL_W // 2   # 20
HH = 10            # ISO_HALF_H (half of 20px diamond height)
NUM_TILES = 20

# Tile definitions: (id, name, argb_hex, elevation)
TILES = [
    (0,  "Grass",       0xFF4CAF50,  0),
    (1,  "Water",       0xFF2196F3, 12),
    (2,  "Sand",        0xFFF5DEB3,  0),
    (3,  "Stone",       0xFF9E9E9E,  0),
    (4,  "Wall",        0xFF5D4037, 20),
    (5,  "Tree",        0xFF2E7D32, 28),
    (6,  "Path",        0xFFD7CCC8,  0),
    (7,  "DeepWater",   0xFF1565C0, 16),
    (8,  "Snow",        0xFFFAFAFA,  0),
    (9,  "Ice",         0xFFB3E5FC,  0),
    (10, "Lava",        0xFFFF5722, 12),
    (11, "Mountain",    0xFF757575, 36),
    (12, "Fence",       0xFFA1887F, 12),
    (13, "Metal",       0xFF78909C,  0),
    (14, "Glass",       0xFF80DEEA,  0),
    (15, "EnergyField", 0xFFAB47BC, 20),
    (16, "Circuit",     0xFF004D40,  0),
    (17, "Void",        0xFF0A0A12, 12),
    (18, "Toxic",       0xFF76FF03, 12),
    (19, "Plasma",      0xFFFF4081, 16),
]


def argb_to_rgb(argb: int):
    r = (argb >> 16) & 0xFF
    g = (argb >> 8) & 0xFF
    b = argb & 0xFF
    return (r, g, b)


def darken(rgb, factor):
    """Darken by factor (0.0 = unchanged, 1.0 = black)."""
    return tuple(max(0, int(c * (1 - factor))) for c in rgb)


def draw_tile_image(base_rgb, elevation: int) -> Image.Image:
    """Draw one isometric tile on its own 40x56 RGBA image, bottom-aligned.

    Drawing on a separate image ensures polygon rasterization is clipped
    to cell boundaries (no pixel leaking into adjacent tile cells).
    """
    cell = Image.new("RGBA", (CELL_W, CELL_H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(cell)

    cx = HW  # 20 — center of the cell
    base_y = CELL_H - HH  # 46 — y-center of flat diamond

    top_color = base_rgb
    left_color = darken(base_rgb, 0.20)
    right_color = darken(base_rgb, 0.35)

    h = elevation

    if h > 0:
        # --- Elevated tile ---
        # Top face diamond shifted up by h
        top_diamond = [
            (cx,      base_y - HH - h),  # top
            (cx + HW, base_y - h),        # right
            (cx,      base_y + HH - h),   # bottom
            (cx - HW, base_y - h),        # left
        ]
        draw.polygon(top_diamond, fill=top_color)

        # Left side face (parallelogram)
        left_face = [
            (cx - HW, base_y - h),        # top-left
            (cx,      base_y + HH - h),   # top-right
            (cx,      base_y + HH),       # bottom-right
            (cx - HW, base_y),            # bottom-left
        ]
        draw.polygon(left_face, fill=left_color)

        # Right side face (parallelogram)
        right_face = [
            (cx,      base_y + HH - h),   # top-left
            (cx + HW, base_y - h),        # top-right
            (cx + HW, base_y),            # bottom-right
            (cx,      base_y + HH),       # bottom-left
        ]
        draw.polygon(right_face, fill=right_color)
    else:
        # --- Flat tile (diamond only) ---
        diamond = [
            (cx,      base_y - HH),  # top
            (cx + HW, base_y),       # right
            (cx,      base_y + HH),  # bottom
            (cx - HW, base_y),       # left
        ]
        draw.polygon(diamond, fill=top_color)

    return cell


def main():
    img = Image.new("RGBA", (NUM_TILES * CELL_W, CELL_H), (0, 0, 0, 0))

    for tile_id, name, argb, elevation in TILES:
        base_rgb = argb_to_rgb(argb)
        cell = draw_tile_image(base_rgb, elevation)
        img.paste(cell, (tile_id * CELL_W, 0))

    import os
    out_dir = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "sprites")
    os.makedirs(out_dir, exist_ok=True)
    out_path = os.path.join(out_dir, "tiles.png")
    img.save(out_path)
    print(f"Generated {out_path} ({img.width}x{img.height})")


if __name__ == "__main__":
    main()
