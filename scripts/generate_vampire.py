#!/usr/bin/env python3
"""Generate sprites/vampire.png — 4-column x 4-row character spritesheet.

256x256 PNG, 64x64 per frame.
Row layout: Down=0, Up=1, Left=2, Right=3
4 walking animation frames per direction.

Style matches Spaceman/Gladiator: big round head, round body, small limbs.
Theme: Gothic vampire — dramatic bat-wing scalloped cape, tall sharp collar,
visible fangs with blood tips, gold medallion, menacing shadowed eyes,
styled widow's peak hair, gold cape clasps.
Enhanced 64x64: high collar detail (pointed tips), fang dots below mouth,
pale complexion gradient, red eye glow with larger radius.
"""

from PIL import Image, ImageDraw

FRAME_SIZE = 64
COLS = 4
ROWS = 4
IMG_W = FRAME_SIZE * COLS   # 256
IMG_H = FRAME_SIZE * ROWS   # 256

# Colors
OUTLINE = (20, 10, 15)
CAPE_DARK = (50, 10, 20)
CAPE = (80, 15, 25)
CAPE_LIGHT = (110, 20, 35)
CAPE_INNER_RED = (140, 25, 30)
COLLAR = (60, 12, 22)
COLLAR_HIGHLIGHT = (90, 18, 30)
COLLAR_TIP = (100, 22, 35)
VEST_DARK = (30, 25, 35)
VEST = (45, 35, 50)
VEST_HIGHLIGHT = (55, 45, 60)
VEST_BUTTON = (160, 140, 55)
SKIN_PALE = (220, 210, 200)
SKIN_SHADOW = (180, 170, 160)
SKIN_HIGHLIGHT = (235, 228, 220)
HAIR_DARK = (25, 15, 20)
HAIR = (40, 20, 30)
HAIR_HIGHLIGHT = (55, 28, 40)
EYE_RED = (220, 30, 30)
EYE_GLOW = (255, 60, 50)
EYE_CORE = (255, 120, 100)
EYE_SHADOW = (40, 15, 20)
EYE_OUTER_GLOW = (180, 20, 20, 100)
MOUTH_DARK = (60, 20, 25)
FANG = (240, 235, 230)
BLOOD_TIP = (180, 10, 10)
BLOOD_DROP = (160, 8, 8)
PANTS = (25, 20, 30)
SHOE = (20, 15, 20)
SHOE_BUCKLE = (160, 140, 55)
GOLD = (220, 180, 50)
GOLD_BRIGHT = (255, 220, 80)
MEDALLION_GEM = (200, 25, 40)
MEDALLION_RIM = (180, 150, 40)

DOWN, UP, LEFT, RIGHT = 0, 1, 2, 3


def ellipse(draw, cx, cy, rx, ry, fill, outline=OUTLINE):
    draw.ellipse([cx - rx, cy - ry, cx + rx, cy + ry], fill=fill, outline=outline)


def draw_scalloped_cape_bottom(draw, left_x, right_x, y, fill, num_points=5):
    """Draw a bat-wing scalloped (zigzag pointed) bottom edge for the cape."""
    width = right_x - left_x
    seg = width / num_points
    for i in range(num_points):
        px = left_x + i * seg
        # Each scallop: triangle pointing downward (doubled size)
        draw.polygon([
            (px, y),
            (px + seg, y),
            (px + seg / 2, y + 6),
        ], fill=fill, outline=OUTLINE)


def draw_collar_pointed(draw, body_cx, body_cy, direction):
    """Draw tall sharp collar with pointed tips."""
    if direction == DOWN:
        # Left collar point (extends to ear level, with pointed tip)
        draw.polygon([
            (body_cx - 18, body_cy - 10),
            (body_cx - 10, body_cy - 24),
            (body_cx - 9, body_cy - 22),   # Pointed tip detail
            (body_cx - 8, body_cy - 18),
            (body_cx - 10, body_cy - 8),
            (body_cx - 16, body_cy - 8),
        ], fill=COLLAR, outline=OUTLINE)
        # Right collar point
        draw.polygon([
            (body_cx + 18, body_cy - 10),
            (body_cx + 10, body_cy - 24),
            (body_cx + 9, body_cy - 22),
            (body_cx + 8, body_cy - 18),
            (body_cx + 10, body_cy - 8),
            (body_cx + 16, body_cy - 8),
        ], fill=COLLAR, outline=OUTLINE)
        # Collar highlight on inner edges
        draw.line([(body_cx - 10, body_cy - 20), (body_cx - 10, body_cy - 10)],
                  fill=COLLAR_HIGHLIGHT, width=2)
        draw.line([(body_cx + 10, body_cy - 20), (body_cx + 10, body_cy - 10)],
                  fill=COLLAR_HIGHLIGHT, width=2)
        # Pointed tip highlights
        draw.point((body_cx - 10, body_cy - 24), fill=COLLAR_TIP)
        draw.point((body_cx + 10, body_cy - 24), fill=COLLAR_TIP)
    elif direction == UP:
        draw.polygon([
            (body_cx - 18, body_cy - 10),
            (body_cx - 10, body_cy - 24),
            (body_cx - 9, body_cy - 22),
            (body_cx - 8, body_cy - 18),
            (body_cx - 10, body_cy - 8),
            (body_cx - 16, body_cy - 8),
        ], fill=COLLAR, outline=OUTLINE)
        draw.polygon([
            (body_cx + 18, body_cy - 10),
            (body_cx + 10, body_cy - 24),
            (body_cx + 9, body_cy - 22),
            (body_cx + 8, body_cy - 18),
            (body_cx + 10, body_cy - 8),
            (body_cx + 16, body_cy - 8),
        ], fill=COLLAR, outline=OUTLINE)
        draw.point((body_cx - 10, body_cy - 24), fill=COLLAR_TIP)
        draw.point((body_cx + 10, body_cy - 24), fill=COLLAR_TIP)
    elif direction == LEFT:
        draw.polygon([
            (body_cx - 12, body_cy - 10),
            (body_cx - 4, body_cy - 24),
            (body_cx - 3, body_cy - 22),
            (body_cx - 2, body_cy - 18),
            (body_cx - 4, body_cy - 8),
            (body_cx - 12, body_cy - 8),
        ], fill=COLLAR, outline=OUTLINE)
        draw.line([(body_cx - 4, body_cy - 20), (body_cx - 4, body_cy - 10)],
                  fill=COLLAR_HIGHLIGHT, width=2)
        draw.point((body_cx - 4, body_cy - 24), fill=COLLAR_TIP)
    elif direction == RIGHT:
        draw.polygon([
            (body_cx + 12, body_cy - 10),
            (body_cx + 4, body_cy - 24),
            (body_cx + 3, body_cy - 22),
            (body_cx + 2, body_cy - 18),
            (body_cx + 4, body_cy - 8),
            (body_cx + 12, body_cy - 8),
        ], fill=COLLAR, outline=OUTLINE)
        draw.line([(body_cx + 4, body_cy - 20), (body_cx + 4, body_cy - 10)],
                  fill=COLLAR_HIGHLIGHT, width=2)
        draw.point((body_cx + 4, body_cy - 24), fill=COLLAR_TIP)


def draw_medallion(draw, cx, cy, full=True):
    """Draw a gold medallion with gem."""
    if full:
        # Full medallion (front view)
        ellipse(draw, cx, cy - 2, 4, 4, MEDALLION_RIM, outline=OUTLINE)
        draw.rectangle([cx - 2, cy - 4, cx + 2, cy], fill=GOLD)
        draw.point((cx, cy - 2), fill=GOLD_BRIGHT)
        draw.rectangle([cx - 1, cy - 1, cx + 1, cy + 1], fill=MEDALLION_GEM)
        # Chain hint
        draw.point((cx - 3, cy - 6), fill=GOLD)
        draw.point((cx + 3, cy - 6), fill=GOLD)
    else:
        # Side view medallion
        draw.rectangle([cx - 1, cy - 2, cx + 1, cy + 1], fill=GOLD_BRIGHT)
        draw.point((cx, cy), fill=MEDALLION_GEM)


def draw_vampire(draw, ox, oy, direction, frame):
    """Draw a single vampire frame at offset (ox, oy)."""
    bob = [0, -2, 0, -1][frame]
    walk_offset = [-2, 0, 2, 0][frame]

    base_y = oy + 58 + bob
    body_cx = ox + 32
    body_cy = base_y - 22
    head_cy = body_cy - 20

    if direction == DOWN:
        # --- Cape (behind body, dramatic and wide with scalloped bottom) ---
        cape_sway = walk_offset
        cape_left = body_cx - 26 + cape_sway
        cape_right = body_cx + 26 + cape_sway
        cape_bottom = base_y + 2
        # Main cape shape — wider than before
        draw.polygon([
            (body_cx - 20, body_cy - 8),
            (body_cx + 20, body_cy - 8),
            (cape_right, cape_bottom),
            (cape_left, cape_bottom),
        ], fill=CAPE_DARK, outline=OUTLINE)
        # Scalloped bat-wing bottom edge
        draw_scalloped_cape_bottom(draw, cape_left, cape_right, cape_bottom - 2, CAPE_DARK, num_points=5)

        # --- Legs ---
        left_leg_x = body_cx - 6 + walk_offset
        right_leg_x = body_cx + 6 - walk_offset
        draw.rectangle([left_leg_x - 4, base_y - 10, left_leg_x + 2, base_y - 2], fill=PANTS, outline=OUTLINE)
        draw.rectangle([right_leg_x - 2, base_y - 10, right_leg_x + 4, base_y - 2], fill=PANTS, outline=OUTLINE)
        draw.rectangle([left_leg_x - 4, base_y - 2, left_leg_x + 4, base_y + 2], fill=SHOE, outline=OUTLINE)
        draw.rectangle([right_leg_x - 4, base_y - 2, right_leg_x + 4, base_y + 2], fill=SHOE, outline=OUTLINE)
        # Shoe buckles
        draw.rectangle([left_leg_x - 1, base_y - 1, left_leg_x + 1, base_y + 1], fill=SHOE_BUCKLE)
        draw.rectangle([right_leg_x - 1, base_y - 1, right_leg_x + 1, base_y + 1], fill=SHOE_BUCKLE)

        # --- Body (vest) ---
        draw.polygon([
            (body_cx - 14, body_cy - 8),
            (body_cx + 14, body_cy - 8),
            (body_cx + 12, body_cy + 10),
            (body_cx - 12, body_cy + 10),
        ], fill=VEST, outline=OUTLINE)
        # Vest center line
        draw.line([(body_cx, body_cy - 6), (body_cx, body_cy + 8)], fill=VEST_HIGHLIGHT, width=2)
        # Vest buttons
        for i in range(3):
            y = body_cy - 4 + i * 5
            draw.rectangle([body_cx - 1, y, body_cx + 1, y + 2], fill=VEST_BUTTON)

        # --- Medallion/brooch at center chest ---
        draw_medallion(draw, body_cx, body_cy - 6, full=True)

        # --- Tall sharp collar with pointed tips ---
        draw_collar_pointed(draw, body_cx, body_cy, DOWN)

        # --- Cape clasps (gold pixels where cape meets collar) ---
        draw.rectangle([body_cx - 17, body_cy - 9, body_cx - 15, body_cy - 7], fill=GOLD_BRIGHT)
        draw.rectangle([body_cx + 15, body_cy - 9, body_cx + 17, body_cy - 7], fill=GOLD_BRIGHT)

        # --- Arms ---
        left_arm_y = body_cy - 4 + walk_offset
        right_arm_y = body_cy - 4 - walk_offset
        draw.rectangle([body_cx - 20, left_arm_y - 2, body_cx - 14, left_arm_y + 8], fill=CAPE, outline=OUTLINE)
        draw.rectangle([body_cx + 14, right_arm_y - 2, body_cx + 20, right_arm_y + 8], fill=CAPE, outline=OUTLINE)
        # Pale hands
        draw.rectangle([body_cx - 20, left_arm_y + 8, body_cx - 14, left_arm_y + 12], fill=SKIN_PALE, outline=OUTLINE)
        draw.rectangle([body_cx + 14, right_arm_y + 8, body_cx + 20, right_arm_y + 12], fill=SKIN_PALE, outline=OUTLINE)

        # --- Head ---
        ellipse(draw, body_cx, head_cy, 16, 16, SKIN_PALE)
        # Pale complexion gradient — lighter highlight on forehead
        ellipse(draw, body_cx, head_cy - 4, 10, 8, SKIN_HIGHLIGHT, outline=None)

        # --- Hair (styled widow's peak — sharper point, wavy sides) ---
        draw.polygon([
            (body_cx - 18, head_cy - 2),
            (body_cx - 16, head_cy - 8),
            (body_cx - 12, head_cy - 12),
            (body_cx - 6, head_cy - 14),
            (body_cx, head_cy - 22),
            (body_cx + 6, head_cy - 14),
            (body_cx + 12, head_cy - 12),
            (body_cx + 16, head_cy - 8),
            (body_cx + 18, head_cy - 2),
            (body_cx + 14, head_cy - 6),
            (body_cx + 10, head_cy - 10),
            (body_cx, head_cy - 14),
            (body_cx - 10, head_cy - 10),
            (body_cx - 14, head_cy - 6),
        ], fill=HAIR, outline=OUTLINE)
        # Hair wave highlights on sides
        draw.point((body_cx - 16, head_cy - 4), fill=HAIR_HIGHLIGHT)
        draw.point((body_cx + 16, head_cy - 4), fill=HAIR_HIGHLIGHT)
        draw.point((body_cx - 14, head_cy - 8), fill=HAIR_HIGHLIGHT)
        draw.point((body_cx + 14, head_cy - 8), fill=HAIR_HIGHLIGHT)
        draw.point((body_cx - 12, head_cy - 10), fill=HAIR_HIGHLIGHT)
        draw.point((body_cx + 12, head_cy - 10), fill=HAIR_HIGHLIGHT)

        # --- Eye brow shadow (darker area above eyes) ---
        draw.line([(body_cx - 12, head_cy - 2), (body_cx - 4, head_cy - 2)], fill=EYE_SHADOW, width=2)
        draw.line([(body_cx + 4, head_cy - 2), (body_cx + 12, head_cy - 2)], fill=EYE_SHADOW, width=2)

        # --- Eyes (glowing red, larger, more menacing, with outer glow) ---
        # Larger eye rectangles
        draw.rectangle([body_cx - 12, head_cy, body_cx - 4, head_cy + 4], fill=EYE_RED)
        draw.rectangle([body_cx + 4, head_cy, body_cx + 12, head_cy + 4], fill=EYE_RED)
        # Bright glow core
        draw.rectangle([body_cx - 9, head_cy + 1, body_cx - 7, head_cy + 3], fill=EYE_GLOW)
        draw.rectangle([body_cx - 7, head_cy + 1, body_cx - 5, head_cy + 3], fill=EYE_CORE)
        draw.rectangle([body_cx + 7, head_cy + 1, body_cx + 9, head_cy + 3], fill=EYE_GLOW)
        draw.rectangle([body_cx + 5, head_cy + 1, body_cx + 7, head_cy + 3], fill=EYE_CORE)

        # --- Mouth with longer fangs + blood tips + blood drops ---
        draw.line([(body_cx - 4, head_cy + 8), (body_cx + 4, head_cy + 8)], fill=MOUTH_DARK, width=2)
        # Left fang — 4px tall
        draw.line([(body_cx - 4, head_cy + 8), (body_cx - 4, head_cy + 12)], fill=FANG, width=2)
        draw.point((body_cx - 4, head_cy + 12), fill=BLOOD_TIP)
        draw.point((body_cx - 4, head_cy + 13), fill=BLOOD_DROP)
        # Right fang — 4px tall
        draw.line([(body_cx + 4, head_cy + 8), (body_cx + 4, head_cy + 12)], fill=FANG, width=2)
        draw.point((body_cx + 4, head_cy + 12), fill=BLOOD_TIP)
        draw.point((body_cx + 4, head_cy + 13), fill=BLOOD_DROP)

    elif direction == UP:
        # --- Cape (visible from behind, dramatic, scalloped, red inner lining) ---
        cape_sway = walk_offset
        cape_left = body_cx - 28 + cape_sway
        cape_right = body_cx + 28 + cape_sway
        cape_bottom = base_y + 2
        # Outer cape — wide
        draw.polygon([
            (body_cx - 22, body_cy - 10),
            (body_cx + 22, body_cy - 10),
            (cape_right, cape_bottom),
            (cape_left, cape_bottom),
        ], fill=CAPE, outline=OUTLINE)
        # Inner red lining visible from behind
        draw.polygon([
            (body_cx - 16, body_cy - 6),
            (body_cx + 16, body_cy - 6),
            (body_cx + 20 + cape_sway, cape_bottom - 4),
            (body_cx - 20 + cape_sway, cape_bottom - 4),
        ], fill=CAPE_INNER_RED, outline=None)
        # Scalloped bat-wing bottom edge
        draw_scalloped_cape_bottom(draw, cape_left, cape_right, cape_bottom - 2, CAPE, num_points=6)

        # --- Legs (behind cape) ---
        left_leg_x = body_cx - 6 + walk_offset
        right_leg_x = body_cx + 6 - walk_offset
        draw.rectangle([left_leg_x - 4, base_y - 6, left_leg_x + 2, base_y - 2], fill=PANTS, outline=OUTLINE)
        draw.rectangle([right_leg_x - 2, base_y - 6, right_leg_x + 4, base_y - 2], fill=PANTS, outline=OUTLINE)
        draw.rectangle([left_leg_x - 4, base_y - 2, left_leg_x + 4, base_y + 2], fill=SHOE, outline=OUTLINE)
        draw.rectangle([right_leg_x - 4, base_y - 2, right_leg_x + 4, base_y + 2], fill=SHOE, outline=OUTLINE)

        # --- Head (back of head, dark hair) ---
        ellipse(draw, body_cx, head_cy, 16, 16, HAIR)
        ellipse(draw, body_cx, head_cy - 2, 14, 14, HAIR_DARK)
        # Hair wave detail on sides
        draw.point((body_cx - 14, head_cy), fill=HAIR_HIGHLIGHT)
        draw.point((body_cx + 14, head_cy), fill=HAIR_HIGHLIGHT)
        draw.point((body_cx - 12, head_cy - 4), fill=HAIR_HIGHLIGHT)
        draw.point((body_cx + 12, head_cy - 4), fill=HAIR_HIGHLIGHT)

        # --- Tall sharp collar points with pointed tips ---
        draw_collar_pointed(draw, body_cx, body_cy, UP)

        # --- Cape clasps ---
        draw.rectangle([body_cx - 17, body_cy - 9, body_cx - 15, body_cy - 7], fill=GOLD_BRIGHT)
        draw.rectangle([body_cx + 15, body_cy - 9, body_cx + 17, body_cy - 7], fill=GOLD_BRIGHT)

    elif direction == LEFT:
        # --- Cape (flowing behind to the right, wider, scalloped) ---
        cape_sway = walk_offset
        cape_right = body_cx + 28 + cape_sway
        cape_bottom = base_y + 2
        draw.polygon([
            (body_cx + 4, body_cy - 10),
            (body_cx + 22, body_cy - 6),
            (cape_right, cape_bottom),
            (body_cx + 4, cape_bottom),
        ], fill=CAPE, outline=OUTLINE)
        # Inner lining
        draw.polygon([
            (body_cx + 6, body_cy - 6),
            (body_cx + 18, body_cy - 4),
            (body_cx + 22 + cape_sway, cape_bottom - 4),
            (body_cx + 6, cape_bottom - 4),
        ], fill=CAPE_LIGHT, outline=None)
        # Scalloped bottom
        draw_scalloped_cape_bottom(draw, body_cx + 4, cape_right, cape_bottom - 2, CAPE, num_points=4)

        # --- Legs ---
        leg_x = body_cx - 4
        front_leg_y = base_y - 10 + walk_offset
        back_leg_y = base_y - 10 - walk_offset
        draw.rectangle([leg_x - 4, front_leg_y, leg_x + 2, base_y - 2], fill=PANTS, outline=OUTLINE)
        draw.rectangle([leg_x + 2, back_leg_y, leg_x + 8, base_y - 2], fill=PANTS, outline=OUTLINE)
        draw.rectangle([leg_x - 6, base_y - 2, leg_x + 2, base_y + 2], fill=SHOE, outline=OUTLINE)
        draw.rectangle([leg_x + 2, base_y - 2, leg_x + 10, base_y + 2], fill=SHOE, outline=OUTLINE)

        # --- Body (vest, side view) ---
        draw.polygon([
            (body_cx - 10, body_cy - 8),
            (body_cx + 10, body_cy - 8),
            (body_cx + 8, body_cy + 10),
            (body_cx - 8, body_cy + 10),
        ], fill=VEST, outline=OUTLINE)
        # Vest buttons (side view)
        for i in range(2):
            y = body_cy - 2 + i * 5
            draw.rectangle([body_cx - 4, y, body_cx - 2, y + 2], fill=VEST_BUTTON)

        # --- Medallion (side view) ---
        draw_medallion(draw, body_cx - 6, body_cy - 6, full=False)

        # --- Tall sharp collar (left side, one point visible, taller) ---
        draw_collar_pointed(draw, body_cx, body_cy, LEFT)

        # --- Cape clasp ---
        draw.rectangle([body_cx + 5, body_cy - 9, body_cx + 7, body_cy - 7], fill=GOLD_BRIGHT)

        # --- Arm (front arm) ---
        arm_y = body_cy - 2 + walk_offset
        draw.rectangle([body_cx - 14, arm_y - 2, body_cx - 8, arm_y + 8], fill=CAPE, outline=OUTLINE)
        draw.rectangle([body_cx - 14, arm_y + 8, body_cx - 8, arm_y + 12], fill=SKIN_PALE, outline=OUTLINE)

        # --- Head (facing left) ---
        ellipse(draw, body_cx - 2, head_cy, 14, 16, SKIN_PALE)
        # Pale complexion gradient
        ellipse(draw, body_cx - 4, head_cy - 4, 8, 8, SKIN_HIGHLIGHT, outline=None)

        # --- Hair (styled widow's peak, sharper, wavy sides) ---
        draw.polygon([
            (body_cx - 18, head_cy - 2),
            (body_cx - 16, head_cy - 8),
            (body_cx - 10, head_cy - 12),
            (body_cx - 2, head_cy - 22),
            (body_cx + 6, head_cy - 12),
            (body_cx + 10, head_cy - 6),
            (body_cx + 12, head_cy - 2),
            (body_cx + 8, head_cy - 6),
            (body_cx + 4, head_cy - 10),
            (body_cx - 2, head_cy - 14),
            (body_cx - 10, head_cy - 10),
            (body_cx - 14, head_cy - 6),
        ], fill=HAIR, outline=OUTLINE)
        draw.point((body_cx - 16, head_cy - 4), fill=HAIR_HIGHLIGHT)
        draw.point((body_cx + 10, head_cy - 4), fill=HAIR_HIGHLIGHT)
        draw.point((body_cx - 14, head_cy - 8), fill=HAIR_HIGHLIGHT)

        # --- Eye brow shadow ---
        draw.line([(body_cx - 14, head_cy - 2), (body_cx - 6, head_cy - 2)], fill=EYE_SHADOW, width=2)

        # --- Eye (larger, glowing, with outer glow) ---
        draw.rectangle([body_cx - 14, head_cy, body_cx - 6, head_cy + 4], fill=EYE_RED)
        draw.rectangle([body_cx - 11, head_cy + 1, body_cx - 9, head_cy + 3], fill=EYE_GLOW)
        draw.rectangle([body_cx - 9, head_cy + 1, body_cx - 7, head_cy + 3], fill=EYE_CORE)

        # --- Mouth + fang (4px tall with blood tip + blood drop) ---
        draw.line([(body_cx - 10, head_cy + 8), (body_cx - 4, head_cy + 8)], fill=MOUTH_DARK, width=2)
        draw.line([(body_cx - 8, head_cy + 8), (body_cx - 8, head_cy + 12)], fill=FANG, width=2)
        draw.point((body_cx - 8, head_cy + 12), fill=BLOOD_TIP)
        draw.point((body_cx - 8, head_cy + 13), fill=BLOOD_DROP)

    elif direction == RIGHT:
        # --- Cape (flowing behind to the left, wider, scalloped) ---
        cape_sway = walk_offset
        cape_left = body_cx - 28 + cape_sway
        cape_bottom = base_y + 2
        draw.polygon([
            (body_cx - 4, body_cy - 10),
            (body_cx - 22, body_cy - 6),
            (cape_left, cape_bottom),
            (body_cx - 4, cape_bottom),
        ], fill=CAPE, outline=OUTLINE)
        # Inner lining
        draw.polygon([
            (body_cx - 6, body_cy - 6),
            (body_cx - 18, body_cy - 4),
            (body_cx - 22 + cape_sway, cape_bottom - 4),
            (body_cx - 6, cape_bottom - 4),
        ], fill=CAPE_LIGHT, outline=None)
        # Scalloped bottom
        draw_scalloped_cape_bottom(draw, cape_left, body_cx - 4, cape_bottom - 2, CAPE, num_points=4)

        # --- Legs ---
        leg_x = body_cx + 4
        front_leg_y = base_y - 10 - walk_offset
        back_leg_y = base_y - 10 + walk_offset
        draw.rectangle([leg_x - 2, front_leg_y, leg_x + 4, base_y - 2], fill=PANTS, outline=OUTLINE)
        draw.rectangle([leg_x - 8, back_leg_y, leg_x - 2, base_y - 2], fill=PANTS, outline=OUTLINE)
        draw.rectangle([leg_x - 2, base_y - 2, leg_x + 6, base_y + 2], fill=SHOE, outline=OUTLINE)
        draw.rectangle([leg_x - 10, base_y - 2, leg_x - 2, base_y + 2], fill=SHOE, outline=OUTLINE)

        # --- Body (vest, side view) ---
        draw.polygon([
            (body_cx - 10, body_cy - 8),
            (body_cx + 10, body_cy - 8),
            (body_cx + 8, body_cy + 10),
            (body_cx - 8, body_cy + 10),
        ], fill=VEST, outline=OUTLINE)
        # Vest buttons (side view)
        for i in range(2):
            y = body_cy - 2 + i * 5
            draw.rectangle([body_cx + 2, y, body_cx + 4, y + 2], fill=VEST_BUTTON)

        # --- Medallion (side view) ---
        draw_medallion(draw, body_cx + 6, body_cy - 6, full=False)

        # --- Tall sharp collar (right side, one point visible, taller) ---
        draw_collar_pointed(draw, body_cx, body_cy, RIGHT)

        # --- Cape clasp ---
        draw.rectangle([body_cx - 7, body_cy - 9, body_cx - 5, body_cy - 7], fill=GOLD_BRIGHT)

        # --- Arm (front arm) ---
        arm_y = body_cy - 2 - walk_offset
        draw.rectangle([body_cx + 8, arm_y - 2, body_cx + 14, arm_y + 8], fill=CAPE, outline=OUTLINE)
        draw.rectangle([body_cx + 8, arm_y + 8, body_cx + 14, arm_y + 12], fill=SKIN_PALE, outline=OUTLINE)

        # --- Head (facing right) ---
        ellipse(draw, body_cx + 2, head_cy, 14, 16, SKIN_PALE)
        # Pale complexion gradient
        ellipse(draw, body_cx + 4, head_cy - 4, 8, 8, SKIN_HIGHLIGHT, outline=None)

        # --- Hair (styled widow's peak, sharper, wavy sides) ---
        draw.polygon([
            (body_cx - 12, head_cy - 2),
            (body_cx - 10, head_cy - 6),
            (body_cx - 6, head_cy - 12),
            (body_cx + 2, head_cy - 22),
            (body_cx + 10, head_cy - 12),
            (body_cx + 16, head_cy - 8),
            (body_cx + 18, head_cy - 2),
            (body_cx + 14, head_cy - 6),
            (body_cx + 10, head_cy - 10),
            (body_cx + 2, head_cy - 14),
            (body_cx - 4, head_cy - 10),
            (body_cx - 8, head_cy - 6),
        ], fill=HAIR, outline=OUTLINE)
        draw.point((body_cx + 16, head_cy - 4), fill=HAIR_HIGHLIGHT)
        draw.point((body_cx - 10, head_cy - 4), fill=HAIR_HIGHLIGHT)
        draw.point((body_cx + 14, head_cy - 8), fill=HAIR_HIGHLIGHT)

        # --- Eye brow shadow ---
        draw.line([(body_cx + 6, head_cy - 2), (body_cx + 14, head_cy - 2)], fill=EYE_SHADOW, width=2)

        # --- Eye (larger, glowing, with outer glow) ---
        draw.rectangle([body_cx + 6, head_cy, body_cx + 14, head_cy + 4], fill=EYE_RED)
        draw.rectangle([body_cx + 9, head_cy + 1, body_cx + 11, head_cy + 3], fill=EYE_GLOW)
        draw.rectangle([body_cx + 7, head_cy + 1, body_cx + 9, head_cy + 3], fill=EYE_CORE)

        # --- Mouth + fang (4px tall with blood tip + blood drop) ---
        draw.line([(body_cx + 4, head_cy + 8), (body_cx + 10, head_cy + 8)], fill=MOUTH_DARK, width=2)
        draw.line([(body_cx + 8, head_cy + 8), (body_cx + 8, head_cy + 12)], fill=FANG, width=2)
        draw.point((body_cx + 8, head_cy + 12), fill=BLOOD_TIP)
        draw.point((body_cx + 8, head_cy + 13), fill=BLOOD_DROP)


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
