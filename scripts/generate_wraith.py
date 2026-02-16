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
# Extra glow colors for enhanced effects
WISP_FAINT = (60, 150, 120, 90)
WISP_DIM = (50, 130, 100, 70)
FACE_BRIGHT = (160, 255, 210)
NOSE_GLOW = (100, 200, 160)
SHOULDER_GLOW = (70, 170, 135, 140)

DOWN, UP, LEFT, RIGHT = 0, 1, 2, 3


def ellipse(draw, cx, cy, rx, ry, fill, outline=OUTLINE):
    """Draw an outlined ellipse centered at (cx, cy)."""
    draw.ellipse([cx - rx, cy - ry, cx + rx, cy + ry], fill=fill, outline=outline)


def draw_wispy_trail_down(draw, body_cx, body_cy, frame, wisp_sway):
    """Draw dramatic wispy trails below the body for DOWN direction."""
    # 5 wispy strands with varying lengths, wider polygon shapes
    strand_offsets = [-6, -3, 0, 3, 6]
    strand_lengths = [7, 9, 11, 8, 6]
    strand_widths = [2, 3, 3, 3, 2]
    # Each frame shifts strands slightly differently for organic motion
    frame_shifts = [
        [0, 1, -1, 0, 1],
        [1, -1, 0, 1, -1],
        [-1, 0, 1, -1, 0],
        [0, -1, 1, 0, -1],
    ]
    shifts = frame_shifts[frame]

    for i, (sx, slen, sw) in enumerate(zip(strand_offsets, strand_lengths, strand_widths)):
        tx = body_cx + sx + wisp_sway + shifts[i]
        ty = body_cy + 6
        # Draw as a tapered polygon for width
        alpha = 130 - i * 15
        col = (50 + i * 8, 130 + i * 10, 100 + i * 8, alpha)
        draw.polygon([
            (tx - sw // 2, ty),
            (tx + sw // 2 + 1, ty),
            (tx + wisp_sway + shifts[i], ty + slen),
            (tx + wisp_sway + shifts[i] - 1, ty + slen),
        ], fill=col)
        # Bright wisp tip
        draw.point((tx + wisp_sway + shifts[i], ty + slen - 1), fill=WISP)


def draw_wispy_trail_up(draw, body_cx, body_cy, frame, wisp_sway):
    """Draw dramatic wispy trails below the body for UP direction."""
    strand_offsets = [-5, -2, 1, 4, 7]
    strand_lengths = [6, 9, 10, 8, 7]
    strand_widths = [2, 3, 3, 2, 2]
    frame_shifts = [
        [1, 0, -1, 1, 0],
        [-1, 1, 0, -1, 1],
        [0, -1, 1, 0, -1],
        [1, 0, -1, 1, -1],
    ]
    shifts = frame_shifts[frame]

    for i, (sx, slen, sw) in enumerate(zip(strand_offsets, strand_lengths, strand_widths)):
        tx = body_cx + sx + wisp_sway + shifts[i]
        ty = body_cy + 6
        alpha = 120 - i * 12
        col = (45 + i * 7, 120 + i * 10, 95 + i * 7, alpha)
        draw.polygon([
            (tx - sw // 2, ty),
            (tx + sw // 2 + 1, ty),
            (tx + wisp_sway + shifts[i], ty + slen),
            (tx + wisp_sway + shifts[i] - 1, ty + slen),
        ], fill=col)
        draw.point((tx + wisp_sway + shifts[i], ty + slen - 1), fill=WISP_FAINT)


def draw_wispy_trail_side(draw, body_cx, body_cy, frame, wisp_sway, facing_left):
    """Draw dramatic wispy trails for LEFT/RIGHT directions."""
    # Trails flow opposite to facing direction
    drift = 1 if facing_left else -1
    strand_offsets = [-3, 0, 3, 5]
    strand_lengths = [5, 8, 9, 6]
    strand_widths = [2, 3, 3, 2]
    frame_shifts = [
        [0, 1, -1, 0],
        [1, -1, 0, 1],
        [-1, 0, 1, -1],
        [0, -1, 1, 0],
    ]
    shifts = frame_shifts[frame]

    for i, (sx, slen, sw) in enumerate(zip(strand_offsets, strand_lengths, strand_widths)):
        tx = body_cx + sx * drift + wisp_sway + shifts[i]
        ty = body_cy + 5
        alpha = 120 - i * 15
        col = (50 + i * 8, 130 + i * 10, 100 + i * 8, alpha)
        end_drift = drift * (2 + i) + wisp_sway
        draw.polygon([
            (tx - sw // 2, ty),
            (tx + sw // 2 + 1, ty),
            (tx + end_drift + 1, ty + slen),
            (tx + end_drift, ty + slen),
        ], fill=col)
        draw.point((tx + end_drift, ty + slen - 1), fill=WISP_FAINT)


def draw_shimmer_glow(draw, body_cx, body_cy, head_cy, frame, direction):
    """Draw scattered glow pixels around the body outline that shift per frame."""
    # Positions shift based on frame for shimmer effect
    # These are offsets from body center
    glow_sets = [
        # frame 0
        [(-9, -2), (9, 0), (-7, 4), (7, 5), (-3, -8), (4, -7), (0, 7)],
        # frame 1
        [(-10, 0), (8, -1), (-6, 5), (8, 3), (-4, -9), (3, -6), (1, 6)],
        # frame 2
        [(-8, -1), (10, 1), (-8, 3), (6, 6), (-2, -7), (5, -8), (-1, 7)],
        # frame 3
        [(-9, 1), (9, -2), (-7, 6), (7, 4), (-5, -8), (2, -7), (0, 8)],
    ]
    for gx, gy in glow_sets[frame]:
        px = body_cx + gx
        py = body_cy + gy
        draw.point((px, py), fill=WISP)


def draw_shoulder_vapors(draw, body_cx, body_cy, frame, direction):
    """Draw wispy shoulder glow pixels near shoulder areas."""
    vapor_sets = [
        # frame 0
        [(-10, -3), (-11, -2), (10, -3), (11, -2)],
        # frame 1
        [(-11, -4), (-10, -2), (11, -4), (10, -2)],
        # frame 2
        [(-10, -4), (-11, -3), (10, -4), (11, -3)],
        # frame 3
        [(-11, -3), (-10, -4), (11, -3), (10, -4)],
    ]
    if direction == LEFT:
        vapors = [v for v in vapor_sets[frame] if v[0] > 0]  # trailing side
        vapors += [(v[0] - 2, v[1]) for v in vapor_sets[frame] if v[0] < 0]
    elif direction == RIGHT:
        vapors = [v for v in vapor_sets[frame] if v[0] < 0]  # trailing side
        vapors += [(v[0] + 2, v[1]) for v in vapor_sets[frame] if v[0] > 0]
    else:
        vapors = vapor_sets[frame]

    for vx, vy in vapors:
        px = body_cx + vx
        py = body_cy + vy
        draw.point((px, py), fill=SHOULDER_GLOW)


def draw_wraith(draw, ox, oy, direction, frame):
    """Draw a single wraith frame at offset (ox, oy).

    Proportions match Spaceman: big round head ~11px, body ~8px tall.
    Wraith floats — bottom of frame has wispy trail instead of legs.
    """
    # More pronounced floating bob animation
    bob = [0, -2, -1, -2][frame]
    wisp_sway = [-1, 1, -1, 0][frame]

    # Anchor: character floats slightly above ground
    base_y = oy + 27 + bob
    body_cx = ox + 16
    body_cy = base_y - 10
    head_cy = body_cy - 10

    if direction == DOWN:
        # --- Dramatic wispy trail ---
        draw_wispy_trail_down(draw, body_cx, body_cy, frame, wisp_sway)

        # --- Body (flowing cloak shape, wider at base with ragged edge) ---
        # Main cloak body — wider taper at bottom, ragged bottom edge
        ragged = [(-1, 0), (1, -1), (0, 1), (-1, 0), (1, -1)][frame % 5]
        draw.polygon([
            (body_cx - 8, body_cy - 4),
            (body_cx + 8, body_cy - 4),
            (body_cx + 11, body_cy + 5),
            (body_cx + 12 + ragged[0], body_cy + 7 + ragged[1]),
            (body_cx + 6, body_cy + 6),
            (body_cx + 2, body_cy + 8),
            (body_cx - 2, body_cy + 7),
            (body_cx - 6, body_cy + 8),
            (body_cx - 12 - ragged[0], body_cy + 7 - ragged[1]),
            (body_cx - 11, body_cy + 5),
        ], fill=CLOAK, outline=OUTLINE)
        # Cloak highlight
        draw.polygon([
            (body_cx - 5, body_cy - 3),
            (body_cx + 5, body_cy - 3),
            (body_cx + 6, body_cy + 4),
            (body_cx - 6, body_cy + 4),
        ], fill=CLOAK_LIGHT, outline=None)

        # --- Hood (big, rounded, covering head, TALLER peak) ---
        ellipse(draw, body_cx, head_cy, 9, 8, HOOD)
        # Hood inner shadow
        ellipse(draw, body_cx, head_cy + 1, 7, 6, HOOD_DARK)

        # --- Face (brighter glowing from within the hood) ---
        ellipse(draw, body_cx, head_cy + 2, 4, 3, FACE_GLOW)  # brighter inner face
        ellipse(draw, body_cx, head_cy + 2, 3, 2, FACE_BRIGHT)  # even brighter core
        # Glowing eyes
        draw.rectangle([body_cx - 4, head_cy + 1, body_cx - 1, head_cy + 3], fill=EYE_GLOW)
        draw.rectangle([body_cx + 1, head_cy + 1, body_cx + 4, head_cy + 3], fill=EYE_GLOW)
        # Eye cores (bright center)
        draw.point((body_cx - 2, head_cy + 2), fill=EYE_CORE)
        draw.point((body_cx + 2, head_cy + 2), fill=EYE_CORE)
        # Nose/mouth glow dot below eyes
        draw.point((body_cx, head_cy + 4), fill=NOSE_GLOW)
        draw.point((body_cx, head_cy + 5), fill=FACE_DARK)

        # --- Hood peak (TALLER and sharper) ---
        draw.polygon([
            (body_cx, head_cy - 12),
            (body_cx + 3, head_cy - 7),
            (body_cx + 2, head_cy - 5),
            (body_cx - 2, head_cy - 5),
            (body_cx - 3, head_cy - 7),
        ], fill=HOOD, outline=OUTLINE)

        # --- Shoulder vapors ---
        draw_shoulder_vapors(draw, body_cx, body_cy, frame, direction)

        # --- Shimmer glow around body ---
        draw_shimmer_glow(draw, body_cx, body_cy, head_cy, frame, direction)

    elif direction == UP:
        # --- Dramatic wispy trail ---
        draw_wispy_trail_up(draw, body_cx, body_cy, frame, wisp_sway)

        # --- Body (wider base, ragged edge) ---
        ragged = [(0, 1), (1, 0), (-1, 1), (0, -1), (1, 0)][frame % 5]
        draw.polygon([
            (body_cx - 8, body_cy - 4),
            (body_cx + 8, body_cy - 4),
            (body_cx + 11, body_cy + 5),
            (body_cx + 12 + ragged[0], body_cy + 7 + ragged[1]),
            (body_cx + 5, body_cy + 6),
            (body_cx + 1, body_cy + 8),
            (body_cx - 3, body_cy + 7),
            (body_cx - 7, body_cy + 8),
            (body_cx - 12 - ragged[0], body_cy + 7 - ragged[1]),
            (body_cx - 11, body_cy + 5),
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

        # --- Hood peak (TALLER and sharper) ---
        draw.polygon([
            (body_cx, head_cy - 12),
            (body_cx + 3, head_cy - 7),
            (body_cx + 2, head_cy - 5),
            (body_cx - 2, head_cy - 5),
            (body_cx - 3, head_cy - 7),
        ], fill=HOOD, outline=OUTLINE)

        # --- Shoulder vapors ---
        draw_shoulder_vapors(draw, body_cx, body_cy, frame, direction)

        # --- Shimmer glow ---
        draw_shimmer_glow(draw, body_cx, body_cy, head_cy, frame, direction)

    elif direction == LEFT:
        # --- Dramatic wispy trail (flowing right/behind) ---
        draw_wispy_trail_side(draw, body_cx, body_cy, frame, wisp_sway, facing_left=True)

        # --- Body (wider base, ragged bottom) ---
        ragged = [(1, 0), (0, 1), (-1, 0), (1, -1), (0, 1)][frame % 5]
        draw.polygon([
            (body_cx - 6, body_cy - 4),
            (body_cx + 6, body_cy - 4),
            (body_cx + 9, body_cy + 5),
            (body_cx + 10 + ragged[0], body_cy + 7 + ragged[1]),
            (body_cx + 4, body_cy + 6),
            (body_cx, body_cy + 8),
            (body_cx - 5, body_cy + 7),
            (body_cx - 10 - ragged[0], body_cy + 7 - ragged[1]),
            (body_cx - 9, body_cy + 5),
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

        # Face (partial, facing left) - brighter
        ellipse(draw, body_cx - 3, head_cy + 2, 3, 3, FACE_GLOW)
        ellipse(draw, body_cx - 3, head_cy + 2, 2, 2, FACE_BRIGHT)
        # One visible eye
        draw.rectangle([body_cx - 5, head_cy + 1, body_cx - 2, head_cy + 3], fill=EYE_GLOW)
        draw.point((body_cx - 3, head_cy + 2), fill=EYE_CORE)
        # Nose/mouth glow
        draw.point((body_cx - 4, head_cy + 4), fill=NOSE_GLOW)

        # Hood peak (taller, sharper)
        draw.polygon([
            (body_cx - 1, head_cy - 12),
            (body_cx + 2, head_cy - 7),
            (body_cx + 1, head_cy - 5),
            (body_cx - 2, head_cy - 5),
            (body_cx - 3, head_cy - 7),
        ], fill=HOOD, outline=OUTLINE)

        # --- Shoulder vapors ---
        draw_shoulder_vapors(draw, body_cx, body_cy, frame, direction)

        # --- Shimmer glow ---
        draw_shimmer_glow(draw, body_cx, body_cy, head_cy, frame, direction)

    elif direction == RIGHT:
        # --- Dramatic wispy trail (flowing left/behind) ---
        draw_wispy_trail_side(draw, body_cx, body_cy, frame, wisp_sway, facing_left=False)

        # --- Body (wider base, ragged bottom) ---
        ragged = [(0, 1), (1, 0), (0, -1), (-1, 1), (1, 0)][frame % 5]
        draw.polygon([
            (body_cx - 6, body_cy - 4),
            (body_cx + 6, body_cy - 4),
            (body_cx + 9, body_cy + 5),
            (body_cx + 10 + ragged[0], body_cy + 7 + ragged[1]),
            (body_cx + 5, body_cy + 7),
            (body_cx, body_cy + 8),
            (body_cx - 4, body_cy + 6),
            (body_cx - 10 - ragged[0], body_cy + 7 - ragged[1]),
            (body_cx - 9, body_cy + 5),
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

        # Face (partial, facing right) - brighter
        ellipse(draw, body_cx + 3, head_cy + 2, 3, 3, FACE_GLOW)
        ellipse(draw, body_cx + 3, head_cy + 2, 2, 2, FACE_BRIGHT)
        # One visible eye
        draw.rectangle([body_cx + 2, head_cy + 1, body_cx + 5, head_cy + 3], fill=EYE_GLOW)
        draw.point((body_cx + 3, head_cy + 2), fill=EYE_CORE)
        # Nose/mouth glow
        draw.point((body_cx + 4, head_cy + 4), fill=NOSE_GLOW)

        # Hood peak (taller, sharper)
        draw.polygon([
            (body_cx + 1, head_cy - 12),
            (body_cx + 3, head_cy - 7),
            (body_cx + 2, head_cy - 5),
            (body_cx - 1, head_cy - 5),
            (body_cx - 2, head_cy - 7),
        ], fill=HOOD, outline=OUTLINE)

        # --- Shoulder vapors ---
        draw_shoulder_vapors(draw, body_cx, body_cy, frame, direction)

        # --- Shimmer glow ---
        draw_shimmer_glow(draw, body_cx, body_cy, head_cy, frame, direction)


def main():
    img = Image.new("RGBA", (IMG_W, IMG_H), (0, 0, 0, 0))

    for direction in range(ROWS):
        for frame in range(COLS):
            frame_img = Image.new("RGBA", (FRAME_SIZE, FRAME_SIZE), (0, 0, 0, 0))
            frame_draw = ImageDraw.Draw(frame_img)
            draw_wraith(frame_draw, 0, 0, direction, frame)
            img.paste(frame_img, (frame * FRAME_SIZE, direction * FRAME_SIZE))

    img.save("sprites/wraith.png")
    print(f"Generated sprites/wraith.png ({IMG_W}x{IMG_H})")


if __name__ == "__main__":
    main()
