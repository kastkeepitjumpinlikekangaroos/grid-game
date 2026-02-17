#!/usr/bin/env python3
"""Generate sprites/transition_masks.png — 4 isometric edge alpha masks (N/E/S/W).

Each mask is 80x112px (matching tile atlas cell size).
The masks define alpha gradients from full opacity at the diamond edge
to fully transparent over ~30% of the tile width.

Only the top face (flat/walkable tile area) is masked.
Layout: 4 masks side by side = 320x112px total.

Mask indices: 0=North, 1=East, 2=South, 3=West
"""

import math
import os
from PIL import Image

CELL_W, CELL_H = 80, 112
HW, HH = 40, 20
CX, BASE_Y = 40, 92
NUM_MASKS = 4

# Flat tile diamond: same geometry as tiles with h=0
def is_on_top_face(px, py):
    """Check if pixel is inside the flat diamond (top face of h=0 tile)."""
    dx = abs(px - CX)
    dy = abs(py - BASE_Y)
    d = dx / HW + dy / HH
    return d <= 1.0

def diamond_edge_distance(px, py):
    """Normalized distance from diamond edge (0 at edge, 1 at center)."""
    dx = abs(px - CX)
    dy = abs(py - BASE_Y)
    d = dx / HW + dy / HH
    return max(0.0, 1.0 - d)

def edge_gradient(px, py, direction):
    """Compute alpha gradient for a given edge direction.

    direction 0 (North): gradient from north edge (top) inward
    direction 1 (East):  gradient from east edge (right) inward
    direction 2 (South): gradient from south edge (bottom) inward
    direction 3 (West):  gradient from west edge (left) inward

    Returns alpha 0.0-1.0 (1.0 = fully opaque at edge, fading inward)
    """
    # Normalized coordinates relative to diamond center
    nx = (px - CX) / HW   # -1 to 1
    ny = (py - BASE_Y) / HH  # -1 to 1

    # Fade depth as fraction of tile
    fade_depth = 0.35

    if direction == 0:  # North — top edge of diamond
        # Edge factor: how close to the north edge
        # North edge: ny approaches -1 + |nx|
        edge_dist = (-ny - abs(nx)) + 1.0  # 0 at north edge, increases inward
        t = edge_dist / fade_depth if fade_depth > 0 else 1.0
    elif direction == 1:  # East — right edge
        edge_dist = (-nx - abs(ny)) + 1.0
        t = edge_dist / fade_depth if fade_depth > 0 else 1.0
    elif direction == 2:  # South — bottom edge
        edge_dist = (ny - abs(nx)) + 1.0  # note: flipped from North
        # Wait, let me reconsider. South edge: ny approaches 1 - |nx|
        edge_dist = (1.0 - ny - abs(nx))  # 0 at south edge
        # Actually the diamond edge at the south is where ny + |nx| = 1
        # So distance from south edge = 1 - (ny + |nx|) when approaching from inside
        # But we want distance from south edge going inward (northward)
        # South edge: ny = 1 - |nx|
        # Distance from south edge inward = (1 - |nx|) - ny = 1 - |nx| - ny
        edge_dist = 1.0 - abs(nx) - ny  # 0 at south edge, increases going north
        t = edge_dist / fade_depth if fade_depth > 0 else 1.0
    else:  # West — left edge
        edge_dist = (nx - abs(ny)) + 1.0
        # West edge: nx = -(1 - |ny|), so -nx = 1 - |ny|
        # Distance from west edge inward = nx - (-(1-|ny|)) = nx + 1 - |ny|
        edge_dist = nx + 1.0 - abs(ny)  # 0 at west edge, increases going east
        t = edge_dist / fade_depth if fade_depth > 0 else 1.0

    t = max(0.0, min(1.0, t))
    # Smoothstep for feathered falloff
    alpha = 1.0 - (3 * t * t - 2 * t * t * t)
    return max(0.0, min(1.0, alpha))


def generate_mask(direction):
    """Generate one 80x112 mask image for the given direction."""
    mask = Image.new("RGBA", (CELL_W, CELL_H), (0, 0, 0, 0))
    pixels = mask.load()

    for py in range(CELL_H):
        for px in range(CELL_W):
            if not is_on_top_face(px, py):
                continue
            alpha = edge_gradient(px, py, direction)
            if alpha > 0.001:
                a = int(alpha * 255)
                pixels[px, py] = (255, 255, 255, a)

    return mask


def main():
    img = Image.new("RGBA", (NUM_MASKS * CELL_W, CELL_H), (0, 0, 0, 0))

    for direction in range(NUM_MASKS):
        mask = generate_mask(direction)
        img.paste(mask, (direction * CELL_W, 0))

    out_dir = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "sprites")
    os.makedirs(out_dir, exist_ok=True)
    path = os.path.join(out_dir, "transition_masks.png")
    img.save(path)
    print(f"Generated {path} ({img.width}x{img.height})")


if __name__ == "__main__":
    main()
