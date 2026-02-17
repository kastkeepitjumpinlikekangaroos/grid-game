#!/usr/bin/env python3
"""Generate sprites/wraith.png — 4-column x 4-row character spritesheet.

256x256 PNG, 64x64 per frame.
Row layout: Down=0, Up=1, Left=2, Right=3
4 walking animation frames per direction.

Style matches Spaceman/Gladiator: big round head, round body, small limbs.
Theme: Spectral wraith — hooded figure, floating (no visible legs), trailing wisps.
Enhanced 64x64: tattered robe edges, ghostly particle trail, hollow eye sockets
with inner glow rings, wispy tendrils extending from body.
"""

from PIL import Image, ImageDraw

FRAME_SIZE = 64
COLS = 4
ROWS = 4
IMG_W = FRAME_SIZE * COLS   # 256
IMG_H = FRAME_SIZE * ROWS   # 256

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
EYE_RING = (100, 200, 160)
WISP = (80, 180, 140, 160)
WISP_BRIGHT = (120, 220, 180, 200)
BLACK = (20, 25, 22)
TEAL_GLOW = (60, 160, 130)
WISP_FAINT = (60, 150, 120, 90)
WISP_DIM = (50, 130, 100, 70)
FACE_BRIGHT = (160, 255, 210)
NOSE_GLOW = (100, 200, 160)
SHOULDER_GLOW = (70, 170, 135, 140)
TENDRIL = (50, 140, 110, 120)
TENDRIL_TIP = (70, 170, 135, 80)
GHOST_PARTICLE = (90, 190, 150, 100)

DOWN, UP, LEFT, RIGHT = 0, 1, 2, 3


def ellipse(draw, cx, cy, rx, ry, fill, outline=OUTLINE):
    """Draw an outlined ellipse centered at (cx, cy)."""
    draw.ellipse([cx - rx, cy - ry, cx + rx, cy + ry], fill=fill, outline=outline)


def draw_wispy_trail_down(draw, body_cx, body_cy, frame, wisp_sway):
    """Draw dramatic wispy trails below the body for DOWN direction."""
    strand_offsets = [-12, -6, 0, 6, 12]
    strand_lengths = [14, 18, 22, 16, 12]
    strand_widths = [4, 6, 6, 6, 4]
    frame_shifts = [
        [0, 2, -2, 0, 2],
        [2, -2, 0, 2, -2],
        [-2, 0, 2, -2, 0],
        [0, -2, 2, 0, -2],
    ]
    shifts = frame_shifts[frame]

    for i, (sx, slen, sw) in enumerate(zip(strand_offsets, strand_lengths, strand_widths)):
        tx = body_cx + sx + wisp_sway + shifts[i]
        ty = body_cy + 12
        alpha = 130 - i * 15
        col = (50 + i * 8, 130 + i * 10, 100 + i * 8, alpha)
        draw.polygon([
            (tx - sw // 2, ty),
            (tx + sw // 2 + 2, ty),
            (tx + wisp_sway + shifts[i], ty + slen),
            (tx + wisp_sway + shifts[i] - 2, ty + slen),
        ], fill=col)
        draw.point((tx + wisp_sway + shifts[i], ty + slen - 2), fill=WISP)
        draw.point((tx + wisp_sway + shifts[i] - 1, ty + slen - 1), fill=WISP)


def draw_wispy_trail_up(draw, body_cx, body_cy, frame, wisp_sway):
    """Draw dramatic wispy trails below the body for UP direction."""
    strand_offsets = [-10, -4, 2, 8, 14]
    strand_lengths = [12, 18, 20, 16, 14]
    strand_widths = [4, 6, 6, 4, 4]
    frame_shifts = [
        [2, 0, -2, 2, 0],
        [-2, 2, 0, -2, 2],
        [0, -2, 2, 0, -2],
        [2, 0, -2, 2, -2],
    ]
    shifts = frame_shifts[frame]

    for i, (sx, slen, sw) in enumerate(zip(strand_offsets, strand_lengths, strand_widths)):
        tx = body_cx + sx + wisp_sway + shifts[i]
        ty = body_cy + 12
        alpha = 120 - i * 12
        col = (45 + i * 7, 120 + i * 10, 95 + i * 7, alpha)
        draw.polygon([
            (tx - sw // 2, ty),
            (tx + sw // 2 + 2, ty),
            (tx + wisp_sway + shifts[i], ty + slen),
            (tx + wisp_sway + shifts[i] - 2, ty + slen),
        ], fill=col)
        draw.point((tx + wisp_sway + shifts[i], ty + slen - 2), fill=WISP_FAINT)


def draw_wispy_trail_side(draw, body_cx, body_cy, frame, wisp_sway, facing_left):
    """Draw dramatic wispy trails for LEFT/RIGHT directions."""
    drift = 1 if facing_left else -1
    strand_offsets = [-6, 0, 6, 10]
    strand_lengths = [10, 16, 18, 12]
    strand_widths = [4, 6, 6, 4]
    frame_shifts = [
        [0, 2, -2, 0],
        [2, -2, 0, 2],
        [-2, 0, 2, -2],
        [0, -2, 2, 0],
    ]
    shifts = frame_shifts[frame]

    for i, (sx, slen, sw) in enumerate(zip(strand_offsets, strand_lengths, strand_widths)):
        tx = body_cx + sx * drift + wisp_sway + shifts[i]
        ty = body_cy + 10
        alpha = 120 - i * 15
        col = (50 + i * 8, 130 + i * 10, 100 + i * 8, alpha)
        end_drift = drift * (4 + i * 2) + wisp_sway
        draw.polygon([
            (tx - sw // 2, ty),
            (tx + sw // 2 + 2, ty),
            (tx + end_drift + 2, ty + slen),
            (tx + end_drift, ty + slen),
        ], fill=col)
        draw.point((tx + end_drift, ty + slen - 2), fill=WISP_FAINT)


def draw_shimmer_glow(draw, body_cx, body_cy, head_cy, frame, direction):
    """Draw scattered glow pixels around the body outline that shift per frame."""
    glow_sets = [
        [(-18, -4), (18, 0), (-14, 8), (14, 10), (-6, -16), (8, -14), (0, 14)],
        [(-20, 0), (16, -2), (-12, 10), (16, 6), (-8, -18), (6, -12), (2, 12)],
        [(-16, -2), (20, 2), (-16, 6), (12, 12), (-4, -14), (10, -16), (-2, 14)],
        [(-18, 2), (18, -4), (-14, 12), (14, 8), (-10, -16), (4, -14), (0, 16)],
    ]
    for gx, gy in glow_sets[frame]:
        px = body_cx + gx
        py = body_cy + gy
        draw.point((px, py), fill=WISP)
        draw.point((px + 1, py), fill=WISP_DIM)


def draw_shoulder_vapors(draw, body_cx, body_cy, frame, direction):
    """Draw wispy shoulder glow pixels near shoulder areas."""
    vapor_sets = [
        [(-20, -6), (-22, -4), (20, -6), (22, -4)],
        [(-22, -8), (-20, -4), (22, -8), (20, -4)],
        [(-20, -8), (-22, -6), (20, -8), (22, -6)],
        [(-22, -6), (-20, -8), (22, -6), (20, -8)],
    ]
    if direction == LEFT:
        vapors = [v for v in vapor_sets[frame] if v[0] > 0]
        vapors += [(v[0] - 4, v[1]) for v in vapor_sets[frame] if v[0] < 0]
    elif direction == RIGHT:
        vapors = [v for v in vapor_sets[frame] if v[0] < 0]
        vapors += [(v[0] + 4, v[1]) for v in vapor_sets[frame] if v[0] > 0]
    else:
        vapors = vapor_sets[frame]

    for vx, vy in vapors:
        px = body_cx + vx
        py = body_cy + vy
        draw.point((px, py), fill=SHOULDER_GLOW)
        draw.point((px + 1, py), fill=SHOULDER_GLOW)


def draw_tendrils(draw, body_cx, body_cy, frame, direction):
    """Draw wispy tendrils extending from body sides."""
    tendril_sway = [-2, 0, 2, 0][frame]
    if direction in (DOWN, UP):
        # Left tendril
        draw.line([(body_cx - 16, body_cy), (body_cx - 22 + tendril_sway, body_cy + 8)],
                  fill=TENDRIL, width=2)
        draw.point((body_cx - 22 + tendril_sway, body_cy + 8), fill=TENDRIL_TIP)
        # Right tendril
        draw.line([(body_cx + 16, body_cy), (body_cx + 22 - tendril_sway, body_cy + 8)],
                  fill=TENDRIL, width=2)
        draw.point((body_cx + 22 - tendril_sway, body_cy + 8), fill=TENDRIL_TIP)
    elif direction == LEFT:
        draw.line([(body_cx + 10, body_cy), (body_cx + 18 + tendril_sway, body_cy + 6)],
                  fill=TENDRIL, width=2)
        draw.point((body_cx + 18 + tendril_sway, body_cy + 6), fill=TENDRIL_TIP)
    elif direction == RIGHT:
        draw.line([(body_cx - 10, body_cy), (body_cx - 18 - tendril_sway, body_cy + 6)],
                  fill=TENDRIL, width=2)
        draw.point((body_cx - 18 - tendril_sway, body_cy + 6), fill=TENDRIL_TIP)


def draw_ghost_particles(draw, body_cx, body_cy, frame):
    """Draw translucent ghostly particle dots trailing behind."""
    particle_sets = [
        [(-4, 16), (6, 18), (-8, 14), (10, 16), (0, 20)],
        [(-6, 18), (4, 16), (-10, 16), (8, 18), (2, 22)],
        [(-2, 14), (8, 20), (-6, 18), (12, 14), (-4, 22)],
        [(-8, 16), (2, 14), (-4, 20), (6, 16), (10, 20)],
    ]
    for px, py in particle_sets[frame]:
        draw.point((body_cx + px, body_cy + py), fill=GHOST_PARTICLE)


def draw_wraith(draw, ox, oy, direction, frame):
    """Draw a single wraith frame at offset (ox, oy)."""
    bob = [0, -4, -2, -4][frame]
    wisp_sway = [-2, 2, -2, 0][frame]

    base_y = oy + 54 + bob
    body_cx = ox + 32
    body_cy = base_y - 20
    head_cy = body_cy - 20

    if direction == DOWN:
        draw_wispy_trail_down(draw, body_cx, body_cy, frame, wisp_sway)
        draw_ghost_particles(draw, body_cx, body_cy, frame)

        # --- Body (flowing cloak shape, ragged tattered edges) ---
        ragged = [(-2, 0), (2, -2), (0, 2), (-2, 0), (2, -2)][frame % 5]
        draw.polygon([
            (body_cx - 16, body_cy - 8),
            (body_cx + 16, body_cy - 8),
            (body_cx + 22, body_cy + 10),
            (body_cx + 24 + ragged[0], body_cy + 14 + ragged[1]),
            (body_cx + 16, body_cy + 12),
            (body_cx + 10, body_cy + 16),
            (body_cx + 4, body_cy + 14),
            (body_cx - 4, body_cy + 16),
            (body_cx - 10, body_cy + 14),
            (body_cx - 16, body_cy + 16),
            (body_cx - 24 - ragged[0], body_cy + 14 - ragged[1]),
            (body_cx - 22, body_cy + 10),
        ], fill=CLOAK, outline=OUTLINE)
        draw.polygon([
            (body_cx - 10, body_cy - 6),
            (body_cx + 10, body_cy - 6),
            (body_cx + 12, body_cy + 8),
            (body_cx - 12, body_cy + 8),
        ], fill=CLOAK_LIGHT, outline=None)

        draw_tendrils(draw, body_cx, body_cy, frame, direction)

        # --- Hood ---
        ellipse(draw, body_cx, head_cy, 18, 16, HOOD)
        ellipse(draw, body_cx, head_cy + 2, 14, 12, HOOD_DARK)

        # --- Face ---
        ellipse(draw, body_cx, head_cy + 4, 8, 6, FACE_GLOW)
        ellipse(draw, body_cx, head_cy + 4, 6, 4, FACE_BRIGHT)
        # Hollow eye sockets with glow rings
        draw.rectangle([body_cx - 8, head_cy + 2, body_cx - 2, head_cy + 6], fill=EYE_GLOW)
        draw.rectangle([body_cx + 2, head_cy + 2, body_cx + 8, head_cy + 6], fill=EYE_GLOW)
        # Inner dark hollow
        draw.rectangle([body_cx - 6, head_cy + 3, body_cx - 4, head_cy + 5], fill=BLACK)
        draw.rectangle([body_cx + 4, head_cy + 3, body_cx + 6, head_cy + 5], fill=BLACK)
        # Eye ring glow
        draw.arc([body_cx - 9, head_cy + 1, body_cx - 1, head_cy + 7], start=0, end=360, fill=EYE_RING)
        draw.arc([body_cx + 1, head_cy + 1, body_cx + 9, head_cy + 7], start=0, end=360, fill=EYE_RING)
        draw.point((body_cx - 4, head_cy + 4), fill=EYE_CORE)
        draw.point((body_cx + 4, head_cy + 4), fill=EYE_CORE)
        draw.point((body_cx, head_cy + 8), fill=NOSE_GLOW)
        draw.point((body_cx, head_cy + 10), fill=FACE_DARK)

        # --- Hood peak ---
        draw.polygon([
            (body_cx, head_cy - 24),
            (body_cx + 6, head_cy - 14),
            (body_cx + 4, head_cy - 10),
            (body_cx - 4, head_cy - 10),
            (body_cx - 6, head_cy - 14),
        ], fill=HOOD, outline=OUTLINE)

        draw_shoulder_vapors(draw, body_cx, body_cy, frame, direction)
        draw_shimmer_glow(draw, body_cx, body_cy, head_cy, frame, direction)

    elif direction == UP:
        draw_wispy_trail_up(draw, body_cx, body_cy, frame, wisp_sway)
        draw_ghost_particles(draw, body_cx, body_cy, frame)

        ragged = [(0, 2), (2, 0), (-2, 2), (0, -2), (2, 0)][frame % 5]
        draw.polygon([
            (body_cx - 16, body_cy - 8),
            (body_cx + 16, body_cy - 8),
            (body_cx + 22, body_cy + 10),
            (body_cx + 24 + ragged[0], body_cy + 14 + ragged[1]),
            (body_cx + 10, body_cy + 12),
            (body_cx + 2, body_cy + 16),
            (body_cx - 6, body_cy + 14),
            (body_cx - 14, body_cy + 16),
            (body_cx - 24 - ragged[0], body_cy + 14 - ragged[1]),
            (body_cx - 22, body_cy + 10),
        ], fill=CLOAK, outline=OUTLINE)
        draw.polygon([
            (body_cx - 12, body_cy - 6),
            (body_cx + 12, body_cy - 6),
            (body_cx + 14, body_cy + 10),
            (body_cx - 14, body_cy + 10),
        ], fill=CLOAK_DARK, outline=None)

        draw_tendrils(draw, body_cx, body_cy, frame, direction)

        ellipse(draw, body_cx, head_cy, 18, 16, HOOD)
        ellipse(draw, body_cx, head_cy, 14, 12, HOOD_DARK)

        draw.polygon([
            (body_cx, head_cy - 24),
            (body_cx + 6, head_cy - 14),
            (body_cx + 4, head_cy - 10),
            (body_cx - 4, head_cy - 10),
            (body_cx - 6, head_cy - 14),
        ], fill=HOOD, outline=OUTLINE)

        draw_shoulder_vapors(draw, body_cx, body_cy, frame, direction)
        draw_shimmer_glow(draw, body_cx, body_cy, head_cy, frame, direction)

    elif direction == LEFT:
        draw_wispy_trail_side(draw, body_cx, body_cy, frame, wisp_sway, facing_left=True)
        draw_ghost_particles(draw, body_cx, body_cy, frame)

        ragged = [(2, 0), (0, 2), (-2, 0), (2, -2), (0, 2)][frame % 5]
        draw.polygon([
            (body_cx - 12, body_cy - 8),
            (body_cx + 12, body_cy - 8),
            (body_cx + 18, body_cy + 10),
            (body_cx + 20 + ragged[0], body_cy + 14 + ragged[1]),
            (body_cx + 8, body_cy + 12),
            (body_cx, body_cy + 16),
            (body_cx - 10, body_cy + 14),
            (body_cx - 20 - ragged[0], body_cy + 14 - ragged[1]),
            (body_cx - 18, body_cy + 10),
        ], fill=CLOAK, outline=OUTLINE)
        draw.polygon([
            (body_cx - 8, body_cy - 6),
            (body_cx + 8, body_cy - 6),
            (body_cx + 10, body_cy + 8),
            (body_cx - 10, body_cy + 8),
        ], fill=CLOAK_LIGHT, outline=None)

        draw_tendrils(draw, body_cx, body_cy, frame, direction)

        ellipse(draw, body_cx - 2, head_cy, 16, 16, HOOD)
        ellipse(draw, body_cx - 2, head_cy + 2, 12, 12, HOOD_DARK)

        ellipse(draw, body_cx - 6, head_cy + 4, 6, 6, FACE_GLOW)
        ellipse(draw, body_cx - 6, head_cy + 4, 4, 4, FACE_BRIGHT)
        draw.rectangle([body_cx - 10, head_cy + 2, body_cx - 4, head_cy + 6], fill=EYE_GLOW)
        draw.rectangle([body_cx - 8, head_cy + 3, body_cx - 6, head_cy + 5], fill=BLACK)
        draw.arc([body_cx - 11, head_cy + 1, body_cx - 3, head_cy + 7], start=0, end=360, fill=EYE_RING)
        draw.point((body_cx - 6, head_cy + 4), fill=EYE_CORE)
        draw.point((body_cx - 8, head_cy + 8), fill=NOSE_GLOW)

        draw.polygon([
            (body_cx - 2, head_cy - 24),
            (body_cx + 4, head_cy - 14),
            (body_cx + 2, head_cy - 10),
            (body_cx - 4, head_cy - 10),
            (body_cx - 6, head_cy - 14),
        ], fill=HOOD, outline=OUTLINE)

        draw_shoulder_vapors(draw, body_cx, body_cy, frame, direction)
        draw_shimmer_glow(draw, body_cx, body_cy, head_cy, frame, direction)

    elif direction == RIGHT:
        draw_wispy_trail_side(draw, body_cx, body_cy, frame, wisp_sway, facing_left=False)
        draw_ghost_particles(draw, body_cx, body_cy, frame)

        ragged = [(0, 2), (2, 0), (0, -2), (-2, 2), (2, 0)][frame % 5]
        draw.polygon([
            (body_cx - 12, body_cy - 8),
            (body_cx + 12, body_cy - 8),
            (body_cx + 18, body_cy + 10),
            (body_cx + 20 + ragged[0], body_cy + 14 + ragged[1]),
            (body_cx + 10, body_cy + 14),
            (body_cx, body_cy + 16),
            (body_cx - 8, body_cy + 12),
            (body_cx - 20 - ragged[0], body_cy + 14 - ragged[1]),
            (body_cx - 18, body_cy + 10),
        ], fill=CLOAK, outline=OUTLINE)
        draw.polygon([
            (body_cx - 8, body_cy - 6),
            (body_cx + 8, body_cy - 6),
            (body_cx + 10, body_cy + 8),
            (body_cx - 10, body_cy + 8),
        ], fill=CLOAK_LIGHT, outline=None)

        draw_tendrils(draw, body_cx, body_cy, frame, direction)

        ellipse(draw, body_cx + 2, head_cy, 16, 16, HOOD)
        ellipse(draw, body_cx + 2, head_cy + 2, 12, 12, HOOD_DARK)

        ellipse(draw, body_cx + 6, head_cy + 4, 6, 6, FACE_GLOW)
        ellipse(draw, body_cx + 6, head_cy + 4, 4, 4, FACE_BRIGHT)
        draw.rectangle([body_cx + 4, head_cy + 2, body_cx + 10, head_cy + 6], fill=EYE_GLOW)
        draw.rectangle([body_cx + 6, head_cy + 3, body_cx + 8, head_cy + 5], fill=BLACK)
        draw.arc([body_cx + 3, head_cy + 1, body_cx + 11, head_cy + 7], start=0, end=360, fill=EYE_RING)
        draw.point((body_cx + 6, head_cy + 4), fill=EYE_CORE)
        draw.point((body_cx + 8, head_cy + 8), fill=NOSE_GLOW)

        draw.polygon([
            (body_cx + 2, head_cy - 24),
            (body_cx + 6, head_cy - 14),
            (body_cx + 4, head_cy - 10),
            (body_cx - 2, head_cy - 10),
            (body_cx - 4, head_cy - 14),
        ], fill=HOOD, outline=OUTLINE)

        draw_shoulder_vapors(draw, body_cx, body_cy, frame, direction)
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
