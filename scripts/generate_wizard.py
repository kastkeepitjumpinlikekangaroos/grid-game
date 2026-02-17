#!/usr/bin/env python3
"""Generate sprites/wizard.png — 4-column x 4-row character spritesheet.

256x256 PNG, 64x64 per frame.
Row layout: Down=0, Up=1, Left=2, Right=3
4 walking animation frames per direction.

Style matches Spaceman/Gladiator/Wraith: big round head, round body, small limbs.
Theme: Wizard — curled pointed hat, flowing purple/indigo robes with star embroidery,
staff with pulsing glowing orb and magic sparkles, outlined beard, gold robe hem.
Enhanced 64x64: staff with wood grain, larger orb with inner swirl, zigzag robe hem,
5 sparkle particles, wider hat brim.
"""

from PIL import Image, ImageDraw

FRAME_SIZE = 64
COLS = 4
ROWS = 4
IMG_W = FRAME_SIZE * COLS   # 256
IMG_H = FRAME_SIZE * ROWS   # 256

# Colors
OUTLINE = (25, 20, 40)
ROBE_DARK = (40, 25, 70)
ROBE = (60, 35, 110)
ROBE_LIGHT = (80, 50, 140)
HAT = (50, 30, 90)
HAT_DARK = (35, 20, 65)
HAT_BAND = (200, 170, 50)      # Gold accent
HAT_BAND_DARK = (160, 130, 30)
FACE = (220, 190, 160)
FACE_SHADOW = (190, 160, 130)
BEARD = (200, 200, 210)
BEARD_DARK = (170, 170, 180)
BEARD_OUTLINE = (120, 120, 135)
EYE = (30, 25, 45)
STAFF_WOOD = (100, 65, 30)
STAFF_DARK = (70, 45, 20)
STAFF_GRAIN = (85, 55, 25)
ORB_GLOW = (120, 80, 220)
ORB_BRIGHT = (180, 140, 255)
ORB_CORE = (220, 200, 255)
ORB_PULSE = (240, 220, 255)
ORB_GLOW_SOFT = (100, 65, 200)
ORB_SWIRL = (160, 120, 240)
SPARKLE_A = (255, 255, 200)
SPARKLE_B = (200, 180, 255)
SPARKLE_C = (180, 255, 255)
SPARKLE_D = (255, 220, 180)
SPARKLE_E = (220, 200, 255)
HAND_GLOW = (150, 110, 255)
ROBE_STAR = (210, 185, 60)
ROBE_HEM = (190, 160, 45)
ROBE_HEM_DARK = (160, 130, 35)

DOWN, UP, LEFT, RIGHT = 0, 1, 2, 3


def ellipse(draw, cx, cy, rx, ry, fill, outline=OUTLINE):
    """Draw an outlined ellipse centered at (cx, cy)."""
    draw.ellipse([cx - rx, cy - ry, cx + rx, cy + ry], fill=fill, outline=outline)


# Per-frame sparkle offsets relative to the orb center (dx, dy).
# Each frame gets 5 sparkle positions that shift around to animate.
SPARKLE_OFFSETS = [
    [(-8, -4), (6, -8), (10, 2), (-4, 8), (8, -6)],    # frame 0
    [(-6, -10), (10, -2), (-2, 8), (4, -10), (-8, 4)],  # frame 1
    [(-10, 0), (4, -10), (8, 6), (-6, 6), (10, -4)],    # frame 2
    [(-4, -8), (10, -6), (-8, 6), (6, 8), (-10, -2)],   # frame 3
]

# Per-frame robe star offsets (dx, dy) relative to body center.
ROBE_STARS = [
    [(-6, -2), (4, 4), (0, 10), (-8, 6), (6, -4)],
    [(-8, 0), (6, 6), (-2, 10), (-4, 4), (8, 0)],
    [(-6, 2), (4, 6), (2, 10), (-8, 8), (6, 2)],
    [(-8, -2), (6, 4), (-2, 8), (-4, 6), (8, -2)],
]


def draw_sparkles(draw, orb_x, orb_y, frame):
    """Draw 5 magic sparkle pixels around the staff orb."""
    colors = [SPARKLE_A, SPARKLE_B, SPARKLE_C, SPARKLE_D, SPARKLE_E]
    for i, (dx, dy) in enumerate(SPARKLE_OFFSETS[frame]):
        draw.point((orb_x + dx, orb_y + dy), fill=colors[i])
        # Add a second pixel for sparkle cross shape
        draw.point((orb_x + dx + 1, orb_y + dy), fill=colors[i])
        draw.point((orb_x + dx, orb_y + dy + 1), fill=colors[i])


def draw_orb(draw, orb_x, orb_y, frame):
    """Draw the staff orb with pulsing brightness across frames, radius 6."""
    is_pulse = frame in (1, 3)
    if is_pulse:
        # Brighter/slightly larger orb on pulse frames
        ellipse(draw, orb_x, orb_y, 6, 6, ORB_BRIGHT)
        draw.rectangle([orb_x - 2, orb_y - 2, orb_x + 2, orb_y + 2], fill=ORB_PULSE)
        draw.point((orb_x, orb_y), fill=(255, 250, 255))
        # Inner swirl pattern
        draw.arc([orb_x - 4, orb_y - 4, orb_x + 4, orb_y + 4],
                 start=frame * 45, end=frame * 45 + 180, fill=ORB_SWIRL)
        # Extra glow pixels
        draw.point((orb_x, orb_y - 6), fill=ORB_CORE)
        draw.point((orb_x + 1, orb_y - 5), fill=ORB_CORE)
    else:
        # Normal orb
        ellipse(draw, orb_x, orb_y, 6, 6, ORB_GLOW)
        draw.rectangle([orb_x - 2, orb_y - 2, orb_x + 2, orb_y + 2], fill=ORB_BRIGHT)
        draw.point((orb_x, orb_y), fill=ORB_CORE)
        # Inner swirl pattern
        draw.arc([orb_x - 4, orb_y - 4, orb_x + 4, orb_y + 4],
                 start=frame * 45, end=frame * 45 + 120, fill=ORB_SWIRL)


def draw_hand_glow(draw, hand_x, hand_y):
    """Draw bright pixels near the hand holding the staff."""
    draw.point((hand_x, hand_y), fill=HAND_GLOW)
    draw.point((hand_x + 1, hand_y - 1), fill=(130, 95, 230))
    draw.point((hand_x + 2, hand_y - 2), fill=(130, 95, 230))
    draw.point((hand_x - 1, hand_y + 1), fill=(130, 95, 230))


def draw_robe_stars(draw, body_cx, body_cy, frame, direction):
    """Draw tiny gold dots on the robe body to look like embroidered stars."""
    offsets = ROBE_STARS[frame]
    for dx, dy in offsets:
        sx = body_cx + dx
        sy = body_cy + dy
        draw.point((sx, sy), fill=ROBE_STAR)
        # Add a small cross for star shape at 64x64
        draw.point((sx + 1, sy), fill=ROBE_STAR)
        draw.point((sx, sy + 1), fill=ROBE_STAR)


def draw_robe_hem_zigzag(draw, body_cx, body_cy):
    """Draw a zigzag gold trim line at the bottom edge of the robe."""
    hem_y = body_cy + 14
    # Zigzag pattern
    for i in range(-16, 17, 4):
        x1 = body_cx + i
        x2 = body_cx + i + 2
        draw.line([(x1, hem_y), (x2, hem_y - 2)], fill=ROBE_HEM, width=1)
        draw.line([(x2, hem_y - 2), (x2 + 2, hem_y)], fill=ROBE_HEM, width=1)
    # Second row for thickness
    for i in range(-16, 17, 4):
        x1 = body_cx + i
        x2 = body_cx + i + 2
        draw.line([(x1, hem_y + 1), (x2, hem_y - 1)], fill=ROBE_HEM_DARK, width=1)
        draw.line([(x2, hem_y - 1), (x2 + 2, hem_y + 1)], fill=ROBE_HEM_DARK, width=1)


def draw_staff_grain(draw, x1, y1, x2, y2):
    """Draw wood grain texture on the staff."""
    dx = x2 - x1
    dy = y2 - y1
    steps = max(abs(dx), abs(dy))
    if steps > 0:
        for i in range(0, steps, 3):
            t = i / steps
            gx = int(x1 + dx * t)
            gy = int(y1 + dy * t)
            draw.point((gx + 1, gy), fill=STAFF_GRAIN)


def draw_wizard(draw, ox, oy, direction, frame):
    """Draw a single wizard frame at offset (ox, oy).

    Proportions match other characters: big round head ~22px, body ~16px tall.
    Wizard floats slightly — bob animation like wraith but subtler.
    """
    bob = [0, -2, 0, -1][frame]
    sway = [-2, 0, 2, 0][frame]

    base_y = oy + 56 + bob
    body_cx = ox + 32
    body_cy = base_y - 18
    head_cy = body_cy - 20

    if direction == DOWN:
        # --- Staff (behind body, right side) ---
        staff_x = body_cx + 14
        staff_hand_y = body_cy + 2
        draw.line([(staff_x, head_cy - 4), (staff_x + sway, body_cy + 16)],
                  fill=STAFF_WOOD, width=3)
        draw.line([(staff_x + 2, head_cy - 4), (staff_x + 2 + sway, body_cy + 16)],
                  fill=STAFF_DARK, width=1)
        # Wood grain texture
        draw_staff_grain(draw, staff_x, head_cy - 4, staff_x + sway, body_cy + 16)
        # Staff orb (larger)
        orb_x, orb_y = staff_x, head_cy - 8
        draw_orb(draw, orb_x, orb_y, frame)
        # Magic sparkles
        draw_sparkles(draw, orb_x, orb_y, frame)
        # Hand glow
        draw_hand_glow(draw, staff_x - 2, staff_hand_y)

        # --- Robe body ---
        draw.polygon([
            (body_cx - 14, body_cy - 8),
            (body_cx + 14, body_cy - 8),
            (body_cx + 18, body_cy + 14),
            (body_cx - 18, body_cy + 14),
        ], fill=ROBE, outline=OUTLINE)
        # Robe highlight
        draw.polygon([
            (body_cx - 8, body_cy - 6),
            (body_cx + 8, body_cy - 6),
            (body_cx + 10, body_cy + 10),
            (body_cx - 10, body_cy + 10),
        ], fill=ROBE_LIGHT, outline=None)
        # Robe stars
        draw_robe_stars(draw, body_cx, body_cy, frame, direction)
        # Robe hem zigzag
        draw_robe_hem_zigzag(draw, body_cx, body_cy)

        # --- Head (face visible) ---
        ellipse(draw, body_cx, head_cy + 2, 14, 12, FACE)
        ellipse(draw, body_cx, head_cy + 4, 10, 8, FACE_SHADOW)

        # Eyes
        draw.rectangle([body_cx - 8, head_cy, body_cx - 4, head_cy + 4], fill=EYE)
        draw.rectangle([body_cx + 4, head_cy, body_cx + 8, head_cy + 4], fill=EYE)

        # Beard (longer, with outline)
        draw.polygon([
            (body_cx - 6, head_cy + 8),
            (body_cx + 6, head_cy + 8),
            (body_cx + 4, head_cy + 18),
            (body_cx, head_cy + 22),
            (body_cx - 4, head_cy + 18),
        ], fill=BEARD, outline=BEARD_OUTLINE)
        # Beard shadow line for depth
        draw.line([(body_cx - 2, head_cy + 12), (body_cx + 2, head_cy + 12)],
                  fill=BEARD_DARK, width=1)
        draw.line([(body_cx - 3, head_cy + 14), (body_cx + 3, head_cy + 14)],
                  fill=BEARD_DARK, width=1)

        # --- Pointed hat (curled tip, wider brim) ---
        # Hat brim (wider)
        ellipse(draw, body_cx, head_cy - 6, 18, 6, HAT)
        # Hat cone with curled tip (tip offset to the right)
        draw.polygon([
            (body_cx - 12, head_cy - 8),
            (body_cx + 12, head_cy - 8),
            (body_cx + 8, head_cy - 30),
            (body_cx + 6, head_cy - 32),
        ], fill=HAT, outline=OUTLINE)
        # Curl at tip (small extra curve to the right)
        draw.line([(body_cx + 6, head_cy - 32), (body_cx + 12, head_cy - 30)],
                  fill=HAT, width=3)
        draw.point((body_cx + 12, head_cy - 30), fill=OUTLINE)
        draw.point((body_cx + 13, head_cy - 30), fill=OUTLINE)
        # Hat band (gold)
        draw.rectangle([body_cx - 12, head_cy - 10, body_cx + 12, head_cy - 6], fill=HAT_BAND)
        # Hat band detail
        draw.line([(body_cx - 12, head_cy - 8), (body_cx + 12, head_cy - 8)], fill=HAT_BAND_DARK)

    elif direction == UP:
        # --- Staff (behind body, right side) ---
        staff_x = body_cx + 14
        staff_hand_y = body_cy + 2
        draw.line([(staff_x, head_cy - 4), (staff_x + sway, body_cy + 16)],
                  fill=STAFF_WOOD, width=3)
        draw_staff_grain(draw, staff_x, head_cy - 4, staff_x + sway, body_cy + 16)
        # Staff orb
        orb_x, orb_y = staff_x, head_cy - 8
        draw_orb(draw, orb_x, orb_y, frame)
        draw_sparkles(draw, orb_x, orb_y, frame)
        draw_hand_glow(draw, staff_x - 2, staff_hand_y)

        # --- Robe body ---
        draw.polygon([
            (body_cx - 14, body_cy - 8),
            (body_cx + 14, body_cy - 8),
            (body_cx + 18, body_cy + 14),
            (body_cx - 18, body_cy + 14),
        ], fill=ROBE, outline=OUTLINE)
        # Back of robe (darker)
        draw.polygon([
            (body_cx - 10, body_cy - 6),
            (body_cx + 10, body_cy - 6),
            (body_cx + 12, body_cy + 10),
            (body_cx - 12, body_cy + 10),
        ], fill=ROBE_DARK, outline=None)
        draw_robe_stars(draw, body_cx, body_cy, frame, direction)
        draw_robe_hem_zigzag(draw, body_cx, body_cy)

        # --- Head (back view, no face) ---
        ellipse(draw, body_cx, head_cy + 2, 14, 12, FACE_SHADOW)

        # --- Pointed hat (back, curled tip, wider brim) ---
        ellipse(draw, body_cx, head_cy - 6, 18, 6, HAT)
        draw.polygon([
            (body_cx - 12, head_cy - 8),
            (body_cx + 12, head_cy - 8),
            (body_cx + 8, head_cy - 30),
            (body_cx + 6, head_cy - 32),
        ], fill=HAT, outline=OUTLINE)
        draw.line([(body_cx + 6, head_cy - 32), (body_cx + 12, head_cy - 30)],
                  fill=HAT, width=3)
        draw.point((body_cx + 12, head_cy - 30), fill=OUTLINE)
        draw.point((body_cx + 13, head_cy - 30), fill=OUTLINE)
        draw.rectangle([body_cx - 12, head_cy - 10, body_cx + 12, head_cy - 6], fill=HAT_BAND)
        draw.line([(body_cx - 12, head_cy - 8), (body_cx + 12, head_cy - 8)], fill=HAT_BAND_DARK)

    elif direction == LEFT:
        # --- Staff (behind body, to the right/behind) ---
        staff_x = body_cx + 10
        staff_hand_y = body_cy + 2
        draw.line([(staff_x, head_cy - 2), (staff_x + sway + 2, body_cy + 16)],
                  fill=STAFF_WOOD, width=3)
        draw_staff_grain(draw, staff_x, head_cy - 2, staff_x + sway + 2, body_cy + 16)
        orb_x, orb_y = staff_x, head_cy - 6
        draw_orb(draw, orb_x, orb_y, frame)
        draw_sparkles(draw, orb_x, orb_y, frame)
        draw_hand_glow(draw, staff_x - 2, staff_hand_y)

        # --- Robe body ---
        draw.polygon([
            (body_cx - 12, body_cy - 8),
            (body_cx + 12, body_cy - 8),
            (body_cx + 16, body_cy + 14),
            (body_cx - 16, body_cy + 14),
        ], fill=ROBE, outline=OUTLINE)
        draw.polygon([
            (body_cx - 8, body_cy - 6),
            (body_cx + 6, body_cy - 6),
            (body_cx + 8, body_cy + 10),
            (body_cx - 10, body_cy + 10),
        ], fill=ROBE_LIGHT, outline=None)
        draw_robe_stars(draw, body_cx, body_cy, frame, direction)
        draw_robe_hem_zigzag(draw, body_cx, body_cy)

        # --- Head (side, facing left) ---
        ellipse(draw, body_cx - 2, head_cy + 2, 12, 12, FACE)
        ellipse(draw, body_cx - 4, head_cy + 4, 8, 8, FACE_SHADOW)

        # Eye (one visible)
        draw.rectangle([body_cx - 10, head_cy, body_cx - 6, head_cy + 4], fill=EYE)

        # Beard (side view, longer, with outline)
        draw.polygon([
            (body_cx - 8, head_cy + 8),
            (body_cx + 2, head_cy + 8),
            (body_cx - 2, head_cy + 18),
            (body_cx - 8, head_cy + 16),
        ], fill=BEARD, outline=BEARD_OUTLINE)
        draw.line([(body_cx - 6, head_cy + 12), (body_cx, head_cy + 12)],
                  fill=BEARD_DARK, width=1)

        # --- Pointed hat (side, leaning left, curled tip, wider brim) ---
        ellipse(draw, body_cx - 2, head_cy - 6, 16, 6, HAT)
        draw.polygon([
            (body_cx - 14, head_cy - 8),
            (body_cx + 10, head_cy - 8),
            (body_cx - 8, head_cy - 30),
            (body_cx - 10, head_cy - 32),
        ], fill=HAT, outline=OUTLINE)
        draw.line([(body_cx - 10, head_cy - 32), (body_cx - 16, head_cy - 28)],
                  fill=HAT, width=3)
        draw.point((body_cx - 16, head_cy - 28), fill=OUTLINE)
        draw.point((body_cx - 17, head_cy - 28), fill=OUTLINE)
        draw.rectangle([body_cx - 14, head_cy - 10, body_cx + 10, head_cy - 6], fill=HAT_BAND)
        draw.line([(body_cx - 14, head_cy - 8), (body_cx + 10, head_cy - 8)], fill=HAT_BAND_DARK)

    elif direction == RIGHT:
        # --- Staff (behind body, to the left/behind) ---
        staff_x = body_cx - 10
        staff_hand_y = body_cy + 2
        draw.line([(staff_x, head_cy - 2), (staff_x + sway - 2, body_cy + 16)],
                  fill=STAFF_WOOD, width=3)
        draw_staff_grain(draw, staff_x, head_cy - 2, staff_x + sway - 2, body_cy + 16)
        orb_x, orb_y = staff_x, head_cy - 6
        draw_orb(draw, orb_x, orb_y, frame)
        draw_sparkles(draw, orb_x, orb_y, frame)
        draw_hand_glow(draw, staff_x + 2, staff_hand_y)

        # --- Robe body ---
        draw.polygon([
            (body_cx - 12, body_cy - 8),
            (body_cx + 12, body_cy - 8),
            (body_cx + 16, body_cy + 14),
            (body_cx - 16, body_cy + 14),
        ], fill=ROBE, outline=OUTLINE)
        draw.polygon([
            (body_cx - 6, body_cy - 6),
            (body_cx + 8, body_cy - 6),
            (body_cx + 10, body_cy + 10),
            (body_cx - 8, body_cy + 10),
        ], fill=ROBE_LIGHT, outline=None)
        draw_robe_stars(draw, body_cx, body_cy, frame, direction)
        draw_robe_hem_zigzag(draw, body_cx, body_cy)

        # --- Head (side, facing right) ---
        ellipse(draw, body_cx + 2, head_cy + 2, 12, 12, FACE)
        ellipse(draw, body_cx + 4, head_cy + 4, 8, 8, FACE_SHADOW)

        # Eye (one visible)
        draw.rectangle([body_cx + 6, head_cy, body_cx + 10, head_cy + 4], fill=EYE)

        # Beard (side view, longer, with outline)
        draw.polygon([
            (body_cx - 2, head_cy + 8),
            (body_cx + 8, head_cy + 8),
            (body_cx + 8, head_cy + 16),
            (body_cx + 2, head_cy + 18),
        ], fill=BEARD, outline=BEARD_OUTLINE)
        draw.line([(body_cx, head_cy + 12), (body_cx + 6, head_cy + 12)],
                  fill=BEARD_DARK, width=1)

        # --- Pointed hat (side, leaning right, curled tip, wider brim) ---
        ellipse(draw, body_cx + 2, head_cy - 6, 16, 6, HAT)
        draw.polygon([
            (body_cx - 10, head_cy - 8),
            (body_cx + 14, head_cy - 8),
            (body_cx + 10, head_cy - 30),
            (body_cx + 8, head_cy - 32),
        ], fill=HAT, outline=OUTLINE)
        draw.line([(body_cx + 8, head_cy - 32), (body_cx + 16, head_cy - 28)],
                  fill=HAT, width=3)
        draw.point((body_cx + 16, head_cy - 28), fill=OUTLINE)
        draw.point((body_cx + 17, head_cy - 28), fill=OUTLINE)
        draw.rectangle([body_cx - 10, head_cy - 10, body_cx + 14, head_cy - 6], fill=HAT_BAND)
        draw.line([(body_cx - 10, head_cy - 8), (body_cx + 14, head_cy - 8)], fill=HAT_BAND_DARK)


def main():
    img = Image.new("RGBA", (IMG_W, IMG_H), (0, 0, 0, 0))

    for direction in range(ROWS):
        for frame in range(COLS):
            frame_img = Image.new("RGBA", (FRAME_SIZE, FRAME_SIZE), (0, 0, 0, 0))
            frame_draw = ImageDraw.Draw(frame_img)
            draw_wizard(frame_draw, 0, 0, direction, frame)
            img.paste(frame_img, (frame * FRAME_SIZE, direction * FRAME_SIZE))

    img.save("sprites/wizard.png")
    print(f"Generated sprites/wizard.png ({IMG_W}x{IMG_H})")


if __name__ == "__main__":
    main()
