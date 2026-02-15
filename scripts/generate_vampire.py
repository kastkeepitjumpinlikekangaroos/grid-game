#!/usr/bin/env python3
"""Generate sprites/vampire.png — 4-column x 4-row character spritesheet.

128x128 PNG, 32x32 per frame.
Row layout: Down=0, Up=1, Left=2, Right=3
4 walking animation frames per direction.

Style matches Spaceman/Gladiator: big round head, round body, small limbs.
Theme: Gothic vampire — high collar cape, pale skin, red glowing eyes, fangs.
Color palette: deep crimson, blacks, pale white skin.
"""

from PIL import Image, ImageDraw

FRAME_SIZE = 32
COLS = 4
ROWS = 4
IMG_W = FRAME_SIZE * COLS   # 128
IMG_H = FRAME_SIZE * ROWS   # 128

# Colors
OUTLINE = (20, 10, 15)
CAPE_DARK = (50, 10, 20)
CAPE = (80, 15, 25)
CAPE_LIGHT = (110, 20, 35)
COLLAR = (60, 12, 22)
COLLAR_HIGHLIGHT = (90, 18, 30)
VEST_DARK = (30, 25, 35)
VEST = (45, 35, 50)
VEST_HIGHLIGHT = (55, 45, 60)
SKIN_PALE = (220, 210, 200)
SKIN_SHADOW = (180, 170, 160)
HAIR_DARK = (25, 15, 20)
HAIR = (40, 20, 30)
EYE_RED = (220, 30, 30)
EYE_GLOW = (255, 60, 50)
EYE_CORE = (255, 120, 100)
MOUTH_DARK = (60, 20, 25)
FANG = (240, 235, 230)
PANTS = (25, 20, 30)
SHOE = (20, 15, 20)

DOWN, UP, LEFT, RIGHT = 0, 1, 2, 3


def ellipse(draw, cx, cy, rx, ry, fill, outline=OUTLINE):
    draw.ellipse([cx - rx, cy - ry, cx + rx, cy + ry], fill=fill, outline=outline)


def draw_vampire(draw, ox, oy, direction, frame):
    """Draw a single vampire frame at offset (ox, oy)."""
    bob = [0, -1, 0, -1][frame]
    walk_offset = [-1, 0, 1, 0][frame]

    base_y = oy + 29 + bob
    body_cx = ox + 16
    body_cy = base_y - 11
    head_cy = body_cy - 10

    if direction == DOWN:
        # --- Cape (behind body, flowing) ---
        cape_sway = walk_offset
        draw.polygon([
            (body_cx - 9, body_cy - 3),
            (body_cx + 9, body_cy - 3),
            (body_cx + 11 + cape_sway, base_y + 1),
            (body_cx - 11 + cape_sway, base_y + 1),
        ], fill=CAPE_DARK, outline=OUTLINE)

        # --- Legs ---
        left_leg_x = body_cx - 3 + walk_offset
        right_leg_x = body_cx + 3 - walk_offset
        draw.rectangle([left_leg_x - 2, base_y - 5, left_leg_x + 1, base_y - 1], fill=PANTS, outline=OUTLINE)
        draw.rectangle([right_leg_x - 1, base_y - 5, right_leg_x + 2, base_y - 1], fill=PANTS, outline=OUTLINE)
        draw.rectangle([left_leg_x - 2, base_y - 1, left_leg_x + 2, base_y + 1], fill=SHOE, outline=OUTLINE)
        draw.rectangle([right_leg_x - 2, base_y - 1, right_leg_x + 2, base_y + 1], fill=SHOE, outline=OUTLINE)

        # --- Body (vest) ---
        draw.polygon([
            (body_cx - 7, body_cy - 4),
            (body_cx + 7, body_cy - 4),
            (body_cx + 6, body_cy + 5),
            (body_cx - 6, body_cy + 5),
        ], fill=VEST, outline=OUTLINE)
        # Vest center line
        draw.line([(body_cx, body_cy - 3), (body_cx, body_cy + 4)], fill=VEST_HIGHLIGHT, width=1)

        # --- High collar ---
        draw.polygon([
            (body_cx - 8, body_cy - 6),
            (body_cx - 5, body_cy - 9),
            (body_cx - 5, body_cy - 4),
            (body_cx - 7, body_cy - 4),
        ], fill=COLLAR, outline=OUTLINE)
        draw.polygon([
            (body_cx + 8, body_cy - 6),
            (body_cx + 5, body_cy - 9),
            (body_cx + 5, body_cy - 4),
            (body_cx + 7, body_cy - 4),
        ], fill=COLLAR, outline=OUTLINE)

        # --- Arms ---
        left_arm_y = body_cy - 2 + walk_offset
        right_arm_y = body_cy - 2 - walk_offset
        draw.rectangle([body_cx - 10, left_arm_y - 1, body_cx - 7, left_arm_y + 4], fill=CAPE, outline=OUTLINE)
        draw.rectangle([body_cx + 7, right_arm_y - 1, body_cx + 10, right_arm_y + 4], fill=CAPE, outline=OUTLINE)
        # Pale hands
        draw.rectangle([body_cx - 10, left_arm_y + 4, body_cx - 7, left_arm_y + 6], fill=SKIN_PALE, outline=OUTLINE)
        draw.rectangle([body_cx + 7, right_arm_y + 4, body_cx + 10, right_arm_y + 6], fill=SKIN_PALE, outline=OUTLINE)

        # --- Head ---
        ellipse(draw, body_cx, head_cy, 8, 8, SKIN_PALE)
        # Hair (slicked back, widow's peak)
        draw.polygon([
            (body_cx - 8, head_cy - 2),
            (body_cx, head_cy - 10),
            (body_cx + 8, head_cy - 2),
            (body_cx + 7, head_cy - 5),
            (body_cx, head_cy - 7),
            (body_cx - 7, head_cy - 5),
        ], fill=HAIR, outline=OUTLINE)

        # --- Eyes (glowing red) ---
        draw.rectangle([body_cx - 5, head_cy, body_cx - 2, head_cy + 2], fill=EYE_RED)
        draw.rectangle([body_cx + 2, head_cy, body_cx + 5, head_cy + 2], fill=EYE_RED)
        draw.point((body_cx - 3, head_cy + 1), fill=EYE_CORE)
        draw.point((body_cx + 3, head_cy + 1), fill=EYE_CORE)

        # --- Mouth with fangs ---
        draw.line([(body_cx - 2, head_cy + 4), (body_cx + 2, head_cy + 4)], fill=MOUTH_DARK, width=1)
        draw.line([(body_cx - 2, head_cy + 4), (body_cx - 2, head_cy + 6)], fill=FANG, width=1)
        draw.line([(body_cx + 2, head_cy + 4), (body_cx + 2, head_cy + 6)], fill=FANG, width=1)

    elif direction == UP:
        # --- Cape (visible from behind, flowing) ---
        cape_sway = walk_offset
        draw.polygon([
            (body_cx - 10, body_cy - 5),
            (body_cx + 10, body_cy - 5),
            (body_cx + 12 + cape_sway, base_y + 1),
            (body_cx - 12 + cape_sway, base_y + 1),
        ], fill=CAPE, outline=OUTLINE)
        # Cape inner highlight
        draw.polygon([
            (body_cx - 7, body_cy - 3),
            (body_cx + 7, body_cy - 3),
            (body_cx + 8 + cape_sway, base_y - 1),
            (body_cx - 8 + cape_sway, base_y - 1),
        ], fill=CAPE_LIGHT, outline=None)

        # --- Legs (behind cape) ---
        left_leg_x = body_cx - 3 + walk_offset
        right_leg_x = body_cx + 3 - walk_offset
        draw.rectangle([left_leg_x - 2, base_y - 3, left_leg_x + 1, base_y - 1], fill=PANTS, outline=OUTLINE)
        draw.rectangle([right_leg_x - 1, base_y - 3, right_leg_x + 2, base_y - 1], fill=PANTS, outline=OUTLINE)
        draw.rectangle([left_leg_x - 2, base_y - 1, left_leg_x + 2, base_y + 1], fill=SHOE, outline=OUTLINE)
        draw.rectangle([right_leg_x - 2, base_y - 1, right_leg_x + 2, base_y + 1], fill=SHOE, outline=OUTLINE)

        # --- Head (back of head, dark hair) ---
        ellipse(draw, body_cx, head_cy, 8, 8, HAIR)
        ellipse(draw, body_cx, head_cy - 1, 7, 7, HAIR_DARK)

        # --- Collar points (visible from behind) ---
        draw.polygon([
            (body_cx - 8, body_cy - 6),
            (body_cx - 5, body_cy - 9),
            (body_cx - 5, body_cy - 4),
            (body_cx - 7, body_cy - 4),
        ], fill=COLLAR, outline=OUTLINE)
        draw.polygon([
            (body_cx + 8, body_cy - 6),
            (body_cx + 5, body_cy - 9),
            (body_cx + 5, body_cy - 4),
            (body_cx + 7, body_cy - 4),
        ], fill=COLLAR, outline=OUTLINE)

    elif direction == LEFT:
        # --- Cape (flowing behind to the right) ---
        cape_sway = walk_offset
        draw.polygon([
            (body_cx + 2, body_cy - 5),
            (body_cx + 10, body_cy - 3),
            (body_cx + 12 + cape_sway, base_y + 1),
            (body_cx + 2, base_y + 1),
        ], fill=CAPE, outline=OUTLINE)
        draw.polygon([
            (body_cx + 3, body_cy - 3),
            (body_cx + 8, body_cy - 2),
            (body_cx + 9 + cape_sway, base_y - 1),
            (body_cx + 3, base_y - 1),
        ], fill=CAPE_LIGHT, outline=None)

        # --- Legs ---
        leg_x = body_cx - 2
        front_leg_y = base_y - 5 + walk_offset
        back_leg_y = base_y - 5 - walk_offset
        draw.rectangle([leg_x - 2, front_leg_y, leg_x + 1, base_y - 1], fill=PANTS, outline=OUTLINE)
        draw.rectangle([leg_x + 1, back_leg_y, leg_x + 4, base_y - 1], fill=PANTS, outline=OUTLINE)
        draw.rectangle([leg_x - 3, base_y - 1, leg_x + 1, base_y + 1], fill=SHOE, outline=OUTLINE)
        draw.rectangle([leg_x + 1, base_y - 1, leg_x + 5, base_y + 1], fill=SHOE, outline=OUTLINE)

        # --- Body (vest, side view) ---
        draw.polygon([
            (body_cx - 5, body_cy - 4),
            (body_cx + 5, body_cy - 4),
            (body_cx + 4, body_cy + 5),
            (body_cx - 4, body_cy + 5),
        ], fill=VEST, outline=OUTLINE)

        # --- Collar (left side, one point visible) ---
        draw.polygon([
            (body_cx - 5, body_cy - 6),
            (body_cx - 2, body_cy - 9),
            (body_cx - 2, body_cy - 4),
            (body_cx - 5, body_cy - 4),
        ], fill=COLLAR, outline=OUTLINE)

        # --- Arm (front arm) ---
        arm_y = body_cy - 1 + walk_offset
        draw.rectangle([body_cx - 7, arm_y - 1, body_cx - 4, arm_y + 4], fill=CAPE, outline=OUTLINE)
        draw.rectangle([body_cx - 7, arm_y + 4, body_cx - 4, arm_y + 6], fill=SKIN_PALE, outline=OUTLINE)

        # --- Head (facing left) ---
        ellipse(draw, body_cx - 1, head_cy, 7, 8, SKIN_PALE)
        # Hair
        draw.polygon([
            (body_cx - 8, head_cy - 2),
            (body_cx - 1, head_cy - 10),
            (body_cx + 5, head_cy - 2),
            (body_cx + 4, head_cy - 5),
            (body_cx - 1, head_cy - 7),
            (body_cx - 7, head_cy - 5),
        ], fill=HAIR, outline=OUTLINE)

        # Eye
        draw.rectangle([body_cx - 6, head_cy, body_cx - 3, head_cy + 2], fill=EYE_RED)
        draw.point((body_cx - 4, head_cy + 1), fill=EYE_CORE)

        # Mouth + fang
        draw.line([(body_cx - 5, head_cy + 4), (body_cx - 2, head_cy + 4)], fill=MOUTH_DARK, width=1)
        draw.line([(body_cx - 4, head_cy + 4), (body_cx - 4, head_cy + 6)], fill=FANG, width=1)

    elif direction == RIGHT:
        # --- Cape (flowing behind to the left) ---
        cape_sway = walk_offset
        draw.polygon([
            (body_cx - 2, body_cy - 5),
            (body_cx - 10, body_cy - 3),
            (body_cx - 12 + cape_sway, base_y + 1),
            (body_cx - 2, base_y + 1),
        ], fill=CAPE, outline=OUTLINE)
        draw.polygon([
            (body_cx - 3, body_cy - 3),
            (body_cx - 8, body_cy - 2),
            (body_cx - 9 + cape_sway, base_y - 1),
            (body_cx - 3, base_y - 1),
        ], fill=CAPE_LIGHT, outline=None)

        # --- Legs ---
        leg_x = body_cx + 2
        front_leg_y = base_y - 5 - walk_offset
        back_leg_y = base_y - 5 + walk_offset
        draw.rectangle([leg_x - 1, front_leg_y, leg_x + 2, base_y - 1], fill=PANTS, outline=OUTLINE)
        draw.rectangle([leg_x - 4, back_leg_y, leg_x - 1, base_y - 1], fill=PANTS, outline=OUTLINE)
        draw.rectangle([leg_x - 1, base_y - 1, leg_x + 3, base_y + 1], fill=SHOE, outline=OUTLINE)
        draw.rectangle([leg_x - 5, base_y - 1, leg_x - 1, base_y + 1], fill=SHOE, outline=OUTLINE)

        # --- Body (vest, side view) ---
        draw.polygon([
            (body_cx - 5, body_cy - 4),
            (body_cx + 5, body_cy - 4),
            (body_cx + 4, body_cy + 5),
            (body_cx - 4, body_cy + 5),
        ], fill=VEST, outline=OUTLINE)

        # --- Collar (right side, one point visible) ---
        draw.polygon([
            (body_cx + 5, body_cy - 6),
            (body_cx + 2, body_cy - 9),
            (body_cx + 2, body_cy - 4),
            (body_cx + 5, body_cy - 4),
        ], fill=COLLAR, outline=OUTLINE)

        # --- Arm (front arm) ---
        arm_y = body_cy - 1 - walk_offset
        draw.rectangle([body_cx + 4, arm_y - 1, body_cx + 7, arm_y + 4], fill=CAPE, outline=OUTLINE)
        draw.rectangle([body_cx + 4, arm_y + 4, body_cx + 7, arm_y + 6], fill=SKIN_PALE, outline=OUTLINE)

        # --- Head (facing right) ---
        ellipse(draw, body_cx + 1, head_cy, 7, 8, SKIN_PALE)
        # Hair
        draw.polygon([
            (body_cx - 5, head_cy - 2),
            (body_cx + 1, head_cy - 10),
            (body_cx + 8, head_cy - 2),
            (body_cx + 7, head_cy - 5),
            (body_cx + 1, head_cy - 7),
            (body_cx - 4, head_cy - 5),
        ], fill=HAIR, outline=OUTLINE)

        # Eye
        draw.rectangle([body_cx + 3, head_cy, body_cx + 6, head_cy + 2], fill=EYE_RED)
        draw.point((body_cx + 4, head_cy + 1), fill=EYE_CORE)

        # Mouth + fang
        draw.line([(body_cx + 2, head_cy + 4), (body_cx + 5, head_cy + 4)], fill=MOUTH_DARK, width=1)
        draw.line([(body_cx + 4, head_cy + 4), (body_cx + 4, head_cy + 6)], fill=FANG, width=1)


def main():
    img = Image.new("RGBA", (IMG_W, IMG_H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    for direction in range(ROWS):
        for frame in range(COLS):
            ox = frame * FRAME_SIZE
            oy = direction * FRAME_SIZE
            draw_vampire(draw, ox, oy, direction, frame)

    img.save("sprites/vampire.png")
    print(f"Generated sprites/vampire.png ({IMG_W}x{IMG_H})")


if __name__ == "__main__":
    main()
