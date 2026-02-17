#!/usr/bin/env python3
"""Generate sprites/assassin.png — 4-column x 4-row character spritesheet.

256x256 PNG, 64x64 per frame.
Row layout: Down=0, Up=1, Left=2, Right=3
4 walking animation frames per direction.

Style matches Spaceman/Gladiator: big round head, round body, small limbs.
Theme: Shadowy assassin — hooded figure with mask, dark cloak, visible daggers.
Color palette: deep purple/black with crimson accents.
Enhanced 64x64: blade strapped to back (UP), utility belt with pouches,
mask detail with eye slit shadow, throwing star detail on belt.
"""

from PIL import Image, ImageDraw

FRAME_SIZE = 64
COLS = 4
ROWS = 4
IMG_W = FRAME_SIZE * COLS   # 256
IMG_H = FRAME_SIZE * ROWS   # 256

# Colors — brightened from original so the character is visible
OUTLINE = (25, 18, 35)
CLOAK_DARK = (40, 28, 55)
CLOAK = (55, 40, 70)
CLOAK_LIGHT = (75, 55, 95)
HOOD = (48, 35, 65)
HOOD_DARK = (35, 25, 50)
HOOD_EDGE = (90, 70, 115)       # Lighter edge highlight for hood silhouette
MASK = (28, 28, 35)
MASK_EDGE = (50, 42, 60)
EYE_GLOW = (240, 55, 45)        # Brighter red glow
EYE_CORE = (255, 150, 120)      # Hot bright core
EYE_HALO = (180, 30, 30, 140)   # Subtle glow halo around eyes
SKIN = (65, 50, 45)
BELT = (140, 30, 30)
BELT_BUCKLE = (190, 170, 65)
DAGGER_BLADE = (185, 190, 205)  # Slightly brighter steel
DAGGER_HILT = (130, 30, 30)
DAGGER_GLINT = (230, 240, 255)  # Bright glint at blade tip
PANTS = (42, 32, 52)
BOOTS = (38, 28, 48)
BOOT_BUCKLE = (160, 140, 55)
SCARF_TAIL = (180, 35, 35)      # Brighter, more visible crimson
THROWING_STAR = (190, 200, 215)  # Metallic bright for shuriken
# New detail colors
EYE_SLIT_SHADOW = (15, 10, 20)
POUCH = (55, 35, 60)
POUCH_FLAP = (70, 50, 80)
BACK_BLADE = (175, 180, 200)
BACK_BLADE_DARK = (130, 135, 150)
STRAP = (90, 40, 40)

DOWN, UP, LEFT, RIGHT = 0, 1, 2, 3


def ellipse(draw, cx, cy, rx, ry, fill, outline=OUTLINE):
    """Draw an outlined ellipse centered at (cx, cy)."""
    draw.ellipse([cx - rx, cy - ry, cx + rx, cy + ry], fill=fill, outline=outline)


def draw_throwing_star(draw, cx, cy):
    """Draw an X-shaped throwing star (shuriken) at (cx, cy), larger for 64x64."""
    # Center dot
    draw.rectangle([cx - 1, cy - 1, cx + 1, cy + 1], fill=THROWING_STAR)
    # 4 arms of the star
    draw.point((cx - 2, cy - 2), fill=THROWING_STAR)
    draw.point((cx + 2, cy - 2), fill=THROWING_STAR)
    draw.point((cx - 2, cy + 2), fill=THROWING_STAR)
    draw.point((cx + 2, cy + 2), fill=THROWING_STAR)
    # Extended tips
    draw.point((cx - 3, cy - 3), fill=DAGGER_GLINT)
    draw.point((cx + 3, cy - 3), fill=DAGGER_GLINT)
    draw.point((cx - 3, cy + 3), fill=DAGGER_GLINT)
    draw.point((cx + 3, cy + 3), fill=DAGGER_GLINT)


def draw_boot_down(draw, cx, base_y, flip=False):
    """Draw a pointed boot (front-facing, for DOWN direction).
    flip=True mirrors horizontally for the other foot."""
    if not flip:
        draw.polygon([
            (cx - 6, base_y - 4),
            (cx + 4, base_y - 4),
            (cx + 6, base_y),
            (cx + 6, base_y + 2),
            (cx - 6, base_y + 2),
            (cx - 6, base_y - 4),
        ], fill=BOOTS, outline=OUTLINE)
        # Buckle detail
        draw.rectangle([cx - 1, base_y - 3, cx + 1, base_y - 1], fill=BOOT_BUCKLE)
    else:
        draw.polygon([
            (cx - 4, base_y - 4),
            (cx + 6, base_y - 4),
            (cx + 6, base_y + 2),
            (cx - 6, base_y + 2),
            (cx - 6, base_y),
            (cx - 4, base_y - 4),
        ], fill=BOOTS, outline=OUTLINE)
        draw.rectangle([cx - 1, base_y - 3, cx + 1, base_y - 1], fill=BOOT_BUCKLE)


def draw_boot_side(draw, cx, base_y, facing_left=True):
    """Draw a pointed boot from the side — toe extends in walking direction."""
    if facing_left:
        draw.polygon([
            (cx + 4, base_y - 4),
            (cx + 4, base_y + 2),
            (cx - 8, base_y + 2),
            (cx - 8, base_y),
            (cx - 2, base_y - 4),
        ], fill=BOOTS, outline=OUTLINE)
        draw.rectangle([cx - 1, base_y - 3, cx + 1, base_y - 1], fill=BOOT_BUCKLE)
    else:
        draw.polygon([
            (cx - 4, base_y - 4),
            (cx + 2, base_y - 4),
            (cx + 8, base_y),
            (cx + 8, base_y + 2),
            (cx - 4, base_y + 2),
        ], fill=BOOTS, outline=OUTLINE)
        draw.rectangle([cx - 1, base_y - 3, cx + 1, base_y - 1], fill=BOOT_BUCKLE)


def draw_utility_belt(draw, body_cx, body_cy, direction):
    """Draw a utility belt with 2-3 small pouch squares."""
    if direction == DOWN:
        # Main belt
        draw.rectangle([body_cx - 14, body_cy + 6, body_cx + 14, body_cy + 10], fill=BELT)
        draw.rectangle([body_cx - 4, body_cy + 6, body_cx + 4, body_cy + 10], fill=BELT_BUCKLE)
        # Left pouch
        draw.rectangle([body_cx - 12, body_cy + 7, body_cx - 7, body_cy + 12], fill=POUCH, outline=OUTLINE)
        draw.rectangle([body_cx - 12, body_cy + 7, body_cx - 7, body_cy + 9], fill=POUCH_FLAP, outline=OUTLINE)
        # Right pouch
        draw.rectangle([body_cx + 7, body_cy + 7, body_cx + 12, body_cy + 12], fill=POUCH, outline=OUTLINE)
        draw.rectangle([body_cx + 7, body_cy + 7, body_cx + 12, body_cy + 9], fill=POUCH_FLAP, outline=OUTLINE)
    elif direction == UP:
        draw.rectangle([body_cx - 14, body_cy + 6, body_cx + 14, body_cy + 10], fill=BELT)
    elif direction == LEFT:
        draw.rectangle([body_cx - 12, body_cy + 6, body_cx + 12, body_cy + 10], fill=BELT)
        # Single pouch visible from side
        draw.rectangle([body_cx + 4, body_cy + 7, body_cx + 10, body_cy + 12], fill=POUCH, outline=OUTLINE)
        draw.rectangle([body_cx + 4, body_cy + 7, body_cx + 10, body_cy + 9], fill=POUCH_FLAP, outline=OUTLINE)
    elif direction == RIGHT:
        draw.rectangle([body_cx - 12, body_cy + 6, body_cx + 12, body_cy + 10], fill=BELT)
        # Single pouch visible from side
        draw.rectangle([body_cx - 10, body_cy + 7, body_cx - 4, body_cy + 12], fill=POUCH, outline=OUTLINE)
        draw.rectangle([body_cx - 10, body_cy + 7, body_cx - 4, body_cy + 9], fill=POUCH_FLAP, outline=OUTLINE)


def draw_mask_with_slit(draw, body_cx, head_cy, direction):
    """Draw the mask with an eye slit shadow detail."""
    if direction == DOWN:
        draw.rectangle([body_cx - 10, head_cy + 2, body_cx + 10, head_cy + 10],
                        fill=MASK, outline=MASK_EDGE)
        # Eye slit shadow — dark line just above the mask top
        draw.line([(body_cx - 8, head_cy + 1), (body_cx + 8, head_cy + 1)],
                  fill=EYE_SLIT_SHADOW, width=2)
    elif direction == LEFT:
        draw.rectangle([body_cx - 10, head_cy + 2, body_cx + 2, head_cy + 10],
                        fill=MASK, outline=MASK_EDGE)
        draw.line([(body_cx - 8, head_cy + 1), (body_cx, head_cy + 1)],
                  fill=EYE_SLIT_SHADOW, width=2)
    elif direction == RIGHT:
        draw.rectangle([body_cx - 2, head_cy + 2, body_cx + 10, head_cy + 10],
                        fill=MASK, outline=MASK_EDGE)
        draw.line([(body_cx, head_cy + 1), (body_cx + 8, head_cy + 1)],
                  fill=EYE_SLIT_SHADOW, width=2)


def draw_assassin(draw, ox, oy, direction, frame):
    """Draw a single assassin frame at offset (ox, oy).

    Proportions match other characters: big round head, round body, small limbs.
    """
    # Walking bob animation
    bob = [0, -2, 0, -1][frame]
    leg_spread = [-4, 0, 4, 0][frame]

    base_y = oy + 58 + bob
    body_cx = ox + 32
    body_cy = base_y - 22
    head_cy = body_cy - 20

    if direction == DOWN:
        # --- Legs ---
        # Left leg
        draw.line([(body_cx - 6 - leg_spread, body_cy + 10),
                   (body_cx - 6 - leg_spread, base_y - 4)],
                  fill=PANTS, width=6)
        draw_boot_down(draw, body_cx - 6 - leg_spread, base_y)
        # Right leg
        draw.line([(body_cx + 6 + leg_spread, body_cy + 10),
                   (body_cx + 6 + leg_spread, base_y - 4)],
                  fill=PANTS, width=6)
        draw_boot_down(draw, body_cx + 6 + leg_spread, base_y, flip=True)

        # --- Body (dark cloak/tunic) ---
        ellipse(draw, body_cx, body_cy, 14, 12, CLOAK)
        # Cloak highlight center
        ellipse(draw, body_cx, body_cy, 8, 8, CLOAK_LIGHT, outline=None)

        # --- Belt with pouches ---
        draw_utility_belt(draw, body_cx, body_cy, DOWN)

        # --- Throwing stars on belt ---
        draw_throwing_star(draw, body_cx - 10, body_cy + 8)
        draw_throwing_star(draw, body_cx + 10, body_cy + 8)

        # --- Daggers on belt (both sides, bigger) ---
        # Left dagger — 4px wide, 14px long
        draw.line([(body_cx - 18, body_cy + 2), (body_cx - 18, body_cy + 16)],
                  fill=DAGGER_BLADE, width=4)
        draw.point((body_cx - 18, body_cy + 16), fill=DAGGER_GLINT)
        draw.point((body_cx - 18, body_cy + 15), fill=DAGGER_GLINT)
        draw.rectangle([body_cx - 20, body_cy - 2, body_cx - 16, body_cy + 2], fill=DAGGER_HILT)
        # Right dagger — 4px wide, 14px long
        draw.line([(body_cx + 18, body_cy + 2), (body_cx + 18, body_cy + 16)],
                  fill=DAGGER_BLADE, width=4)
        draw.point((body_cx + 18, body_cy + 16), fill=DAGGER_GLINT)
        draw.point((body_cx + 18, body_cy + 15), fill=DAGGER_GLINT)
        draw.rectangle([body_cx + 16, body_cy - 2, body_cx + 20, body_cy + 2], fill=DAGGER_HILT)

        # --- Hood ---
        ellipse(draw, body_cx, head_cy, 16, 16, HOOD)
        # Hood edge highlight — arc along the top
        for px in range(-12, 13):
            draw.point((body_cx + px, head_cy - 14), fill=HOOD_EDGE)
        for px in range(-14, 15):
            draw.point((body_cx + px, head_cy - 12), fill=HOOD_EDGE)
        # Hood inner shadow
        ellipse(draw, body_cx, head_cy + 2, 12, 12, HOOD_DARK)

        # --- Mask with eye slit shadow ---
        draw_mask_with_slit(draw, body_cx, head_cy, DOWN)

        # --- Eyes (glowing red, larger and brighter) ---
        # Left eye
        draw.rectangle([body_cx - 10, head_cy - 4, body_cx - 2, head_cy + 2], fill=EYE_GLOW)
        draw.point((body_cx - 6, head_cy - 2), fill=EYE_CORE)
        draw.point((body_cx - 4, head_cy), fill=EYE_CORE)
        draw.point((body_cx - 5, head_cy - 1), fill=EYE_CORE)
        # Right eye
        draw.rectangle([body_cx + 2, head_cy - 4, body_cx + 10, head_cy + 2], fill=EYE_GLOW)
        draw.point((body_cx + 6, head_cy - 2), fill=EYE_CORE)
        draw.point((body_cx + 4, head_cy), fill=EYE_CORE)
        draw.point((body_cx + 5, head_cy - 1), fill=EYE_CORE)

        # --- Hood peak ---
        draw.polygon([
            (body_cx - 4, head_cy - 18),
            (body_cx + 4, head_cy - 18),
            (body_cx + 2, head_cy - 10),
            (body_cx - 2, head_cy - 10),
        ], fill=HOOD, outline=OUTLINE)
        # Peak edge highlight
        draw.point((body_cx - 2, head_cy - 18), fill=HOOD_EDGE)
        draw.point((body_cx, head_cy - 18), fill=HOOD_EDGE)
        draw.point((body_cx + 2, head_cy - 18), fill=HOOD_EDGE)
        draw.point((body_cx - 1, head_cy - 18), fill=HOOD_EDGE)
        draw.point((body_cx + 1, head_cy - 18), fill=HOOD_EDGE)

        # --- Scarf tail (wider, longer, more dramatic) ---
        scarf_sway = [-2, 0, 2, 0][frame]
        # Main scarf body — 6px wide
        draw.line([(body_cx + 8, head_cy + 6),
                   (body_cx + 14 + scarf_sway, head_cy + 20)],
                  fill=SCARF_TAIL, width=6)
        # Scarf tip extension
        draw.line([(body_cx + 14 + scarf_sway, head_cy + 20),
                   (body_cx + 18 + scarf_sway, head_cy + 26)],
                  fill=SCARF_TAIL, width=4)

    elif direction == UP:
        # --- Legs ---
        draw.line([(body_cx - 6 - leg_spread, body_cy + 10),
                   (body_cx - 6 - leg_spread, base_y - 4)],
                  fill=PANTS, width=6)
        draw_boot_down(draw, body_cx - 6 - leg_spread, base_y)
        draw.line([(body_cx + 6 + leg_spread, body_cy + 10),
                   (body_cx + 6 + leg_spread, base_y - 4)],
                  fill=PANTS, width=6)
        draw_boot_down(draw, body_cx + 6 + leg_spread, base_y, flip=True)

        # --- Scarf tail (flowing down in back, wider and longer) ---
        scarf_sway = [-2, 0, 2, 0][frame]
        draw.line([(body_cx, head_cy + 10),
                   (body_cx + scarf_sway, head_cy + 24)],
                  fill=SCARF_TAIL, width=6)
        draw.line([(body_cx + scarf_sway, head_cy + 24),
                   (body_cx + scarf_sway * 2, head_cy + 32)],
                  fill=SCARF_TAIL, width=4)

        # --- Body ---
        ellipse(draw, body_cx, body_cy, 14, 12, CLOAK)
        ellipse(draw, body_cx, body_cy, 10, 8, CLOAK_DARK, outline=None)

        # --- Belt ---
        draw_utility_belt(draw, body_cx, body_cy, UP)

        # --- Blade strapped to back (visible from UP direction) ---
        # Long blade running diagonally across the back
        draw.line([(body_cx - 4, body_cy - 14), (body_cx + 8, body_cy + 10)],
                  fill=BACK_BLADE, width=4)
        draw.line([(body_cx - 4, body_cy - 14), (body_cx + 8, body_cy + 10)],
                  fill=BACK_BLADE_DARK, width=2)
        # Glint at tip
        draw.point((body_cx - 4, body_cy - 14), fill=DAGGER_GLINT)
        draw.point((body_cx - 3, body_cy - 13), fill=DAGGER_GLINT)
        # Hilt at bottom
        draw.rectangle([body_cx + 6, body_cy + 8, body_cx + 12, body_cy + 14], fill=DAGGER_HILT)
        # Strap across body
        draw.line([(body_cx - 10, body_cy - 6), (body_cx + 10, body_cy + 6)],
                  fill=STRAP, width=2)

        # --- Daggers (crossed on back, bigger) ---
        draw.line([(body_cx - 8, body_cy - 10), (body_cx + 8, body_cy + 8)],
                  fill=DAGGER_BLADE, width=4)
        draw.line([(body_cx + 8, body_cy - 10), (body_cx - 8, body_cy + 8)],
                  fill=DAGGER_BLADE, width=4)
        draw.point((body_cx - 8, body_cy - 10), fill=DAGGER_GLINT)
        draw.point((body_cx + 8, body_cy - 10), fill=DAGGER_GLINT)
        draw.rectangle([body_cx - 10, body_cy - 12, body_cx - 6, body_cy - 8], fill=DAGGER_HILT)
        draw.rectangle([body_cx + 6, body_cy - 12, body_cx + 10, body_cy - 8], fill=DAGGER_HILT)

        # --- Hood (back view) ---
        ellipse(draw, body_cx, head_cy, 16, 16, HOOD)
        # Hood edge highlight along top
        for px in range(-12, 13):
            draw.point((body_cx + px, head_cy - 14), fill=HOOD_EDGE)
        for px in range(-14, 15):
            draw.point((body_cx + px, head_cy - 12), fill=HOOD_EDGE)
        ellipse(draw, body_cx, head_cy, 12, 12, HOOD_DARK)

        # --- Hood peak ---
        draw.polygon([
            (body_cx - 4, head_cy - 18),
            (body_cx + 4, head_cy - 18),
            (body_cx + 2, head_cy - 10),
            (body_cx - 2, head_cy - 10),
        ], fill=HOOD, outline=OUTLINE)
        draw.point((body_cx - 2, head_cy - 18), fill=HOOD_EDGE)
        draw.point((body_cx, head_cy - 18), fill=HOOD_EDGE)
        draw.point((body_cx + 2, head_cy - 18), fill=HOOD_EDGE)
        draw.point((body_cx - 1, head_cy - 18), fill=HOOD_EDGE)
        draw.point((body_cx + 1, head_cy - 18), fill=HOOD_EDGE)

    elif direction == LEFT:
        # --- Legs ---
        draw.line([(body_cx - 2 - leg_spread, body_cy + 10),
                   (body_cx - 2 - leg_spread, base_y - 4)],
                  fill=PANTS, width=6)
        draw_boot_side(draw, body_cx - 2 - leg_spread, base_y, facing_left=True)
        draw.line([(body_cx + 4 + leg_spread, body_cy + 10),
                   (body_cx + 4 + leg_spread, base_y - 4)],
                  fill=PANTS, width=6)
        draw_boot_side(draw, body_cx + 4 + leg_spread, base_y, facing_left=True)

        # --- Scarf tail (flowing right, wider and longer) ---
        scarf_sway = [0, 2, 0, -2][frame]
        draw.line([(body_cx + 6, head_cy + 6),
                   (body_cx + 18, head_cy + 12 + scarf_sway)],
                  fill=SCARF_TAIL, width=6)
        draw.line([(body_cx + 18, head_cy + 12 + scarf_sway),
                   (body_cx + 24, head_cy + 18 + scarf_sway)],
                  fill=SCARF_TAIL, width=4)

        # --- Body ---
        ellipse(draw, body_cx, body_cy, 12, 12, CLOAK)
        ellipse(draw, body_cx - 2, body_cy, 8, 8, CLOAK_LIGHT, outline=None)

        # --- Belt with pouch ---
        draw_utility_belt(draw, body_cx, body_cy, LEFT)

        # --- Throwing star on belt ---
        draw_throwing_star(draw, body_cx + 6, body_cy + 8)

        # --- Dagger (held forward prominently, bigger) ---
        draw.line([(body_cx - 16, body_cy - 2), (body_cx - 16, body_cy + 14)],
                  fill=DAGGER_BLADE, width=4)
        draw.point((body_cx - 16, body_cy + 14), fill=DAGGER_GLINT)
        draw.point((body_cx - 16, body_cy + 13), fill=DAGGER_GLINT)
        draw.rectangle([body_cx - 18, body_cy - 6, body_cx - 14, body_cy - 2], fill=DAGGER_HILT)
        # Second dagger on belt (smaller, behind)
        draw.line([(body_cx + 12, body_cy + 4), (body_cx + 12, body_cy + 12)],
                  fill=DAGGER_BLADE, width=2)
        draw.point((body_cx + 12, body_cy + 12), fill=DAGGER_GLINT)

        # --- Hood (side facing left) ---
        ellipse(draw, body_cx - 2, head_cy, 14, 16, HOOD)
        # Hood edge highlight along the top-left rim
        for px in range(-12, 8):
            draw.point((body_cx - 2 + px, head_cy - 14), fill=HOOD_EDGE)
        ellipse(draw, body_cx - 2, head_cy + 2, 10, 12, HOOD_DARK)

        # --- Mask with eye slit shadow (side view) ---
        draw_mask_with_slit(draw, body_cx, head_cy, LEFT)

        # --- Eye (one visible, glowing, larger) ---
        draw.rectangle([body_cx - 10, head_cy - 4, body_cx - 2, head_cy + 2], fill=EYE_GLOW)
        draw.point((body_cx - 6, head_cy - 2), fill=EYE_CORE)
        draw.point((body_cx - 4, head_cy), fill=EYE_CORE)
        draw.point((body_cx - 5, head_cy - 1), fill=EYE_CORE)

        # --- Hood peak ---
        draw.polygon([
            (body_cx - 6, head_cy - 18),
            (body_cx + 2, head_cy - 18),
            (body_cx, head_cy - 10),
            (body_cx - 4, head_cy - 10),
        ], fill=HOOD, outline=OUTLINE)
        draw.point((body_cx - 4, head_cy - 18), fill=HOOD_EDGE)
        draw.point((body_cx - 2, head_cy - 18), fill=HOOD_EDGE)
        draw.point((body_cx, head_cy - 18), fill=HOOD_EDGE)
        draw.point((body_cx - 3, head_cy - 18), fill=HOOD_EDGE)
        draw.point((body_cx - 1, head_cy - 18), fill=HOOD_EDGE)

    elif direction == RIGHT:
        # --- Legs ---
        draw.line([(body_cx - 4 - leg_spread, body_cy + 10),
                   (body_cx - 4 - leg_spread, base_y - 4)],
                  fill=PANTS, width=6)
        draw_boot_side(draw, body_cx - 4 - leg_spread, base_y, facing_left=False)
        draw.line([(body_cx + 2 + leg_spread, body_cy + 10),
                   (body_cx + 2 + leg_spread, base_y - 4)],
                  fill=PANTS, width=6)
        draw_boot_side(draw, body_cx + 2 + leg_spread, base_y, facing_left=False)

        # --- Scarf tail (flowing left, wider and longer) ---
        scarf_sway = [0, -2, 0, 2][frame]
        draw.line([(body_cx - 6, head_cy + 6),
                   (body_cx - 18, head_cy + 12 + scarf_sway)],
                  fill=SCARF_TAIL, width=6)
        draw.line([(body_cx - 18, head_cy + 12 + scarf_sway),
                   (body_cx - 24, head_cy + 18 + scarf_sway)],
                  fill=SCARF_TAIL, width=4)

        # --- Body ---
        ellipse(draw, body_cx, body_cy, 12, 12, CLOAK)
        ellipse(draw, body_cx + 2, body_cy, 8, 8, CLOAK_LIGHT, outline=None)

        # --- Belt with pouch ---
        draw_utility_belt(draw, body_cx, body_cy, RIGHT)

        # --- Throwing star on belt ---
        draw_throwing_star(draw, body_cx - 6, body_cy + 8)

        # --- Dagger (held forward prominently, bigger) ---
        draw.line([(body_cx + 16, body_cy - 2), (body_cx + 16, body_cy + 14)],
                  fill=DAGGER_BLADE, width=4)
        draw.point((body_cx + 16, body_cy + 14), fill=DAGGER_GLINT)
        draw.point((body_cx + 16, body_cy + 13), fill=DAGGER_GLINT)
        draw.rectangle([body_cx + 14, body_cy - 6, body_cx + 18, body_cy - 2], fill=DAGGER_HILT)
        # Second dagger on belt (smaller, behind)
        draw.line([(body_cx - 12, body_cy + 4), (body_cx - 12, body_cy + 12)],
                  fill=DAGGER_BLADE, width=2)
        draw.point((body_cx - 12, body_cy + 12), fill=DAGGER_GLINT)

        # --- Hood (side facing right) ---
        ellipse(draw, body_cx + 2, head_cy, 14, 16, HOOD)
        # Hood edge highlight along the top-right rim
        for px in range(-6, 14):
            draw.point((body_cx + 2 + px, head_cy - 14), fill=HOOD_EDGE)
        ellipse(draw, body_cx + 2, head_cy + 2, 10, 12, HOOD_DARK)

        # --- Mask with eye slit shadow (side view) ---
        draw_mask_with_slit(draw, body_cx, head_cy, RIGHT)

        # --- Eye (one visible, glowing, larger) ---
        draw.rectangle([body_cx + 2, head_cy - 4, body_cx + 10, head_cy + 2], fill=EYE_GLOW)
        draw.point((body_cx + 6, head_cy - 2), fill=EYE_CORE)
        draw.point((body_cx + 4, head_cy), fill=EYE_CORE)
        draw.point((body_cx + 5, head_cy - 1), fill=EYE_CORE)

        # --- Hood peak ---
        draw.polygon([
            (body_cx - 2, head_cy - 18),
            (body_cx + 6, head_cy - 18),
            (body_cx + 4, head_cy - 10),
            (body_cx, head_cy - 10),
        ], fill=HOOD, outline=OUTLINE)
        draw.point((body_cx, head_cy - 18), fill=HOOD_EDGE)
        draw.point((body_cx + 2, head_cy - 18), fill=HOOD_EDGE)
        draw.point((body_cx + 4, head_cy - 18), fill=HOOD_EDGE)
        draw.point((body_cx + 1, head_cy - 18), fill=HOOD_EDGE)
        draw.point((body_cx + 3, head_cy - 18), fill=HOOD_EDGE)


def main():
    img = Image.new("RGBA", (IMG_W, IMG_H), (0, 0, 0, 0))

    for direction in range(ROWS):
        for frame in range(COLS):
            frame_img = Image.new("RGBA", (FRAME_SIZE, FRAME_SIZE), (0, 0, 0, 0))
            frame_draw = ImageDraw.Draw(frame_img)
            draw_assassin(frame_draw, 0, 0, direction, frame)
            img.paste(frame_img, (frame * FRAME_SIZE, direction * FRAME_SIZE))

    img.save("sprites/assassin.png")
    print(f"Generated sprites/assassin.png ({IMG_W}x{IMG_H})")


if __name__ == "__main__":
    main()
