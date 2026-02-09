#!/usr/bin/env python3
"""Generate sprites/tiles.png — a 20-column x 4-row isometric tileset.

Each cell is 40x56 px. Flat tiles occupy the bottom 20px (diamond only).
Elevated tiles have a top diamond face + left/right side faces.

Rows correspond to animation frames (0–3). Animated tiles (Water, Lava, etc.)
get per-frame color variations; non-animated tiles are identical across rows.

Colors: flat fills — top=base, left=20% darker, right=35% darker.
"""

import math
import random
from PIL import Image, ImageDraw

CELL_W = 40
CELL_H = 56
HW = CELL_W // 2   # 20
HH = 10            # ISO_HALF_H (half of 20px diamond height)
NUM_TILES = 20
ANIM_FRAMES = 4

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


def lighten(rgb, factor):
    """Lighten by factor (0.0 = unchanged, 1.0 = white)."""
    return tuple(min(255, int(c + (255 - c) * factor)) for c in rgb)


def lerp_color(a, b, t):
    """Linearly interpolate between two RGB tuples."""
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


# ---- Animation functions ----
# Each returns a modified base_rgb for a given pixel position and frame.

def anim_water(base_rgb, px, py, frame):
    """Shifting light ripple highlights."""
    phase = math.sin((px * 0.8 + py * 0.5 + frame * 1.6)) * 0.5 + 0.5
    return lighten(base_rgb, phase * 0.25)


def anim_deep_water(base_rgb, px, py, frame):
    """Slower, darker ripple highlights."""
    phase = math.sin((px * 0.5 + py * 0.3 + frame * 1.2)) * 0.5 + 0.5
    return lighten(base_rgb, phase * 0.15)


def anim_lava(base_rgb, px, py, frame):
    """Pulsing orange-yellow hot spots."""
    hot = (255, 200, 50)
    # Use a hash-like pattern seeded by position for stable hot spots that shift per frame
    noise = math.sin(px * 1.3 + py * 0.7 + frame * 1.5) * 0.5 + 0.5
    noise *= math.cos(px * 0.6 - py * 1.1 + frame * 0.9) * 0.5 + 0.5
    return lerp_color(base_rgb, hot, noise * 0.5)


def anim_toxic(base_rgb, px, py, frame):
    """Bubbling bright spots."""
    bright = (200, 255, 100)
    bubble = math.sin(px * 1.5 + frame * 2.0) * math.cos(py * 1.2 + frame * 1.3)
    t = max(0, bubble) * 0.4
    return lerp_color(base_rgb, bright, t)


def anim_plasma(base_rgb, px, py, frame):
    """Hue-shifting color cycle."""
    # Rotate hue by shifting channel emphasis per frame
    shift = frame * 0.25  # 0, 0.25, 0.5, 0.75
    r = int(128 + 127 * math.sin(2 * math.pi * (shift + 0.0)))
    g = int(128 + 127 * math.sin(2 * math.pi * (shift + 0.33)))
    b = int(128 + 127 * math.sin(2 * math.pi * (shift + 0.66)))
    target = (r, g, b)
    return lerp_color(base_rgb, target, 0.35)


def anim_energy_field(base_rgb, px, py, frame):
    """Brightness pulsing shimmer."""
    pulse = math.sin(frame * 1.5 + px * 0.4 + py * 0.4) * 0.5 + 0.5
    return lighten(base_rgb, pulse * 0.3)


# Map tile IDs to their animation functions
ANIM_FUNCS = {
    1:  anim_water,
    7:  anim_deep_water,
    10: anim_lava,
    15: anim_energy_field,
    18: anim_toxic,
    19: anim_plasma,
}


def draw_tile_image(base_rgb, elevation: int, tile_id: int = -1, frame: int = 0) -> Image.Image:
    """Draw one isometric tile on its own 40x56 RGBA image, bottom-aligned.

    If the tile has an animation function and frame > 0, per-pixel color
    variation is applied. Frame 0 always uses the base color (consistent
    with the non-animated appearance).
    """
    cell = Image.new("RGBA", (CELL_W, CELL_H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(cell)

    cx = HW  # 20 — center of the cell
    base_y = CELL_H - HH  # 46 — y-center of flat diamond

    anim_fn = ANIM_FUNCS.get(tile_id)

    top_color = base_rgb
    left_color = darken(base_rgb, 0.20)
    right_color = darken(base_rgb, 0.35)

    h = elevation

    if h > 0:
        # --- Elevated tile ---
        top_diamond = [
            (cx,      base_y - HH - h),
            (cx + HW, base_y - h),
            (cx,      base_y + HH - h),
            (cx - HW, base_y - h),
        ]
        draw.polygon(top_diamond, fill=top_color)

        left_face = [
            (cx - HW, base_y - h),
            (cx,      base_y + HH - h),
            (cx,      base_y + HH),
            (cx - HW, base_y),
        ]
        draw.polygon(left_face, fill=left_color)

        right_face = [
            (cx,      base_y + HH - h),
            (cx + HW, base_y - h),
            (cx + HW, base_y),
            (cx,      base_y + HH),
        ]
        draw.polygon(right_face, fill=right_color)
    else:
        # --- Flat tile (diamond only) ---
        diamond = [
            (cx,      base_y - HH),
            (cx + HW, base_y),
            (cx,      base_y + HH),
            (cx - HW, base_y),
        ]
        draw.polygon(diamond, fill=top_color)

    # Apply per-pixel animation if this tile is animated and frame > 0
    if anim_fn is not None:
        pixels = cell.load()
        for py in range(CELL_H):
            for px in range(CELL_W):
                r, g, b, a = pixels[px, py]
                if a == 0:
                    continue
                # Determine which face this pixel belongs to by its original color
                orig = (r, g, b)
                if orig == top_color:
                    new_base = anim_fn(base_rgb, px, py, frame)
                    pixels[px, py] = (*new_base, a)
                elif orig == left_color:
                    new_base = anim_fn(base_rgb, px, py, frame)
                    pixels[px, py] = (*darken(new_base, 0.20), a)
                elif orig == right_color:
                    new_base = anim_fn(base_rgb, px, py, frame)
                    pixels[px, py] = (*darken(new_base, 0.35), a)

    return cell


def main():
    img = Image.new("RGBA", (NUM_TILES * CELL_W, CELL_H * ANIM_FRAMES), (0, 0, 0, 0))

    for frame in range(ANIM_FRAMES):
        for tile_id, name, argb, elevation in TILES:
            base_rgb = argb_to_rgb(argb)
            cell = draw_tile_image(base_rgb, elevation, tile_id, frame)
            img.paste(cell, (tile_id * CELL_W, frame * CELL_H))

    import os
    out_dir = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "sprites")
    os.makedirs(out_dir, exist_ok=True)
    out_path = os.path.join(out_dir, "tiles.png")
    img.save(out_path)
    print(f"Generated {out_path} ({img.width}x{img.height})")


if __name__ == "__main__":
    main()
