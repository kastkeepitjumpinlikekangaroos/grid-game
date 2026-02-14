#!/usr/bin/env python3
"""Generate sprites/raptor.png — 4-column x 4-row character spritesheet.

128x128 PNG, 32x32 per frame.
Row layout: Down=0, Up=1, Left=2, Right=3
4 walking animation frames per direction.

Style matches Spaceman/Gladiator/Wraith: big round head, round body, small limbs.
Theme: Bird of prey — sharp beak, feathered body, taloned feet, wing-arms.
Color palette: gold/brown/amber with dark outlines.
"""

from PIL import Image, ImageDraw

FRAME_SIZE = 32
COLS = 4
ROWS = 4
IMG_W = FRAME_SIZE * COLS   # 128
IMG_H = FRAME_SIZE * ROWS   # 128

# Colors
OUTLINE = (40, 30, 15)
BODY_DARK = (90, 60, 25)
BODY = (140, 95, 35)
BODY_LIGHT = (180, 130, 50)
BELLY = (210, 180, 120)
HEAD = (160, 110, 40)
HEAD_DARK = (120, 80, 30)
BEAK_DARK = (160, 120, 30)
BEAK = (210, 170, 50)
EYE = (220, 180, 30)
EYE_CORE = (40, 30, 15)
WING_DARK = (80, 55, 20)
WING = (120, 85, 30)
WING_LIGHT = (160, 120, 45)
TALON = (90, 70, 30)
TALON_TIP = (60, 45, 20)
CREST = (190, 130, 40)

DOWN, UP, LEFT, RIGHT = 0, 1, 2, 3


def ellipse(draw, cx, cy, rx, ry, fill, outline=OUTLINE):
    """Draw an outlined ellipse centered at (cx, cy)."""
    draw.ellipse([cx - rx, cy - ry, cx + rx, cy + ry], fill=fill, outline=outline)


def draw_raptor(draw, ox, oy, direction, frame):
    """Draw a single raptor frame at offset (ox, oy).

    Proportions match other characters: big round head ~11px, body ~8px tall.
    Bird of prey with sharp features and wing-like arms.
    """
    bob = [0, -1, 0, -1][frame]
    wing_flap = [-1, 1, -1, 0][frame]
    leg_step = [0, 1, 0, -1][frame]

    base_y = oy + 28 + bob
    body_cx = ox + 16
    body_cy = base_y - 9
    head_cy = body_cy - 10

    if direction == DOWN:
        # --- Taloned feet ---
        for side in [-1, 1]:
            foot_x = body_cx + side * 4 + side * leg_step
            draw.line([(foot_x, base_y), (foot_x - 2, base_y + 3)], fill=TALON, width=1)
            draw.line([(foot_x, base_y), (foot_x + 2, base_y + 3)], fill=TALON, width=1)
            draw.point((foot_x, base_y + 1), fill=TALON_TIP)

        # --- Legs ---
        draw.line([(body_cx - 4 - leg_step, base_y), (body_cx - 3, body_cy + 5)], fill=BODY_DARK, width=2)
        draw.line([(body_cx + 4 + leg_step, base_y), (body_cx + 3, body_cy + 5)], fill=BODY_DARK, width=2)

        # --- Wings (arms) ---
        for side in [-1, 1]:
            wing_x = body_cx + side * 9
            wing_y = body_cy + wing_flap
            draw.polygon([
                (body_cx + side * 7, body_cy - 3),
                (wing_x + side * 2, wing_y - 1),
                (wing_x + side * 3, wing_y + 3),
                (wing_x + side * 1, wing_y + 5),
                (body_cx + side * 7, body_cy + 4),
            ], fill=WING, outline=OUTLINE)
            # Wing feather detail
            draw.line([(wing_x + side * 1, wing_y), (wing_x + side * 2, wing_y + 4)],
                     fill=WING_LIGHT, width=1)

        # --- Body ---
        draw.polygon([
            (body_cx - 7, body_cy - 4),
            (body_cx + 7, body_cy - 4),
            (body_cx + 8, body_cy + 5),
            (body_cx - 8, body_cy + 5),
        ], fill=BODY, outline=OUTLINE)
        # Belly patch
        draw.polygon([
            (body_cx - 4, body_cy - 1),
            (body_cx + 4, body_cy - 1),
            (body_cx + 5, body_cy + 4),
            (body_cx - 5, body_cy + 4),
        ], fill=BELLY, outline=None)

        # --- Head ---
        ellipse(draw, body_cx, head_cy, 8, 7, HEAD)
        # Feather crest on top
        draw.polygon([
            (body_cx - 2, head_cy - 8),
            (body_cx + 2, head_cy - 8),
            (body_cx + 1, head_cy - 4),
            (body_cx - 1, head_cy - 4),
        ], fill=CREST, outline=OUTLINE)
        # Small side crests
        draw.polygon([
            (body_cx - 4, head_cy - 6),
            (body_cx - 2, head_cy - 7),
            (body_cx - 1, head_cy - 4),
        ], fill=CREST, outline=OUTLINE)
        draw.polygon([
            (body_cx + 4, head_cy - 6),
            (body_cx + 2, head_cy - 7),
            (body_cx + 1, head_cy - 4),
        ], fill=CREST, outline=OUTLINE)

        # Eyes
        draw.rectangle([body_cx - 5, head_cy - 1, body_cx - 2, head_cy + 1], fill=EYE)
        draw.rectangle([body_cx + 2, head_cy - 1, body_cx + 5, head_cy + 1], fill=EYE)
        draw.point((body_cx - 3, head_cy), fill=EYE_CORE)
        draw.point((body_cx + 3, head_cy), fill=EYE_CORE)

        # Beak (pointing down)
        draw.polygon([
            (body_cx - 2, head_cy + 2),
            (body_cx + 2, head_cy + 2),
            (body_cx, head_cy + 6),
        ], fill=BEAK, outline=OUTLINE)

    elif direction == UP:
        # --- Taloned feet ---
        for side in [-1, 1]:
            foot_x = body_cx + side * 4 + side * leg_step
            draw.line([(foot_x, base_y), (foot_x - 2, base_y + 3)], fill=TALON, width=1)
            draw.line([(foot_x, base_y), (foot_x + 2, base_y + 3)], fill=TALON, width=1)

        # Legs
        draw.line([(body_cx - 4 - leg_step, base_y), (body_cx - 3, body_cy + 5)], fill=BODY_DARK, width=2)
        draw.line([(body_cx + 4 + leg_step, base_y), (body_cx + 3, body_cy + 5)], fill=BODY_DARK, width=2)

        # Wings
        for side in [-1, 1]:
            wing_x = body_cx + side * 9
            wing_y = body_cy + wing_flap
            draw.polygon([
                (body_cx + side * 7, body_cy - 3),
                (wing_x + side * 2, wing_y - 1),
                (wing_x + side * 3, wing_y + 3),
                (wing_x + side * 1, wing_y + 5),
                (body_cx + side * 7, body_cy + 4),
            ], fill=WING, outline=OUTLINE)
            draw.line([(wing_x + side * 1, wing_y), (wing_x + side * 2, wing_y + 4)],
                     fill=WING_LIGHT, width=1)

        # Body (back view — darker)
        draw.polygon([
            (body_cx - 7, body_cy - 4),
            (body_cx + 7, body_cy - 4),
            (body_cx + 8, body_cy + 5),
            (body_cx - 8, body_cy + 5),
        ], fill=BODY, outline=OUTLINE)
        draw.polygon([
            (body_cx - 5, body_cy - 2),
            (body_cx + 5, body_cy - 2),
            (body_cx + 6, body_cy + 4),
            (body_cx - 6, body_cy + 4),
        ], fill=BODY_DARK, outline=None)

        # Head (back view)
        ellipse(draw, body_cx, head_cy, 8, 7, HEAD)
        ellipse(draw, body_cx, head_cy, 6, 5, HEAD_DARK)
        # Crest
        draw.polygon([
            (body_cx - 2, head_cy - 8),
            (body_cx + 2, head_cy - 8),
            (body_cx + 1, head_cy - 4),
            (body_cx - 1, head_cy - 4),
        ], fill=CREST, outline=OUTLINE)
        draw.polygon([
            (body_cx - 4, head_cy - 6),
            (body_cx - 2, head_cy - 7),
            (body_cx - 1, head_cy - 4),
        ], fill=CREST, outline=OUTLINE)
        draw.polygon([
            (body_cx + 4, head_cy - 6),
            (body_cx + 2, head_cy - 7),
            (body_cx + 1, head_cy - 4),
        ], fill=CREST, outline=OUTLINE)

    elif direction == LEFT:
        # Feet
        foot_x = body_cx - 2 + leg_step
        draw.line([(foot_x, base_y), (foot_x - 3, base_y + 3)], fill=TALON, width=1)
        draw.line([(foot_x, base_y), (foot_x + 1, base_y + 3)], fill=TALON, width=1)
        foot_x2 = body_cx + 2 + leg_step
        draw.line([(foot_x2, base_y), (foot_x2 - 3, base_y + 3)], fill=TALON, width=1)

        # Legs
        draw.line([(body_cx - 2 + leg_step, base_y), (body_cx - 1, body_cy + 5)], fill=BODY_DARK, width=2)
        draw.line([(body_cx + 2 + leg_step, base_y), (body_cx + 1, body_cy + 5)], fill=BODY_DARK, width=2)

        # Wing (trailing behind — right side)
        wing_x = body_cx + 6
        wing_y = body_cy + wing_flap
        draw.polygon([
            (body_cx + 5, body_cy - 3),
            (wing_x + 3, wing_y - 2),
            (wing_x + 4, wing_y + 3),
            (wing_x + 2, wing_y + 5),
            (body_cx + 5, body_cy + 4),
        ], fill=WING_DARK, outline=OUTLINE)

        # Body
        draw.polygon([
            (body_cx - 6, body_cy - 4),
            (body_cx + 6, body_cy - 4),
            (body_cx + 7, body_cy + 5),
            (body_cx - 7, body_cy + 5),
        ], fill=BODY, outline=OUTLINE)
        draw.polygon([
            (body_cx - 4, body_cy - 1),
            (body_cx + 3, body_cy - 1),
            (body_cx + 4, body_cy + 4),
            (body_cx - 3, body_cy + 4),
        ], fill=BELLY, outline=None)

        # Wing (front — left side)
        wing_x = body_cx - 7
        wing_y = body_cy + wing_flap
        draw.polygon([
            (body_cx - 5, body_cy - 3),
            (wing_x - 2, wing_y - 1),
            (wing_x - 3, wing_y + 3),
            (wing_x - 1, wing_y + 5),
            (body_cx - 5, body_cy + 4),
        ], fill=WING, outline=OUTLINE)
        draw.line([(wing_x - 1, wing_y), (wing_x - 2, wing_y + 4)],
                 fill=WING_LIGHT, width=1)

        # Head (facing left)
        ellipse(draw, body_cx - 1, head_cy, 7, 7, HEAD)
        # Eye
        draw.rectangle([body_cx - 5, head_cy - 1, body_cx - 2, head_cy + 1], fill=EYE)
        draw.point((body_cx - 3, head_cy), fill=EYE_CORE)
        # Beak (pointing left)
        draw.polygon([
            (body_cx - 8, head_cy),
            (body_cx - 5, head_cy - 2),
            (body_cx - 5, head_cy + 2),
        ], fill=BEAK, outline=OUTLINE)
        # Crest
        draw.polygon([
            (body_cx - 1, head_cy - 8),
            (body_cx + 2, head_cy - 7),
            (body_cx + 1, head_cy - 4),
            (body_cx - 1, head_cy - 4),
        ], fill=CREST, outline=OUTLINE)

    elif direction == RIGHT:
        # Feet
        foot_x = body_cx + 2 - leg_step
        draw.line([(foot_x, base_y), (foot_x + 3, base_y + 3)], fill=TALON, width=1)
        draw.line([(foot_x, base_y), (foot_x - 1, base_y + 3)], fill=TALON, width=1)
        foot_x2 = body_cx - 2 - leg_step
        draw.line([(foot_x2, base_y), (foot_x2 + 3, base_y + 3)], fill=TALON, width=1)

        # Legs
        draw.line([(body_cx + 2 - leg_step, base_y), (body_cx + 1, body_cy + 5)], fill=BODY_DARK, width=2)
        draw.line([(body_cx - 2 - leg_step, base_y), (body_cx - 1, body_cy + 5)], fill=BODY_DARK, width=2)

        # Wing (trailing behind — left side)
        wing_x = body_cx - 6
        wing_y = body_cy + wing_flap
        draw.polygon([
            (body_cx - 5, body_cy - 3),
            (wing_x - 3, wing_y - 2),
            (wing_x - 4, wing_y + 3),
            (wing_x - 2, wing_y + 5),
            (body_cx - 5, body_cy + 4),
        ], fill=WING_DARK, outline=OUTLINE)

        # Body
        draw.polygon([
            (body_cx - 6, body_cy - 4),
            (body_cx + 6, body_cy - 4),
            (body_cx + 7, body_cy + 5),
            (body_cx - 7, body_cy + 5),
        ], fill=BODY, outline=OUTLINE)
        draw.polygon([
            (body_cx - 3, body_cy - 1),
            (body_cx + 4, body_cy - 1),
            (body_cx + 3, body_cy + 4),
            (body_cx - 4, body_cy + 4),
        ], fill=BELLY, outline=None)

        # Wing (front — right side)
        wing_x = body_cx + 7
        wing_y = body_cy + wing_flap
        draw.polygon([
            (body_cx + 5, body_cy - 3),
            (wing_x + 2, wing_y - 1),
            (wing_x + 3, wing_y + 3),
            (wing_x + 1, wing_y + 5),
            (body_cx + 5, body_cy + 4),
        ], fill=WING, outline=OUTLINE)
        draw.line([(wing_x + 1, wing_y), (wing_x + 2, wing_y + 4)],
                 fill=WING_LIGHT, width=1)

        # Head (facing right)
        ellipse(draw, body_cx + 1, head_cy, 7, 7, HEAD)
        # Eye
        draw.rectangle([body_cx + 2, head_cy - 1, body_cx + 5, head_cy + 1], fill=EYE)
        draw.point((body_cx + 3, head_cy), fill=EYE_CORE)
        # Beak (pointing right)
        draw.polygon([
            (body_cx + 8, head_cy),
            (body_cx + 5, head_cy - 2),
            (body_cx + 5, head_cy + 2),
        ], fill=BEAK, outline=OUTLINE)
        # Crest
        draw.polygon([
            (body_cx + 1, head_cy - 8),
            (body_cx - 2, head_cy - 7),
            (body_cx - 1, head_cy - 4),
            (body_cx + 1, head_cy - 4),
        ], fill=CREST, outline=OUTLINE)


def main():
    img = Image.new("RGBA", (IMG_W, IMG_H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    for direction in range(ROWS):
        for frame in range(COLS):
            ox = frame * FRAME_SIZE
            oy = direction * FRAME_SIZE
            draw_raptor(draw, ox, oy, direction, frame)

    img.save("sprites/raptor.png")
    print(f"Generated sprites/raptor.png ({IMG_W}x{IMG_H})")


if __name__ == "__main__":
    main()
