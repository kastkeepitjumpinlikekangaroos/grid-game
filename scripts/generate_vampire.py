#!/usr/bin/env python3
"""Generate sprites/vampire.png — 4-column x 4-row character spritesheet.

128x128 PNG, 32x32 per frame.
Row layout: Down=0, Up=1, Left=2, Right=3
4 walking animation frames per direction.

Style matches Spaceman/Gladiator: big round head, round body, small limbs.
Theme: Gothic vampire — dramatic bat-wing scalloped cape, tall sharp collar,
visible fangs with blood tips, gold medallion, menacing shadowed eyes,
styled widow's peak hair, gold cape clasps.
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
CAPE_INNER_RED = (140, 25, 30)
COLLAR = (60, 12, 22)
COLLAR_HIGHLIGHT = (90, 18, 30)
VEST_DARK = (30, 25, 35)
VEST = (45, 35, 50)
VEST_HIGHLIGHT = (55, 45, 60)
SKIN_PALE = (220, 210, 200)
SKIN_SHADOW = (180, 170, 160)
HAIR_DARK = (25, 15, 20)
HAIR = (40, 20, 30)
HAIR_HIGHLIGHT = (55, 28, 40)
EYE_RED = (220, 30, 30)
EYE_GLOW = (255, 60, 50)
EYE_CORE = (255, 120, 100)
EYE_SHADOW = (40, 15, 20)
MOUTH_DARK = (60, 20, 25)
FANG = (240, 235, 230)
BLOOD_TIP = (180, 10, 10)
PANTS = (25, 20, 30)
SHOE = (20, 15, 20)
GOLD = (220, 180, 50)
GOLD_BRIGHT = (255, 220, 80)
MEDALLION_GEM = (200, 25, 40)

DOWN, UP, LEFT, RIGHT = 0, 1, 2, 3


def ellipse(draw, cx, cy, rx, ry, fill, outline=OUTLINE):
    draw.ellipse([cx - rx, cy - ry, cx + rx, cy + ry], fill=fill, outline=outline)


def draw_scalloped_cape_bottom(draw, left_x, right_x, y, fill, num_points=5):
    """Draw a bat-wing scalloped (zigzag pointed) bottom edge for the cape."""
    width = right_x - left_x
    seg = width / num_points
    for i in range(num_points):
        px = left_x + i * seg
        # Each scallop: triangle pointing downward
        draw.polygon([
            (px, y),
            (px + seg, y),
            (px + seg / 2, y + 3),
        ], fill=fill, outline=OUTLINE)


def draw_vampire(draw, ox, oy, direction, frame):
    """Draw a single vampire frame at offset (ox, oy)."""
    bob = [0, -1, 0, -1][frame]
    walk_offset = [-1, 0, 1, 0][frame]

    base_y = oy + 29 + bob
    body_cx = ox + 16
    body_cy = base_y - 11
    head_cy = body_cy - 10

    if direction == DOWN:
        # --- Cape (behind body, dramatic and wide with scalloped bottom) ---
        cape_sway = walk_offset
        cape_left = body_cx - 13 + cape_sway
        cape_right = body_cx + 13 + cape_sway
        cape_bottom = base_y + 1
        # Main cape shape — wider than before
        draw.polygon([
            (body_cx - 10, body_cy - 4),
            (body_cx + 10, body_cy - 4),
            (cape_right, cape_bottom),
            (cape_left, cape_bottom),
        ], fill=CAPE_DARK, outline=OUTLINE)
        # Scalloped bat-wing bottom edge
        draw_scalloped_cape_bottom(draw, cape_left, cape_right, cape_bottom - 1, CAPE_DARK, num_points=5)

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

        # --- Medallion/brooch at center chest ---
        draw.point((body_cx, body_cy - 4), fill=GOLD_BRIGHT)
        draw.point((body_cx - 1, body_cy - 4), fill=GOLD)
        draw.point((body_cx + 1, body_cy - 4), fill=GOLD)
        draw.point((body_cx, body_cy - 5), fill=GOLD)
        draw.point((body_cx, body_cy - 3), fill=MEDALLION_GEM)

        # --- Tall sharp collar (extends to ear level) ---
        draw.polygon([
            (body_cx - 9, body_cy - 5),
            (body_cx - 5, body_cy - 12),
            (body_cx - 4, body_cy - 9),
            (body_cx - 5, body_cy - 4),
            (body_cx - 8, body_cy - 4),
        ], fill=COLLAR, outline=OUTLINE)
        draw.polygon([
            (body_cx + 9, body_cy - 5),
            (body_cx + 5, body_cy - 12),
            (body_cx + 4, body_cy - 9),
            (body_cx + 5, body_cy - 4),
            (body_cx + 8, body_cy - 4),
        ], fill=COLLAR, outline=OUTLINE)
        # Collar highlight on inner edges
        draw.line([(body_cx - 5, body_cy - 10), (body_cx - 5, body_cy - 5)], fill=COLLAR_HIGHLIGHT, width=1)
        draw.line([(body_cx + 5, body_cy - 10), (body_cx + 5, body_cy - 5)], fill=COLLAR_HIGHLIGHT, width=1)

        # --- Cape clasps (gold pixels where cape meets collar) ---
        draw.point((body_cx - 8, body_cy - 4), fill=GOLD_BRIGHT)
        draw.point((body_cx + 8, body_cy - 4), fill=GOLD_BRIGHT)

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

        # --- Hair (styled widow's peak — sharper point, wavy sides) ---
        draw.polygon([
            (body_cx - 9, head_cy - 1),
            (body_cx - 8, head_cy - 4),
            (body_cx - 6, head_cy - 6),
            (body_cx - 3, head_cy - 7),
            (body_cx, head_cy - 11),
            (body_cx + 3, head_cy - 7),
            (body_cx + 6, head_cy - 6),
            (body_cx + 8, head_cy - 4),
            (body_cx + 9, head_cy - 1),
            (body_cx + 7, head_cy - 3),
            (body_cx + 5, head_cy - 5),
            (body_cx, head_cy - 7),
            (body_cx - 5, head_cy - 5),
            (body_cx - 7, head_cy - 3),
        ], fill=HAIR, outline=OUTLINE)
        # Hair wave highlights on sides
        draw.point((body_cx - 8, head_cy - 2), fill=HAIR_HIGHLIGHT)
        draw.point((body_cx + 8, head_cy - 2), fill=HAIR_HIGHLIGHT)
        draw.point((body_cx - 7, head_cy - 4), fill=HAIR_HIGHLIGHT)
        draw.point((body_cx + 7, head_cy - 4), fill=HAIR_HIGHLIGHT)

        # --- Eye brow shadow (darker area above eyes) ---
        draw.line([(body_cx - 6, head_cy - 1), (body_cx - 2, head_cy - 1)], fill=EYE_SHADOW, width=1)
        draw.line([(body_cx + 2, head_cy - 1), (body_cx + 6, head_cy - 1)], fill=EYE_SHADOW, width=1)

        # --- Eyes (glowing red, larger, more menacing) ---
        draw.rectangle([body_cx - 6, head_cy, body_cx - 2, head_cy + 2], fill=EYE_RED)
        draw.rectangle([body_cx + 2, head_cy, body_cx + 6, head_cy + 2], fill=EYE_RED)
        # Bright glow core
        draw.point((body_cx - 4, head_cy + 1), fill=EYE_GLOW)
        draw.point((body_cx - 3, head_cy + 1), fill=EYE_CORE)
        draw.point((body_cx + 4, head_cy + 1), fill=EYE_GLOW)
        draw.point((body_cx + 3, head_cy + 1), fill=EYE_CORE)

        # --- Mouth with longer fangs + blood tips ---
        draw.line([(body_cx - 2, head_cy + 4), (body_cx + 2, head_cy + 4)], fill=MOUTH_DARK, width=1)
        # Left fang — 2px tall
        draw.line([(body_cx - 2, head_cy + 4), (body_cx - 2, head_cy + 6)], fill=FANG, width=1)
        draw.point((body_cx - 2, head_cy + 6), fill=BLOOD_TIP)
        # Right fang — 2px tall
        draw.line([(body_cx + 2, head_cy + 4), (body_cx + 2, head_cy + 6)], fill=FANG, width=1)
        draw.point((body_cx + 2, head_cy + 6), fill=BLOOD_TIP)

    elif direction == UP:
        # --- Cape (visible from behind, dramatic, scalloped, red inner lining) ---
        cape_sway = walk_offset
        cape_left = body_cx - 14 + cape_sway
        cape_right = body_cx + 14 + cape_sway
        cape_bottom = base_y + 1
        # Outer cape — wide
        draw.polygon([
            (body_cx - 11, body_cy - 5),
            (body_cx + 11, body_cy - 5),
            (cape_right, cape_bottom),
            (cape_left, cape_bottom),
        ], fill=CAPE, outline=OUTLINE)
        # Inner red lining visible from behind
        draw.polygon([
            (body_cx - 8, body_cy - 3),
            (body_cx + 8, body_cy - 3),
            (body_cx + 10 + cape_sway, cape_bottom - 2),
            (body_cx - 10 + cape_sway, cape_bottom - 2),
        ], fill=CAPE_INNER_RED, outline=None)
        # Scalloped bat-wing bottom edge
        draw_scalloped_cape_bottom(draw, cape_left, cape_right, cape_bottom - 1, CAPE, num_points=6)

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
        # Hair wave detail on sides
        draw.point((body_cx - 7, head_cy), fill=HAIR_HIGHLIGHT)
        draw.point((body_cx + 7, head_cy), fill=HAIR_HIGHLIGHT)

        # --- Tall sharp collar points (visible from behind, up to ear level) ---
        draw.polygon([
            (body_cx - 9, body_cy - 5),
            (body_cx - 5, body_cy - 12),
            (body_cx - 4, body_cy - 9),
            (body_cx - 5, body_cy - 4),
            (body_cx - 8, body_cy - 4),
        ], fill=COLLAR, outline=OUTLINE)
        draw.polygon([
            (body_cx + 9, body_cy - 5),
            (body_cx + 5, body_cy - 12),
            (body_cx + 4, body_cy - 9),
            (body_cx + 5, body_cy - 4),
            (body_cx + 8, body_cy - 4),
        ], fill=COLLAR, outline=OUTLINE)

        # --- Cape clasps ---
        draw.point((body_cx - 8, body_cy - 4), fill=GOLD_BRIGHT)
        draw.point((body_cx + 8, body_cy - 4), fill=GOLD_BRIGHT)

    elif direction == LEFT:
        # --- Cape (flowing behind to the right, wider, scalloped) ---
        cape_sway = walk_offset
        cape_right = body_cx + 14 + cape_sway
        cape_bottom = base_y + 1
        draw.polygon([
            (body_cx + 2, body_cy - 5),
            (body_cx + 11, body_cy - 3),
            (cape_right, cape_bottom),
            (body_cx + 2, cape_bottom),
        ], fill=CAPE, outline=OUTLINE)
        # Inner lining
        draw.polygon([
            (body_cx + 3, body_cy - 3),
            (body_cx + 9, body_cy - 2),
            (body_cx + 11 + cape_sway, cape_bottom - 2),
            (body_cx + 3, cape_bottom - 2),
        ], fill=CAPE_LIGHT, outline=None)
        # Scalloped bottom
        draw_scalloped_cape_bottom(draw, body_cx + 2, cape_right, cape_bottom - 1, CAPE, num_points=4)

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

        # --- Medallion (side view — just one gold dot visible) ---
        draw.point((body_cx - 3, body_cy - 4), fill=GOLD_BRIGHT)
        draw.point((body_cx - 3, body_cy - 3), fill=MEDALLION_GEM)

        # --- Tall sharp collar (left side, one point visible, taller) ---
        draw.polygon([
            (body_cx - 6, body_cy - 5),
            (body_cx - 2, body_cy - 12),
            (body_cx - 1, body_cy - 9),
            (body_cx - 2, body_cy - 4),
            (body_cx - 6, body_cy - 4),
        ], fill=COLLAR, outline=OUTLINE)
        draw.line([(body_cx - 2, body_cy - 10), (body_cx - 2, body_cy - 5)], fill=COLLAR_HIGHLIGHT, width=1)

        # --- Cape clasp ---
        draw.point((body_cx + 3, body_cy - 4), fill=GOLD_BRIGHT)

        # --- Arm (front arm) ---
        arm_y = body_cy - 1 + walk_offset
        draw.rectangle([body_cx - 7, arm_y - 1, body_cx - 4, arm_y + 4], fill=CAPE, outline=OUTLINE)
        draw.rectangle([body_cx - 7, arm_y + 4, body_cx - 4, arm_y + 6], fill=SKIN_PALE, outline=OUTLINE)

        # --- Head (facing left) ---
        ellipse(draw, body_cx - 1, head_cy, 7, 8, SKIN_PALE)

        # --- Hair (styled widow's peak, sharper, wavy sides) ---
        draw.polygon([
            (body_cx - 9, head_cy - 1),
            (body_cx - 8, head_cy - 4),
            (body_cx - 5, head_cy - 6),
            (body_cx - 1, head_cy - 11),
            (body_cx + 3, head_cy - 6),
            (body_cx + 5, head_cy - 3),
            (body_cx + 6, head_cy - 1),
            (body_cx + 4, head_cy - 3),
            (body_cx + 2, head_cy - 5),
            (body_cx - 1, head_cy - 7),
            (body_cx - 5, head_cy - 5),
            (body_cx - 7, head_cy - 3),
        ], fill=HAIR, outline=OUTLINE)
        draw.point((body_cx - 8, head_cy - 2), fill=HAIR_HIGHLIGHT)
        draw.point((body_cx + 5, head_cy - 2), fill=HAIR_HIGHLIGHT)

        # --- Eye brow shadow ---
        draw.line([(body_cx - 7, head_cy - 1), (body_cx - 3, head_cy - 1)], fill=EYE_SHADOW, width=1)

        # --- Eye (larger, glowing) ---
        draw.rectangle([body_cx - 7, head_cy, body_cx - 3, head_cy + 2], fill=EYE_RED)
        draw.point((body_cx - 5, head_cy + 1), fill=EYE_GLOW)
        draw.point((body_cx - 4, head_cy + 1), fill=EYE_CORE)

        # --- Mouth + fang (2px tall with blood tip) ---
        draw.line([(body_cx - 5, head_cy + 4), (body_cx - 2, head_cy + 4)], fill=MOUTH_DARK, width=1)
        draw.line([(body_cx - 4, head_cy + 4), (body_cx - 4, head_cy + 6)], fill=FANG, width=1)
        draw.point((body_cx - 4, head_cy + 6), fill=BLOOD_TIP)

    elif direction == RIGHT:
        # --- Cape (flowing behind to the left, wider, scalloped) ---
        cape_sway = walk_offset
        cape_left = body_cx - 14 + cape_sway
        cape_bottom = base_y + 1
        draw.polygon([
            (body_cx - 2, body_cy - 5),
            (body_cx - 11, body_cy - 3),
            (cape_left, cape_bottom),
            (body_cx - 2, cape_bottom),
        ], fill=CAPE, outline=OUTLINE)
        # Inner lining
        draw.polygon([
            (body_cx - 3, body_cy - 3),
            (body_cx - 9, body_cy - 2),
            (body_cx - 11 + cape_sway, cape_bottom - 2),
            (body_cx - 3, cape_bottom - 2),
        ], fill=CAPE_LIGHT, outline=None)
        # Scalloped bottom
        draw_scalloped_cape_bottom(draw, cape_left, body_cx - 2, cape_bottom - 1, CAPE, num_points=4)

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

        # --- Medallion (side view) ---
        draw.point((body_cx + 3, body_cy - 4), fill=GOLD_BRIGHT)
        draw.point((body_cx + 3, body_cy - 3), fill=MEDALLION_GEM)

        # --- Tall sharp collar (right side, one point visible, taller) ---
        draw.polygon([
            (body_cx + 6, body_cy - 5),
            (body_cx + 2, body_cy - 12),
            (body_cx + 1, body_cy - 9),
            (body_cx + 2, body_cy - 4),
            (body_cx + 6, body_cy - 4),
        ], fill=COLLAR, outline=OUTLINE)
        draw.line([(body_cx + 2, body_cy - 10), (body_cx + 2, body_cy - 5)], fill=COLLAR_HIGHLIGHT, width=1)

        # --- Cape clasp ---
        draw.point((body_cx - 3, body_cy - 4), fill=GOLD_BRIGHT)

        # --- Arm (front arm) ---
        arm_y = body_cy - 1 - walk_offset
        draw.rectangle([body_cx + 4, arm_y - 1, body_cx + 7, arm_y + 4], fill=CAPE, outline=OUTLINE)
        draw.rectangle([body_cx + 4, arm_y + 4, body_cx + 7, arm_y + 6], fill=SKIN_PALE, outline=OUTLINE)

        # --- Head (facing right) ---
        ellipse(draw, body_cx + 1, head_cy, 7, 8, SKIN_PALE)

        # --- Hair (styled widow's peak, sharper, wavy sides) ---
        draw.polygon([
            (body_cx - 6, head_cy - 1),
            (body_cx - 5, head_cy - 3),
            (body_cx - 3, head_cy - 6),
            (body_cx + 1, head_cy - 11),
            (body_cx + 5, head_cy - 6),
            (body_cx + 8, head_cy - 4),
            (body_cx + 9, head_cy - 1),
            (body_cx + 7, head_cy - 3),
            (body_cx + 5, head_cy - 5),
            (body_cx + 1, head_cy - 7),
            (body_cx - 2, head_cy - 5),
            (body_cx - 4, head_cy - 3),
        ], fill=HAIR, outline=OUTLINE)
        draw.point((body_cx + 8, head_cy - 2), fill=HAIR_HIGHLIGHT)
        draw.point((body_cx - 5, head_cy - 2), fill=HAIR_HIGHLIGHT)

        # --- Eye brow shadow ---
        draw.line([(body_cx + 3, head_cy - 1), (body_cx + 7, head_cy - 1)], fill=EYE_SHADOW, width=1)

        # --- Eye (larger, glowing) ---
        draw.rectangle([body_cx + 3, head_cy, body_cx + 7, head_cy + 2], fill=EYE_RED)
        draw.point((body_cx + 5, head_cy + 1), fill=EYE_GLOW)
        draw.point((body_cx + 4, head_cy + 1), fill=EYE_CORE)

        # --- Mouth + fang (2px tall with blood tip) ---
        draw.line([(body_cx + 2, head_cy + 4), (body_cx + 5, head_cy + 4)], fill=MOUTH_DARK, width=1)
        draw.line([(body_cx + 4, head_cy + 4), (body_cx + 4, head_cy + 6)], fill=FANG, width=1)
        draw.point((body_cx + 4, head_cy + 6), fill=BLOOD_TIP)


def main():
    img = Image.new("RGBA", (IMG_W, IMG_H), (0, 0, 0, 0))

    for direction in range(ROWS):
        for frame in range(COLS):
            frame_img = Image.new("RGBA", (FRAME_SIZE, FRAME_SIZE), (0, 0, 0, 0))
            frame_draw = ImageDraw.Draw(frame_img)
            draw_vampire(frame_draw, 0, 0, direction, frame)
            img.paste(frame_img, (frame * FRAME_SIZE, direction * FRAME_SIZE))

    img.save("sprites/vampire.png")
    print(f"Generated sprites/vampire.png ({IMG_W}x{IMG_H})")


if __name__ == "__main__":
    main()
