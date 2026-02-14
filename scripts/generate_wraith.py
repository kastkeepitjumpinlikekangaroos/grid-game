#!/usr/bin/env python3
"""Generate sprites/wraith.png — 4-column x 4-row character spritesheet.

128x128 PNG, 32x32 per frame.
Row layout: Down=0, Up=1, Left=2, Right=3
4 walking animation frames per direction.

Style matches Spaceman/Gladiator: big round head, round body, small limbs.
Theme: Spectral wraith — hooded figure, floating (no visible legs), trailing wisps.
Color palette: dark teal/ghostly green with black outlines.
"""

from PIL import Image, ImageDraw

FRAME_SIZE = 32
COLS = 4
ROWS = 4
IMG_W = FRAME_SIZE * COLS   # 128
IMG_H = FRAME_SIZE * ROWS   # 128

# Colors
OUTLINE = (30, 40, 35)
CLOAK_DARK = (25, 55, 50)
CLOAK = (40, 80, 70)
CLOAK_LIGHT = (55, 105, 90)
HOOD = (30, 60, 55)
HOOD_DARK = (20, 45, 40)
FACE_GLOW = (120, 220, 180)
FACE_DARK = (60, 140, 110)
EYE_GLOW = (150, 255, 200)
EYE_CORE = (200, 255, 230)
WISP = (80, 180, 140, 160)
WISP_BRIGHT = (120, 220, 180, 200)
BLACK = (20, 25, 22)
TEAL_GLOW = (60, 160, 130)

DOWN, UP, LEFT, RIGHT = 0, 1, 2, 3


def ellipse(draw, cx, cy, rx, ry, fill, outline=OUTLINE):
    """Draw an outlined ellipse centered at (cx, cy)."""
    draw.ellipse([cx - rx, cy - ry, cx + rx, cy + ry], fill=fill, outline=outline)


def draw_wraith(draw, ox, oy, direction, frame):
    """Draw a single wraith frame at offset (ox, oy).

    Proportions match Spaceman: big round head ~11px, body ~8px tall.
    Wraith floats — bottom of frame has wispy trail instead of legs.
    """
    # Floating bob animation (more pronounced than walking)
    bob = [0, -2, 0, -1][frame]
    wisp_sway = [-1, 0, 1, 0][frame]

    # Anchor: character floats slightly above ground
    base_y = oy + 27 + bob
    body_cx = ox + 16
    body_cy = base_y - 10
    head_cy = body_cy - 10

    if direction == DOWN:
        # --- Wispy trail (behind body, ghostly bottom) ---
        for i in range(3):
            trail_x = body_cx + (i - 1) * 4 + wisp_sway
            trail_y = body_cy + 6
            trail_len = 6 + (i % 2) * 2
            draw.line([(trail_x, trail_y), (trail_x + wisp_sway, trail_y + trail_len)],
                     fill=CLOAK_DARK, width=2)

        # --- Body (flowing cloak shape) ---
        # Main cloak body — wider at bottom for ghostly drape
        draw.polygon([
            (body_cx - 8, body_cy - 4),
            (body_cx + 8, body_cy - 4),
            (body_cx + 10, body_cy + 6),
            (body_cx - 10, body_cy + 6),
        ], fill=CLOAK, outline=OUTLINE)
        # Cloak highlight
        draw.polygon([
            (body_cx - 5, body_cy - 3),
            (body_cx + 5, body_cy - 3),
            (body_cx + 6, body_cy + 4),
            (body_cx - 6, body_cy + 4),
        ], fill=CLOAK_LIGHT, outline=None)

        # --- Hood (big, rounded, covering head) ---
        ellipse(draw, body_cx, head_cy, 9, 8, HOOD)
        # Hood inner shadow
        ellipse(draw, body_cx, head_cy + 1, 7, 6, HOOD_DARK)

        # --- Face (glowing from within the hood) ---
        ellipse(draw, body_cx, head_cy + 2, 4, 3, FACE_DARK)
        # Glowing eyes
        draw.rectangle([body_cx - 4, head_cy + 1, body_cx - 1, head_cy + 3], fill=EYE_GLOW)
        draw.rectangle([body_cx + 1, head_cy + 1, body_cx + 4, head_cy + 3], fill=EYE_GLOW)
        # Eye cores (bright center)
        draw.point((body_cx - 2, head_cy + 2), fill=EYE_CORE)
        draw.point((body_cx + 2, head_cy + 2), fill=EYE_CORE)

        # --- Hood peak ---
        draw.polygon([
            (body_cx - 2, head_cy - 9),
            (body_cx + 2, head_cy - 9),
            (body_cx + 1, head_cy - 5),
            (body_cx - 1, head_cy - 5),
        ], fill=HOOD, outline=OUTLINE)

    elif direction == UP:
        # --- Wispy trail ---
        for i in range(3):
            trail_x = body_cx + (i - 1) * 4 + wisp_sway
            trail_y = body_cy + 6
            trail_len = 6 + (i % 2) * 2
            draw.line([(trail_x, trail_y), (trail_x + wisp_sway, trail_y + trail_len)],
                     fill=CLOAK_DARK, width=2)

        # --- Body ---
        draw.polygon([
            (body_cx - 8, body_cy - 4),
            (body_cx + 8, body_cy - 4),
            (body_cx + 10, body_cy + 6),
            (body_cx - 10, body_cy + 6),
        ], fill=CLOAK, outline=OUTLINE)
        # Back of cloak (darker)
        draw.polygon([
            (body_cx - 6, body_cy - 3),
            (body_cx + 6, body_cy - 3),
            (body_cx + 7, body_cy + 5),
            (body_cx - 7, body_cy + 5),
        ], fill=CLOAK_DARK, outline=None)

        # --- Hood (back view) ---
        ellipse(draw, body_cx, head_cy, 9, 8, HOOD)
        ellipse(draw, body_cx, head_cy, 7, 6, HOOD_DARK)

        # --- Hood peak ---
        draw.polygon([
            (body_cx - 2, head_cy - 9),
            (body_cx + 2, head_cy - 9),
            (body_cx + 1, head_cy - 5),
            (body_cx - 1, head_cy - 5),
        ], fill=HOOD, outline=OUTLINE)

    elif direction == LEFT:
        # --- Wispy trail (flowing right/behind) ---
        for i in range(2):
            trail_x = body_cx + 2 + i * 3 + wisp_sway
            trail_y = body_cy + 5
            trail_len = 5 + i * 2
            draw.line([(trail_x, trail_y), (trail_x + abs(wisp_sway) + 1, trail_y + trail_len)],
                     fill=CLOAK_DARK, width=2)

        # --- Body ---
        draw.polygon([
            (body_cx - 6, body_cy - 4),
            (body_cx + 6, body_cy - 4),
            (body_cx + 8, body_cy + 6),
            (body_cx - 8, body_cy + 6),
        ], fill=CLOAK, outline=OUTLINE)
        draw.polygon([
            (body_cx - 4, body_cy - 3),
            (body_cx + 4, body_cy - 3),
            (body_cx + 5, body_cy + 4),
            (body_cx - 5, body_cy + 4),
        ], fill=CLOAK_LIGHT, outline=None)

        # --- Hood (side view facing left) ---
        ellipse(draw, body_cx - 1, head_cy, 8, 8, HOOD)
        ellipse(draw, body_cx - 1, head_cy + 1, 6, 6, HOOD_DARK)

        # Face (partial, facing left)
        ellipse(draw, body_cx - 3, head_cy + 2, 3, 3, FACE_DARK)
        # One visible eye
        draw.rectangle([body_cx - 5, head_cy + 1, body_cx - 2, head_cy + 3], fill=EYE_GLOW)
        draw.point((body_cx - 3, head_cy + 2), fill=EYE_CORE)

        # Hood peak
        draw.polygon([
            (body_cx - 3, head_cy - 9),
            (body_cx + 1, head_cy - 9),
            (body_cx, head_cy - 5),
            (body_cx - 2, head_cy - 5),
        ], fill=HOOD, outline=OUTLINE)

    elif direction == RIGHT:
        # --- Wispy trail (flowing left/behind) ---
        for i in range(2):
            trail_x = body_cx - 2 - i * 3 + wisp_sway
            trail_y = body_cy + 5
            trail_len = 5 + i * 2
            draw.line([(trail_x, trail_y), (trail_x - abs(wisp_sway) - 1, trail_y + trail_len)],
                     fill=CLOAK_DARK, width=2)

        # --- Body ---
        draw.polygon([
            (body_cx - 6, body_cy - 4),
            (body_cx + 6, body_cy - 4),
            (body_cx + 8, body_cy + 6),
            (body_cx - 8, body_cy + 6),
        ], fill=CLOAK, outline=OUTLINE)
        draw.polygon([
            (body_cx - 4, body_cy - 3),
            (body_cx + 4, body_cy - 3),
            (body_cx + 5, body_cy + 4),
            (body_cx - 5, body_cy + 4),
        ], fill=CLOAK_LIGHT, outline=None)

        # --- Hood (side view facing right) ---
        ellipse(draw, body_cx + 1, head_cy, 8, 8, HOOD)
        ellipse(draw, body_cx + 1, head_cy + 1, 6, 6, HOOD_DARK)

        # Face (partial, facing right)
        ellipse(draw, body_cx + 3, head_cy + 2, 3, 3, FACE_DARK)
        # One visible eye
        draw.rectangle([body_cx + 2, head_cy + 1, body_cx + 5, head_cy + 3], fill=EYE_GLOW)
        draw.point((body_cx + 3, head_cy + 2), fill=EYE_CORE)

        # Hood peak
        draw.polygon([
            (body_cx - 1, head_cy - 9),
            (body_cx + 3, head_cy - 9),
            (body_cx + 2, head_cy - 5),
            (body_cx, head_cy - 5),
        ], fill=HOOD, outline=OUTLINE)


def main():
    img = Image.new("RGBA", (IMG_W, IMG_H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    for direction in range(ROWS):
        for frame in range(COLS):
            ox = frame * FRAME_SIZE
            oy = direction * FRAME_SIZE
            draw_wraith(draw, ox, oy, direction, frame)

    img.save("sprites/wraith.png")
    print(f"Generated sprites/wraith.png ({IMG_W}x{IMG_H})")


if __name__ == "__main__":
    main()
