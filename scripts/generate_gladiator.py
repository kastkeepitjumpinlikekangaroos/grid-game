#!/usr/bin/env python3
"""Generate sprites/gladiator.png — 4-column x 4-row character spritesheet.

128x128 PNG, 32x32 per frame.
Row layout: Down=0, Up=1, Left=2, Right=3
4 walking animation frames per direction.

Style matches Spaceman: big round head, round body, small limbs, dark outlines.
Theme: Roman gladiator — bronze helmet with red crest, armor torso, red belt.
"""

from PIL import Image, ImageDraw

FRAME_SIZE = 32
COLS = 4
ROWS = 4
IMG_W = FRAME_SIZE * COLS   # 128
IMG_H = FRAME_SIZE * ROWS   # 128

# Colors — matching Spaceman's palette approach (dark outline + fill + highlight)
OUTLINE = (40, 35, 35)
SKIN = (220, 180, 140)
SKIN_DARK = (185, 145, 110)
BRONZE = (180, 135, 55)
BRONZE_LIGHT = (215, 170, 75)
BRONZE_DARK = (130, 95, 35)
RED = (175, 45, 45)
RED_DARK = (130, 35, 35)
RED_LIGHT = (200, 70, 70)
GOLD = (245, 200, 65)
BROWN = (110, 75, 45)
BROWN_DARK = (80, 55, 35)
BLACK = (30, 30, 30)
WHITE_GLINT = (255, 245, 220)

DOWN, UP, LEFT, RIGHT = 0, 1, 2, 3


def ellipse(draw, cx, cy, rx, ry, fill, outline=OUTLINE):
    """Draw an outlined ellipse centered at (cx, cy)."""
    draw.ellipse([cx - rx, cy - ry, cx + rx, cy + ry], fill=fill, outline=outline)


def pill(draw, cx, cy, rx, ry, fill, outline=OUTLINE):
    """Draw a rounded rectangle (pill shape) as an ellipse."""
    draw.rounded_rectangle([cx - rx, cy - ry, cx + rx, cy + ry],
                           radius=min(rx, ry), fill=fill, outline=outline)


def draw_gladiator(draw, ox, oy, direction, frame):
    """Draw a single gladiator frame at offset (ox, oy).

    Proportions match Spaceman: big round head ~11px, body ~8px tall,
    small stick legs, centered in 32x32 frame.
    """
    # Walking bob
    bob = [0, -1, 0, -1][frame]
    leg_spread = [-2, 0, 2, 0][frame]

    # Anchor: bottom of feet at oy+28, so character sits in lower portion of frame
    base_y = oy + 27 + bob
    # Body center
    body_cx = ox + 16
    body_cy = base_y - 10
    # Head center (big round head above body)
    head_cy = body_cy - 10

    if direction == DOWN:
        # --- Legs (behind body) ---
        # Left leg
        draw.rectangle([body_cx - 5 + leg_spread, body_cy + 5,
                        body_cx - 2 + leg_spread, base_y], fill=BROWN, outline=OUTLINE)
        # Right leg
        draw.rectangle([body_cx + 2 - leg_spread, body_cy + 5,
                        body_cx + 5 - leg_spread, base_y], fill=BROWN, outline=OUTLINE)
        # Greave accents
        draw.rectangle([body_cx - 5 + leg_spread, base_y - 3,
                        body_cx - 2 + leg_spread, base_y], fill=BRONZE_DARK, outline=OUTLINE)
        draw.rectangle([body_cx + 2 - leg_spread, base_y - 3,
                        body_cx + 5 - leg_spread, base_y], fill=BRONZE_DARK, outline=OUTLINE)

        # --- Body (round torso with armor) ---
        ellipse(draw, body_cx, body_cy, 7, 6, BRONZE)
        # Armor highlight
        ellipse(draw, body_cx, body_cy - 1, 5, 4, BRONZE_LIGHT)
        # Red belt
        draw.rectangle([body_cx - 7, body_cy + 3, body_cx + 7, body_cy + 5],
                       fill=RED, outline=OUTLINE)

        # --- Arms (small, at sides) ---
        # Left arm
        draw.rectangle([body_cx - 9, body_cy - 3, body_cx - 6, body_cy + 3],
                       fill=SKIN, outline=OUTLINE)
        # Right arm
        draw.rectangle([body_cx + 6, body_cy - 3, body_cx + 9, body_cy + 3],
                       fill=SKIN, outline=OUTLINE)
        # Shoulder pads
        ellipse(draw, body_cx - 7, body_cy - 3, 3, 2, BRONZE)
        ellipse(draw, body_cx + 7, body_cy - 3, 3, 2, BRONZE)

        # --- Head (big round helmet) ---
        # Helmet (main dome)
        ellipse(draw, body_cx, head_cy, 8, 7, BRONZE)
        # Face opening
        ellipse(draw, body_cx, head_cy + 2, 5, 4, SKIN)
        # Helmet brim
        draw.rectangle([body_cx - 8, head_cy + 1, body_cx + 8, head_cy + 3],
                       fill=BRONZE_DARK, outline=OUTLINE)
        # Eyes
        draw.rectangle([body_cx - 3, head_cy + 1, body_cx - 1, head_cy + 3], fill=BLACK)
        draw.rectangle([body_cx + 1, head_cy + 1, body_cx + 3, head_cy + 3], fill=BLACK)
        # Crest (red plume on top)
        draw.rectangle([body_cx - 1, head_cy - 10, body_cx + 1, head_cy - 5],
                       fill=RED, outline=OUTLINE)
        draw.rectangle([body_cx - 2, head_cy - 9, body_cx + 2, head_cy - 6],
                       fill=RED_LIGHT, outline=None)
        # Gold accent on helmet
        draw.point((body_cx, head_cy - 5), fill=GOLD)

    elif direction == UP:
        # --- Legs ---
        draw.rectangle([body_cx - 5 + leg_spread, body_cy + 5,
                        body_cx - 2 + leg_spread, base_y], fill=BROWN, outline=OUTLINE)
        draw.rectangle([body_cx + 2 - leg_spread, body_cy + 5,
                        body_cx + 5 - leg_spread, base_y], fill=BROWN, outline=OUTLINE)
        draw.rectangle([body_cx - 5 + leg_spread, base_y - 3,
                        body_cx - 2 + leg_spread, base_y], fill=BRONZE_DARK, outline=OUTLINE)
        draw.rectangle([body_cx + 2 - leg_spread, base_y - 3,
                        body_cx + 5 - leg_spread, base_y], fill=BRONZE_DARK, outline=OUTLINE)

        # --- Cape (visible from behind, flowing) ---
        cape_sway = [0, 1, 0, -1][frame]
        draw.rounded_rectangle([body_cx - 6 + cape_sway, body_cy - 3,
                                body_cx + 6 + cape_sway, body_cy + 7],
                               radius=3, fill=RED, outline=OUTLINE)
        draw.rounded_rectangle([body_cx - 5 + cape_sway, body_cy - 2,
                                body_cx + 5 + cape_sway, body_cy + 6],
                               radius=2, fill=RED_DARK, outline=None)

        # --- Body ---
        ellipse(draw, body_cx, body_cy, 7, 6, BRONZE)
        ellipse(draw, body_cx, body_cy - 1, 5, 4, BRONZE_DARK)

        # --- Arms ---
        draw.rectangle([body_cx - 9, body_cy - 3, body_cx - 6, body_cy + 3],
                       fill=SKIN, outline=OUTLINE)
        draw.rectangle([body_cx + 6, body_cy - 3, body_cx + 9, body_cy + 3],
                       fill=SKIN, outline=OUTLINE)
        ellipse(draw, body_cx - 7, body_cy - 3, 3, 2, BRONZE)
        ellipse(draw, body_cx + 7, body_cy - 3, 3, 2, BRONZE)

        # --- Head (back of helmet) ---
        ellipse(draw, body_cx, head_cy, 8, 7, BRONZE)
        ellipse(draw, body_cx, head_cy, 6, 5, BRONZE_DARK)
        # Crest
        draw.rectangle([body_cx - 1, head_cy - 10, body_cx + 1, head_cy - 5],
                       fill=RED, outline=OUTLINE)
        draw.rectangle([body_cx - 2, head_cy - 9, body_cx + 2, head_cy - 6],
                       fill=RED_LIGHT, outline=None)

    elif direction == LEFT:
        # --- Legs (side view) ---
        # Back leg
        draw.rectangle([body_cx - 1 - leg_spread, body_cy + 5,
                        body_cx + 2 - leg_spread, base_y], fill=BROWN_DARK, outline=OUTLINE)
        draw.rectangle([body_cx - 1 - leg_spread, base_y - 3,
                        body_cx + 2 - leg_spread, base_y], fill=BRONZE_DARK, outline=OUTLINE)
        # Front leg
        draw.rectangle([body_cx - 4 + leg_spread, body_cy + 5,
                        body_cx - 1 + leg_spread, base_y], fill=BROWN, outline=OUTLINE)
        draw.rectangle([body_cx - 4 + leg_spread, base_y - 3,
                        body_cx - 1 + leg_spread, base_y], fill=BRONZE_DARK, outline=OUTLINE)

        # --- Cape (flowing right/behind) ---
        cape_sway = [0, 1, 0, -1][frame]
        draw.rounded_rectangle([body_cx + 3, body_cy - 4,
                                body_cx + 8 + cape_sway, body_cy + 6],
                               radius=3, fill=RED, outline=OUTLINE)

        # --- Body ---
        ellipse(draw, body_cx - 1, body_cy, 6, 6, BRONZE)
        ellipse(draw, body_cx - 1, body_cy - 1, 4, 4, BRONZE_LIGHT)
        # Red belt
        draw.rectangle([body_cx - 7, body_cy + 3, body_cx + 5, body_cy + 5],
                       fill=RED, outline=OUTLINE)

        # --- Arm (front, leading) ---
        draw.rectangle([body_cx - 7, body_cy - 2, body_cx - 4, body_cy + 3],
                       fill=SKIN, outline=OUTLINE)
        # Shoulder pad
        ellipse(draw, body_cx - 5, body_cy - 3, 3, 2, BRONZE)

        # --- Head (side view, facing left) ---
        ellipse(draw, body_cx - 1, head_cy, 7, 7, BRONZE)
        # Face (partial)
        ellipse(draw, body_cx - 3, head_cy + 2, 4, 3, SKIN)
        # Helmet brim
        draw.rectangle([body_cx - 8, head_cy + 1, body_cx + 4, head_cy + 3],
                       fill=BRONZE_DARK, outline=OUTLINE)
        # Eye
        draw.rectangle([body_cx - 5, head_cy + 1, body_cx - 3, head_cy + 3], fill=BLACK)
        # Crest
        draw.rectangle([body_cx - 2, head_cy - 10, body_cx, head_cy - 5],
                       fill=RED, outline=OUTLINE)

    elif direction == RIGHT:
        # --- Legs ---
        # Back leg
        draw.rectangle([body_cx - 1 + leg_spread, body_cy + 5,
                        body_cx + 2 + leg_spread, base_y], fill=BROWN_DARK, outline=OUTLINE)
        draw.rectangle([body_cx - 1 + leg_spread, base_y - 3,
                        body_cx + 2 + leg_spread, base_y], fill=BRONZE_DARK, outline=OUTLINE)
        # Front leg
        draw.rectangle([body_cx + 2 - leg_spread, body_cy + 5,
                        body_cx + 5 - leg_spread, base_y], fill=BROWN, outline=OUTLINE)
        draw.rectangle([body_cx + 2 - leg_spread, base_y - 3,
                        body_cx + 5 - leg_spread, base_y], fill=BRONZE_DARK, outline=OUTLINE)

        # --- Cape (flowing left/behind) ---
        cape_sway = [0, -1, 0, 1][frame]
        draw.rounded_rectangle([body_cx - 8 + cape_sway, body_cy - 4,
                                body_cx - 3, body_cy + 6],
                               radius=3, fill=RED, outline=OUTLINE)

        # --- Body ---
        ellipse(draw, body_cx + 1, body_cy, 6, 6, BRONZE)
        ellipse(draw, body_cx + 1, body_cy - 1, 4, 4, BRONZE_LIGHT)
        # Red belt
        draw.rectangle([body_cx - 5, body_cy + 3, body_cx + 7, body_cy + 5],
                       fill=RED, outline=OUTLINE)

        # --- Arm ---
        draw.rectangle([body_cx + 4, body_cy - 2, body_cx + 7, body_cy + 3],
                       fill=SKIN, outline=OUTLINE)
        ellipse(draw, body_cx + 5, body_cy - 3, 3, 2, BRONZE)

        # --- Head ---
        ellipse(draw, body_cx + 1, head_cy, 7, 7, BRONZE)
        ellipse(draw, body_cx + 3, head_cy + 2, 4, 3, SKIN)
        draw.rectangle([body_cx - 4, head_cy + 1, body_cx + 8, head_cy + 3],
                       fill=BRONZE_DARK, outline=OUTLINE)
        # Eye
        draw.rectangle([body_cx + 3, head_cy + 1, body_cx + 5, head_cy + 3], fill=BLACK)
        # Crest
        draw.rectangle([body_cx, head_cy - 10, body_cx + 2, head_cy - 5],
                       fill=RED, outline=OUTLINE)


def main():
    img = Image.new("RGBA", (IMG_W, IMG_H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    for direction in range(ROWS):
        for frame in range(COLS):
            ox = frame * FRAME_SIZE
            oy = direction * FRAME_SIZE
            draw_gladiator(draw, ox, oy, direction, frame)

    img.save("sprites/gladiator.png")
    print(f"Generated sprites/gladiator.png ({IMG_W}x{IMG_H})")


if __name__ == "__main__":
    main()
